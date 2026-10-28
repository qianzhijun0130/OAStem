package com.kellen.oa_system.hr.salary.support;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * 薪酬模块的字面量与状态码。
 *
 * <p>集中在这里的唯一目的是「改口径只改一处」：状态码同时被核算、列表、工资条、状态推进四段使用，
 * 散落在各协作类里一旦有一个没跟着改，就会出现「列表显示已封账但还能调整」这类自相矛盾。</p>
 */
public final class SalaryConstants {

    private SalaryConstants() {
    }

    public static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 核算记录状态 */
    public static final int STATUS_DRAFT = 1;
    public static final int STATUS_REVIEWED = 2;
    public static final int STATUS_LOCKED = 3;
    public static final int STATUS_PAID = 4;

    /** 考勤月结的「已封账」状态 */
    public static final int MONTH_LOCKED_STATUS = 2;

    public static final Map<Integer, String> STATUS_LABEL = Map.of(
            STATUS_DRAFT, "草稿",
            STATUS_REVIEWED, "已复核",
            STATUS_LOCKED, "已封账",
            STATUS_PAID, "已发放");

    /** 任职状态：已离职 */
    public static final int EMPLOYMENT_RESIGNED = 5;

    /** 员工异动类型：离职 */
    public static final int CHANGE_TYPE_RESIGN = 5;

    /** 参与考勤扣款的假别（婚假/产假等法定假别不扣钱，由政策决定是否支付，不做扣款） */
    public static final String LEAVE_PERSONAL = "事假";
    public static final String LEAVE_SICK = "病假";

    /** 核算引擎直接引用的编码，删掉会导致计算链路断裂 */
    public static final Set<String> BUILTIN_ITEM_CODES = Set.of(
            "BASE", "POST", "PERF", "ALLOW", "OT", "BONUS", "ATT", "SOCIAL", "FUND", "TAX", "OTHER");

    /** 累计预扣数的数组下标（见 {@link SalaryAmounts#zeroCumulative()}） */
    public static final int CUM_INCOME = 0;
    public static final int CUM_SPECIAL = 1;
    public static final int CUM_EXTRA = 2;
    public static final int CUM_TAX = 3;
}
