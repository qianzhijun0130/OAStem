package com.kellen.oa_system.hr.attendance.support;

import com.kellen.oa_system.hr.common.enums.LeaveTypeEnum;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 考勤模块的字面量与状态码。
 *
 * <p>考勤状态码同时决定「前端怎么显示」「薪资怎么扣款」，散落必然分叉：
 * 例如 {@link #STATUS_LEAVE} 与 {@link #STATUS_TRIP} 在月结里<b>不计旷工</b>，
 * 若某处改用字面量 5/6 并漏了这个判断，员工请一天假会被扣两次钱。</p>
 */
public final class AttendanceConstants {

    private AttendanceConstants() {
    }

    public static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_LATE = 2;
    public static final int STATUS_EARLY = 3;
    public static final int STATUS_ABSENT = 4;
    /** 当天有已通过的请假：算正常出勤的一部分，不能计迟到/早退/缺卡（否则工资会双重扣） */
    public static final int STATUS_LEAVE = 5;
    /** 当天在出差中：同上，出差期间免打卡 */
    public static final int STATUS_TRIP = 6;

    /** 月汇总状态：1 未封账 / 2 已封账。封账后结果不可再变 */
    public static final int MONTH_OPEN = 1;
    public static final int MONTH_LOCKED = 2;

    /** 打卡方向：1 上班（签到）/ 2 下班（签退）——补卡、考勤机导入、漏卡清单共用 */
    public static final int DIRECTION_IN = 1;
    public static final int DIRECTION_OUT = 2;

    /** 打卡来源：1 网页打卡 / 2 考勤机 / 3 补卡 */
    public static final int SOURCE_WEB = 1;
    public static final int SOURCE_MACHINE = 2;
    public static final int SOURCE_SUPPLEMENT = 3;

    /** 审批终态：请假单已通过 */
    public static final int LEAVE_APPROVED = 2;
    /** 审批终态：出差单已通过 */
    public static final int TRIP_APPROVED = 2;
    /** 审批终态：加班单已通过 */
    public static final int OVERTIME_APPROVED = 2;

    /** 参与排班的在职状态（试用/正式/待离职） */
    public static final List<Integer> EMPLOYED = List.of(2, 3, 4);

    /**
     * 假别名称（用于月汇总的 {@code leave_days_json} 键）。
     *
     * <p>直接取自 {@link LeaveTypeEnum#labelMap()}，不再本地维护一份：
     * 这份 JSON 的键会被薪资核算当作「事假/病假」的匹配依据。
     * 一旦考勤侧与薪资侧各写一份中文名，改名时只改一处就会让扣款静默失效——
     * 月汇总里明明有 3 天事假，工资条上扣款却是 0，且不报任何错。</p>
     */
    public static final Map<Integer, String> LEAVE_TYPE_LABEL = LeaveTypeEnum.labelMap();

    /** 加班类型 → 薪资倍率分组标签 */
    public static final String OVERTIME_WORKDAY = "工作日";
    public static final String OVERTIME_RESTDAY = "休息日";
    public static final String OVERTIME_HOLIDAY = "节假日";
}
