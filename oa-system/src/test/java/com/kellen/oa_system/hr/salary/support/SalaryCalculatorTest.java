package com.kellen.oa_system.hr.salary.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「怎么算钱」的口径测试。
 *
 * <p>这不是为了覆盖率——这些算式一旦改错，错的是发到员工卡里的钱。
 * 因此每个算式都刻意包含两类断言：</p>
 * <ol>
 *   <li><b>边界值</b>：0 / null / 负数 / 档位临界点；</li>
 *   <li><b>杀手断言</b>：注入本类头部注释列出的典型错误后必然变红。
 *       例如把 21.75 换成「当月天数」、把日工资的除数写成 21.75 而非 21.75×8、
 *       把超额累进写成「整笔乘最高档」。</li>
 * </ol>
 */
class SalaryCalculatorTest {

    private static final BigDecimal M8700 = new BigDecimal("8700");

    // ---------------- 日工资 / 小时工资 ----------------

    @Test
    @DisplayName("法定月计薪天数是 21.75，不能改成当月天数")
    void workDaysPerMonthIsFixed() {
        assertEquals(0, new BigDecimal("21.75").compareTo(SalaryCalculator.WORK_DAYS_PER_MONTH));
    }

    @Test
    @DisplayName("日工资 = 月标准 ÷ 21.75")
    void dailyWage() {
        // 8700 / 21.75 = 400 整除，便于人工核对
        assertEquals(0, new BigDecimal("400.00").compareTo(SalaryCalculator.dailyWage(M8700)));
        // 非整除：10000 / 21.75 = 459.7701… → 459.77
        assertEquals(0, new BigDecimal("459.77").compareTo(SalaryCalculator.dailyWage(new BigDecimal("10000"))));
    }

    @Test
    @DisplayName("杀手：日工资只由常量 21.75 决定，与月份天数无关")
    void dailyWageIndependentOfMonthLength() {
        // 若有人把除数改成「当月天数」（28/30/31），下面这个整除关系立刻不成立
        assertEquals(0, new BigDecimal("1000.00").compareTo(SalaryCalculator.dailyWage(new BigDecimal("21750"))));
        assertEquals(0, new BigDecimal("2000.00").compareTo(SalaryCalculator.dailyWage(new BigDecimal("43500"))));
    }

    @Test
    @DisplayName("日工资对 0 / null / 负数返回 0.00")
    void dailyWageGuards() {
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.dailyWage(null)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.dailyWage(BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.dailyWage(new BigDecimal("-100"))));
    }

    @Test
    @DisplayName("小时工资 = 日工资 ÷ 8")
    void hourlyWage() {
        assertEquals(0, new BigDecimal("50.00").compareTo(SalaryCalculator.hourlyWage(M8700)));
    }

    @Test
    @DisplayName("杀手：小时工资除数是 21.75×8，漏乘 8 会让加班费放大 8 倍")
    void hourlyWageDividesByHoursToo() {
        // 21750 / 21.75 = 1000（日），再 /8 = 125（时）。若漏乘 8 会得到 1000
        assertEquals(0, new BigDecimal("125.00").compareTo(SalaryCalculator.hourlyWage(new BigDecimal("21750"))));
        assertTrue(SalaryCalculator.hourlyWage(new BigDecimal("21750")).compareTo(new BigDecimal("1000")) < 0);
    }

    // ---------------- 考勤扣款 ----------------

    @Test
    @DisplayName("考勤扣款：事假 ×1、病假 ×0.4、旷工 ×2")
    void attendanceDeductSingleType() {
        BigDecimal one = BigDecimal.ONE;
        assertEquals(0, new BigDecimal("400.00").compareTo(
                SalaryCalculator.attendanceDeduct(M8700, one, BigDecimal.ZERO, BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("160.00").compareTo(
                SalaryCalculator.attendanceDeduct(M8700, BigDecimal.ZERO, one, BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("800.00").compareTo(
                SalaryCalculator.attendanceDeduct(M8700, BigDecimal.ZERO, BigDecimal.ZERO, one)));
    }

    @Test
    @DisplayName("考勤扣款：三类可叠加，合计手算一致")
    void attendanceDeductCombined() {
        BigDecimal one = BigDecimal.ONE;
        // 400 + 400×0.4 + 400×2 = 400 + 160 + 800 = 1360
        assertEquals(0, new BigDecimal("1360.00").compareTo(
                SalaryCalculator.attendanceDeduct(M8700, one, one, one)));
    }

    @Test
    @DisplayName("杀手：旷工倍率是 2 而不是照抄病假的 0.4，两者不能混用")
    void absentMultipleIsNotSickRate() {
        BigDecimal absent = SalaryCalculator.attendanceDeduct(M8700, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        BigDecimal sick = SalaryCalculator.attendanceDeduct(M8700, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO);
        assertTrue(absent.compareTo(sick) > 0, "旷工扣款应大于病假扣款");
        assertEquals(0, new BigDecimal("2").compareTo(SalaryCalculator.ABSENT_DEDUCT_MULTIPLE));
        assertEquals(0, new BigDecimal("0.4").compareTo(SalaryCalculator.SICK_LEAVE_DEDUCT_RATE));
    }

    @Test
    @DisplayName("考勤扣款：半天假与 null 天数")
    void attendanceDeductHalfDayAndNull() {
        // 半天事假 = 200
        assertEquals(0, new BigDecimal("200.00").compareTo(
                SalaryCalculator.attendanceDeduct(M8700, new BigDecimal("0.5"), null, null)));
        // 全 null 视为无扣款
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                SalaryCalculator.attendanceDeduct(M8700, null, null, null)));
    }

    // ---------------- 加班费 ----------------

    @Test
    @DisplayName("加班费三档倍率：工作日 1.5 / 休息日 2 / 节假日 3")
    void overtimePayRates() {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        // 时薪 50
        assertEquals(0, new BigDecimal("75.00").compareTo(
                SalaryCalculator.overtimePay(M8700, one, zero, zero)));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                SalaryCalculator.overtimePay(M8700, zero, one, zero)));
        assertEquals(0, new BigDecimal("150.00").compareTo(
                SalaryCalculator.overtimePay(M8700, zero, zero, one)));
    }

    @Test
    @DisplayName("杀手：三档倍率顺序不能错位（休息日给成 1.5 会导致少发）")
    void overtimeRatesAreStrictlyIncreasing() {
        BigDecimal weekday = SalaryCalculator.overtimePay(M8700, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal weekend = SalaryCalculator.overtimePay(M8700, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO);
        BigDecimal holiday = SalaryCalculator.overtimePay(M8700, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        assertTrue(weekday.compareTo(weekend) < 0);
        assertTrue(weekend.compareTo(holiday) < 0);
    }

    @Test
    @DisplayName("加班费：0 小时或全 null 返回 0.00")
    void overtimePayGuards() {
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                SalaryCalculator.overtimePay(M8700, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                SalaryCalculator.overtimePay(M8700, null, null, null)));
    }

    // ---------------- 累计预扣个税 ----------------

    @Test
    @DisplayName("个税：0 与负数返回 0.00")
    void cumulativeTaxNonPositive() {
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.cumulativeTax(null)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.cumulativeTax(BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("-8000"))));
    }

    @Test
    @DisplayName("个税七级税率表：各档位上限处的累计应纳税额")
    void cumulativeTaxBrackets() {
        // 36000 × 3% = 1080
        assertEquals(0, new BigDecimal("1080.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("36000"))));
        // 144000 × 10% − 2520 = 11880
        assertEquals(0, new BigDecimal("11880.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("144000"))));
        // 300000 × 20% − 16920 = 43080
        assertEquals(0, new BigDecimal("43080.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("300000"))));
        // 420000 × 25% − 31920 = 73080
        assertEquals(0, new BigDecimal("73080.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("420000"))));
        // 660000 × 30% − 52920 = 145080
        assertEquals(0, new BigDecimal("145080.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("660000"))));
        // 960000 × 35% − 85920 = 250080
        assertEquals(0, new BigDecimal("250080.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("960000"))));
        // 1000000 × 45% − 181920 = 268080
        assertEquals(0, new BigDecimal("268080.00").compareTo(SalaryCalculator.cumulativeTax(new BigDecimal("1000000"))));
    }

    @Test
    @DisplayName("杀手：跨档处税额必须连续，不能出现「多赚 1 元少拿钱」")
    void cumulativeTaxIsContinuousAcrossBrackets() {
        BigDecimal at36000 = SalaryCalculator.cumulativeTax(new BigDecimal("36000"));
        BigDecimal above36000 = SalaryCalculator.cumulativeTax(new BigDecimal("36000.01"));
        // 超额累进：临界点两侧税额只差 0.001（四舍五入后相等或略增），
        // 若写成「整笔乘最高档」，above36000 会跳到 3600.00，下面断言立刻变红
        assertTrue(above36000.compareTo(at36000) >= 0, "跨档后税额不应变小");
        assertTrue(above36000.subtract(at36000).compareTo(new BigDecimal("1")) < 0,
                "跨档处税额不应跳变（应连续，差值 < 1 元）");
    }

    @Test
    @DisplayName("杀手：税额随应纳税所得额单调不减")
    void cumulativeTaxMonotonic() {
        BigDecimal prev = BigDecimal.ZERO;
        for (String v : new String[]{"0", "1000", "36000", "36001", "100000", "300001", "1000000", "5000000"}) {
            BigDecimal tax = SalaryCalculator.cumulativeTax(new BigDecimal(v));
            assertTrue(tax.compareTo(prev) >= 0, "应纳税所得额 " + v + " 的税额小于前一个档位: " + tax);
            prev = tax;
        }
    }

    // ---------------- 社保基数截断 ----------------

    @Test
    @DisplayName("基数截断：低于下限取下限，高于上限取上限，区间内原值")
    void clampBaseBounds() {
        BigDecimal min = new BigDecimal("2000");
        BigDecimal max = new BigDecimal("30000");
        assertEquals(0, new BigDecimal("2000.00").compareTo(
                SalaryCalculator.clampBase(new BigDecimal("1000"), min, max)));
        assertEquals(0, new BigDecimal("30000.00").compareTo(
                SalaryCalculator.clampBase(new BigDecimal("50000"), min, max)));
        assertEquals(0, new BigDecimal("8000.00").compareTo(
                SalaryCalculator.clampBase(new BigDecimal("8000"), min, max)));
    }

    @Test
    @DisplayName("杀手：0 表示「不限」而不是真实上下限，不能被当成 0 元基数")
    void clampBaseZeroMeansUnlimited() {
        // 上限 0 → 不封顶
        assertEquals(0, new BigDecimal("99999.00").compareTo(
                SalaryCalculator.clampBase(new BigDecimal("99999"), BigDecimal.ZERO, BigDecimal.ZERO)));
        // 下限 0 → 不托底
        assertEquals(0, new BigDecimal("100.00").compareTo(
                SalaryCalculator.clampBase(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("基数截断：null 基数按 0 处理，null 上下限视为不限")
    void clampBaseNulls() {
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.clampBase(null, null, null)));
        assertEquals(0, new BigDecimal("5000.00").compareTo(
                SalaryCalculator.clampBase(new BigDecimal("5000"), null, null)));
    }

    // ---------------- 精度 ----------------

    @Test
    @DisplayName("杀手：统一四舍五入（HALF_UP），不是银行家舍入")
    void scaleUsesHalfUp() {
        // HALF_UP: 2.005 → 2.01；HALF_EVEN 会得到 2.00
        assertEquals(0, new BigDecimal("2.01").compareTo(SalaryCalculator.scale(new BigDecimal("2.005"))));
        assertEquals(0, new BigDecimal("2.02").compareTo(SalaryCalculator.scale(new BigDecimal("2.015"))));
        // null 归零
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(SalaryCalculator.scale(null)));
    }

    @Test
    @DisplayName("所有出口都是 2 位小数，避免工资条出现 0.001 级别的尾差")
    void allOutputsHaveTwoDecimals() {
        assertEquals(2, SalaryCalculator.dailyWage(new BigDecimal("10000")).scale());
        assertEquals(2, SalaryCalculator.hourlyWage(new BigDecimal("10000")).scale());
        assertEquals(2, SalaryCalculator.attendanceDeduct(M8700, BigDecimal.ONE, null, null).scale());
        assertEquals(2, SalaryCalculator.overtimePay(M8700, BigDecimal.ONE, null, null).scale());
        assertEquals(2, SalaryCalculator.cumulativeTax(new BigDecimal("12345.67")).scale());
        assertEquals(2, SalaryCalculator.clampBase(new BigDecimal("1234.567"), null, null).scale());
    }
}
