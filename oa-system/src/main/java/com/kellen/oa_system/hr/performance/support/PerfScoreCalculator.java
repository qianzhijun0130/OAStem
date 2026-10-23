package com.kellen.oa_system.hr.performance.support;

import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 绩效总分与等级换算。
 *
 * <p>口径集中在 {@code 开发文档/人力资源/04} 第 5.1 节：</p>
 * <pre>
 * 总分 = Σ(指标得分 × 权重) + 加分 - 扣分   （四舍五入 1 位小数，封顶 120 / 封底 0）
 * 等级 = 方案配置的分数区间映射
 * 系数 = 等级对应的系数（供薪酬引用）
 * </pre>
 *
 * <p><b>为什么加减分单独处理</b>：加分项与扣分项的权重是 0，分数本身就是「要加/要减的分」。
 * 若按权重乘一遍会全部归零，工资条上就会出现「有加分但不影响总分」的怪事。</p>
 */
public final class PerfScoreCalculator {

    /** 总分上限：允许加分把分数推到 120，再多没有区分意义 */
    public static final BigDecimal MAX_TOTAL = new BigDecimal("120");

    /** 加分项合计上限 */
    public static final BigDecimal MAX_BONUS = new BigDecimal("10");

    /** 扣分项合计上限 */
    public static final BigDecimal MAX_PENALTY = new BigDecimal("20");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PerfScoreCalculator() {
    }

    /**
     * 计算总分。
     *
     * @param items   考核表指标行
     * @param useSelf true=用自评分，false=用上级评分
     */
    public static BigDecimal total(List<PerfSheetItem> items, boolean useSelf) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PerfSheetItem item : items) {
            BigDecimal score = useSelf ? item.getSelfScore() : item.getManagerScore();
            if (score == null) {
                continue;
            }
            int type = item.getIndicatorType() == null ? 1 : item.getIndicatorType();
            if (type == 3) {
                sum = sum.add(score);
            } else if (type == 4) {
                sum = sum.subtract(score);
            } else {
                BigDecimal weight = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
                sum = sum.add(score.multiply(weight).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            }
        }
        return clamp(sum);
    }

    /** 单项加权得分（给前端展示，不用自己乘） */
    public static BigDecimal weightedScore(BigDecimal score, BigDecimal weight) {
        if (score == null || weight == null) {
            return null;
        }
        return score.multiply(weight).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** 封顶 120、封底 0，保留 1 位小数 */
    public static BigDecimal clamp(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            v = BigDecimal.ZERO;
        }
        if (v.compareTo(MAX_TOTAL) > 0) {
            v = MAX_TOTAL;
        }
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 按分数映射等级。
     *
     * <p>取「分数落在 [minScore, maxScore] 区间内」的第一个等级；都不命中时，
     * 取 minScore 最小的一个（等价于最低档）——不能返回 null，否则归档时等级为空、
     * 薪酬拿不到系数。</p>
     */
    public static PerfPlanGrade gradeOf(BigDecimal score, List<PerfPlanGrade> grades) {
        if (grades == null || grades.isEmpty() || score == null) {
            return null;
        }
        for (PerfPlanGrade grade : grades) {
            BigDecimal min = grade.getMinScore() == null ? BigDecimal.ZERO : grade.getMinScore();
            BigDecimal max = grade.getMaxScore() == null ? MAX_TOTAL : grade.getMaxScore();
            if (score.compareTo(min) >= 0 && score.compareTo(max) <= 0) {
                return grade;
            }
        }
        return grades.stream()
                .min(Comparator.comparing(g -> g.getMinScore() == null ? BigDecimal.ZERO : g.getMinScore()))
                .orElse(null);
    }

    /** 等级编码 → 系数；找不到时按 1.0（薪酬取系数也遵循同样的兜底） */
    public static BigDecimal coefficientOf(String gradeCode, List<PerfPlanGrade> grades) {
        if (grades == null || gradeCode == null) {
            return BigDecimal.ONE;
        }
        return grades.stream()
                .filter(g -> Objects.equals(g.getGradeCode(), gradeCode))
                .map(PerfPlanGrade::getCoefficient)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(BigDecimal.ONE);
    }
}
