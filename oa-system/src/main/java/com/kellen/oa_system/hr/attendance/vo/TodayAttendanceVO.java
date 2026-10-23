package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 「我的考勤」今日卡片。
 */
@Data
public class TodayAttendanceVO {

    private LocalDate date;

    /** 是否已排班；false 时前端不显示打卡按钮（未排班不产生考勤记录） */
    private Boolean scheduled;

    private String shiftName;

    private LocalTime workStart;

    private LocalTime workEnd;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    /** 1正常 2迟到 3早退 4缺勤 5假勤；未打卡时为 null */
    private Integer attendanceStatus;

    private String attendanceStatusLabel;

    private Integer lateMinutes;

    private Integer earlyMinutes;

    /** 已打卡时长（分钟），未打下班卡时为 null */
    private Long workedMinutes;

    private Boolean canClockIn;

    private Boolean canClockOut;
}
