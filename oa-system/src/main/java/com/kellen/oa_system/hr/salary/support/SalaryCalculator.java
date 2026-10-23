package com.kellen.oa_system.hr.salary.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 薪资计算口径。
 *
 * <p>把「怎么算钱」集中在一个类里，而不是散在 Service 各处：薪资是最容易被
 * 「顺手改一行」改错的地方，集中之后单测可以只针对这一个类。</p>
 *
 * <p>三个必须守住的约定（详见《03_薪酬管理》）：</p>
 * <ol>
 *   <li><b>日工资 = 月标准工资 / 21.75</b>。用当月实际天数会让 2 月与 3 月的同一天单价不同，
 *       员工一算就发现「同样的假，扣的钱不一样」；</li>
 *   <li><b>个税按累计预扣法</b>。按月单独算会在年中跳档时多扣或少扣，年度汇算还要补退；</li>
 *   <li><b>全程 BigDecimal 且 HALF_UP 保留 2 位</b>。用 double 会出现 0.01 的分差，
 *       工资条上就是对不上账。</li>
 * </ol>
 */
public final class SalaryCalculator {

    /** 制度计薪天数（人社部口径 (365-104)/12 = 21.75） */
    public static final BigDecimal WORK_DAYS_PER_MONTH = new BigDecimal("21.75");

    /** 每日标准工时 */
    public static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");

    /** 个税起征点（每月减除费用） */
    public static final BigDecimal MONTHLY_TAX_FREE = new BigDecimal("5000");

    /** 病假期间工资按 60% 支付 → 扣 40% */
    public static final BigDecimal SICK_LEAVE_DEDUCT_RATE = new BigDecimal("0.4");

    /** 旷工按 2 倍日工资扣（1 倍是「没上班没工资」，另 1 倍是处罚） */
    public static final BigDecimal ABSENT_DEDUCT_MULTIPLE = new BigDecimal("2");

    public static final BigDecimal OT_WEEKDAY = new BigDecimal("1.5");
    public static final BigDecimal OT_WEEKEND = new BigDecimal("2");
    public static final BigDecimal OT_HOLIDAY = new BigDecimal("3");

    /** 七级超额累进税率表：{上限, 税率, 速算扣除数}，最后一个上限为 null 表示无上限 */
    private static final Object[][] TAX_TABLE = {
            {new BigDecimal("36000"), new BigDecimal("0.03"), BigDecimal.ZERO},
            {new BigDecimal("144000"), new BigDecimal("0.10"), new BigDecimal("2520")},
            {new BigDecimal("300000"), new BigDecimal("0.20"), new BigDecimal("16920")},
            {new BigDecimal("420000"), new BigDecimal("0.25"), new BigDecimal("31920")},
            {new BigDecimal("660000"), new BigDecimal("0.30"), new BigDecimal("52920")},
            {new BigDecimal("960000"), new BigDecimal("0.35"), new BigDecimal("85920")},
            {null, new BigDecimal("0.45"), new BigDecimal("181920")},
    };

    private SalaryCalculator() {
    }

    /**
     * 按累计应纳税所得额算「累计应纳税额」。
     *
     * <p>超额累进不是「整笔乘最高档税率」——那样 36001 元会比 36000 元到手更少。
     * 用「全额累进表 + 速算扣除数」表达，结果与分段累加完全一致。</p>
     */
    public static BigDecimal cumulativeTax(BigDecimal cumulativeTaxable) {
        if (cumulativeTaxable == null || cumulativeTaxable.compareTo(BigDecimal.ZERO) <= 0) {
            return scale(BigDecimal.ZERO);
        }
        for (Object[] row : TAX_TABLE) {
            BigDecimal upper = (BigDecimal) row[0];
            if (upper == null || cumulativeTaxable.compareTo(upper) <= 0) {
                BigDecimal rate = (BigDecimal) row[1];
                BigDecimal quick = (BigDecimal) row[2];
                return scale(cumulativeTaxable.multiply(rate).subtract(quick));
            }
        }
        // 循环一定会在最后一档返回，这里仅为编译通过
        throw new BizException(ErrorCode.SYSTEM_ERROR, "个税税率表配置异常");
    }

    /** 日工资 = 月标准工资 / 21.75 */
    public static BigDecimal dailyWage(BigDecimal monthlyStandard) {
        if (monthlyStandard == null || monthlyStandard.compareTo(BigDecimal.ZERO) <= 0) {
            return scale(BigDecimal.ZERO);
        }
        return scale(monthlyStandard.divide(WORK_DAYS_PER_MONTH, 4, RoundingMode.HALF_UP));
    }

    /** 小时工资 = 日工资 / 8 */
    public static BigDecimal hourlyWage(BigDecimal monthlyStandard) {
        return scale(dailyWage(monthlyStandard).divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP));
    }

    /**
     * 考勤扣款。
     *
     * @param monthlyStandard 月标准工资（基本+岗位+绩效+津贴）
     * @param personalDays    事假天数
     * @param sickDays        病假天数
     * @param absentDays      旷工天数
     */
    public static BigDecimal attendanceDeduct(BigDecimal monthlyStandard,
                                              BigDecimal personalDays,
                                              BigDecimal sickDays,
                                              BigDecimal absentDays) {
        BigDecimal daily = dailyWage(monthlyStandard);
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(daily.multiply(nz(personalDays)));
        total = total.add(daily.multiply(SICK_LEAVE_DEDUCT_RATE).multiply(nz(sickDays)));
        total = total.add(daily.multiply(ABSENT_DEDUCT_MULTIPLE).multiply(nz(absentDays)));
        return scale(total);
    }

    /**
     * 加班费。
     *
     * <p>转调休的加班已在业务侧折算成假期，这里只算「要发钱」的部分，
     * 两者不能同时给——同时给等于一次加班付两次。</p>
     */
    public static BigDecimal overtimePay(BigDecimal monthlyStandard,
                                         BigDecimal weekdayHours,
                                         BigDecimal weekendHours,
                                         BigDecimal holidayHours) {
        BigDecimal hourly = hourlyWage(monthlyStandard);
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(hourly.multiply(OT_WEEKDAY).multiply(nz(weekdayHours)));
        total = total.add(hourly.multiply(OT_WEEKEND).multiply(nz(weekendHours)));
        total = total.add(hourly.multiply(OT_HOLIDAY).multiply(nz(holidayHours)));
        return scale(total);
    }

    /** 基数按政策上下限截断（下限 0 表示不限，上限 0 表示不封顶） */
    public static BigDecimal clampBase(BigDecimal base, BigDecimal min, BigDecimal max) {
        BigDecimal value = nz(base);
        if (min != null && min.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(min) < 0) {
            value = min;
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(max) > 0) {
            value = max;
        }
        return scale(value);
    }

    /** 统一保留 2 位（四舍五入），所有出口都走这里，避免各处精度不一 */
    public static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
