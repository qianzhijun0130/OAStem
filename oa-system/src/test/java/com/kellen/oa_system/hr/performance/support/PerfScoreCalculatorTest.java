package com.kellen.oa_system.hr.performance.support;

import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绩效总分口径测试。
 *
 * <p>总分 = Σ(得分 × 权重) + 加分 − 扣分，**封顶 120 / 封底 0**。
 * 封底这一侧最容易被漏：只写了 `if (v > 120) v = 120` 的实现，
 * 在扣分把总分拉到负数时会算出一个负分，进而把薪酬系数算成 0 甚至负数。</p>
 */
class PerfScoreCalculatorTest {

    private static PerfSheetItem item(Integer type, String weight, String self, String manager) {
        PerfSheetItem it = new PerfSheetItem();
        it.setIndicatorType(type);
        it.setWeight(weight == null ? null : new BigDecimal(weight));
        it.setSelfScore(self == null ? null : new BigDecimal(self));
        it.setManagerScore(manager == null ? null : new BigDecimal(manager));
        return it;
    }

    @Test
    @DisplayName("空指标表返回 0.0（1 位小数）")
    void emptyItems() {
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.total(List.of(), true)));
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.total(null, true)));
    }

    @Test
    @DisplayName("加权得分 = 得分 × 权重 ÷ 100")
    void weightedTotal() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, "40", "90", "80"));
        items.add(item(1, "60", "80", "70"));
        // 自评：90×0.4 + 80×0.6 = 36 + 48 = 84
        assertEquals(0, new BigDecimal("84.0").compareTo(PerfScoreCalculator.total(items, true)));
        // 上级：80×0.4 + 70×0.6 = 32 + 42 = 74
        assertEquals(0, new BigDecimal("74.0").compareTo(PerfScoreCalculator.total(items, false)));
    }

    @Test
    @DisplayName("杀手：权重必须除以 100，漏除会让总分放大 100 倍")
    void weightIsDividedByHundred() {
        List<PerfSheetItem> items = List.of(item(1, "100", "90", "90"));
        assertEquals(0, new BigDecimal("90.0").compareTo(PerfScoreCalculator.total(items, true)),
                "权重 100% 时总分应等于得分本身，若得 9000 说明漏了 ÷100");
    }

    @Test
    @DisplayName("加分项（type=3）与扣分项（type=4）直接用分数加减，不乘权重")
    void bonusAndPenaltyAreRawScores() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, "100", "90", "90"));
        items.add(item(3, "0", "5", "5"));    // 加分
        items.add(item(4, "0", "3", "3"));    // 扣分
        // 90 + 5 − 3 = 92
        assertEquals(0, new BigDecimal("92.0").compareTo(PerfScoreCalculator.total(items, true)));
    }

    @Test
    @DisplayName("杀手：加分项若被乘权重（权重固定 0）会归零，出现「有加分但不影响总分」")
    void bonusItemWithZeroWeightStillCounts() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, "100", "80", "80"));
        items.add(item(3, "0", "10", "10"));
        // 若加分也走 weight(0)，结果为 80 而不是 90
        assertEquals(0, new BigDecimal("90.0").compareTo(PerfScoreCalculator.total(items, true)));
    }

    @Test
    @DisplayName("封顶 120：超出部分被截断")
    void capAtMax() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, "100", "130", "130"));
        assertEquals(0, new BigDecimal("120.0").compareTo(PerfScoreCalculator.total(items, true)));
    }

    @Test
    @DisplayName("杀手：封底 0，扣分后不能出现负分")
    void floorAtZero() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, "100", "40", "40"));
        items.add(item(4, "0", "60", "60"));
        // 40 − 60 = −20 → 必须收敛为 0.0
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.total(items, true)),
                "只做封顶不做封底的实现会返回 -20.0");
        assertTrue(PerfScoreCalculator.total(items, true).compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    @DisplayName("空分数行被跳过，不影响其他行")
    void nullScoreSkipped() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, "50", null, null));
        items.add(item(1, "50", "80", "80"));
        // 未打分那行跳过；已打分 80×0.5 = 40
        assertEquals(0, new BigDecimal("40.0").compareTo(PerfScoreCalculator.total(items, true)));
    }

    @Test
    @DisplayName("未打分（null 权重按 0 处理）不会抛异常")
    void nullWeightTreatedAsZero() {
        List<PerfSheetItem> items = new ArrayList<>();
        items.add(item(1, null, "90", "90"));
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.total(items, true)));
    }

    @Test
    @DisplayName("clamp 边界：0 / 120 / 越界两侧")
    void clampBoundaries() {
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.clamp(new BigDecimal("-5"))));
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.clamp(BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("120.0").compareTo(PerfScoreCalculator.clamp(new BigDecimal("120"))));
        assertEquals(0, new BigDecimal("120.0").compareTo(PerfScoreCalculator.clamp(new BigDecimal("120.01"))));
        assertEquals(0, new BigDecimal("0.0").compareTo(PerfScoreCalculator.clamp(null)));
    }

    @Test
    @DisplayName("总分保留 1 位小数（四舍五入）")
    void oneDecimalScale() {
        List<PerfSheetItem> items = List.of(item(1, "100", "89.55", "89.55"));
        assertEquals(0, new BigDecimal("89.6").compareTo(PerfScoreCalculator.total(items, true)));
        assertEquals(1, PerfScoreCalculator.total(items, true).scale());
    }

    @Test
    @DisplayName("weightedScore 单行展示值：÷100 并保留 2 位")
    void weightedScoreOfSingleItem() {
        assertEquals(0, new BigDecimal("36.00").compareTo(
                PerfScoreCalculator.weightedScore(new BigDecimal("90"), new BigDecimal("40"))));
        assertEquals(null, PerfScoreCalculator.weightedScore(null, new BigDecimal("40")));
        assertEquals(null, PerfScoreCalculator.weightedScore(new BigDecimal("90"), null));
    }
}
