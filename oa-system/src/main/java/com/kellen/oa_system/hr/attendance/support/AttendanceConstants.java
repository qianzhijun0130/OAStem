package com.kellen.oa_system.hr.attendance.support;

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

    /** 审批终态：请假单已通过 */
    public static final int LEAVE_APPROVED = 2;
    /** 审批终态：出差单已通过 */
    public static final int TRIP_APPROVED = 2;
    /** 审批终态：加班单已通过 */
    public static final int OVERTIME_APPROVED = 2;

    /** 参与排班的在职状态（试用/正式/待离职） */
    public static final List<Integer> EMPLOYED = List.of(2, 3, 4);

    /** 假别名称（与字典 hr_leave_type 一致，用于月汇总的 leave_days_json） */
    public static final Map<Integer, String> LEAVE_TYPE_LABEL = Map.of(
            1, "事假", 2, "病假", 3, "年假", 4, "婚假",
            5, "产假", 6, "陪产假", 7, "丧假", 8, "调休");

    /** 加班类型 → 薪资倍率分组标签 */
    public static final String OVERTIME_WORKDAY = "工作日";
    public static final String OVERTIME_RESTDAY = "休息日";
    public static final String OVERTIME_HOLIDAY = "节假日";
}
