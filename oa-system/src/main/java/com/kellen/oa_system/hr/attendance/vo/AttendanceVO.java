package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤明细视图。
 */
@Data
public class AttendanceVO {

    private Long id;

    private Long userId;

    private String realName;

    private String workNumber;

    private Long deptId;

    private String deptName;

    private LocalDate attendanceDate;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    /** 上班打卡来源：1 网页打卡 / 2 考勤机 / 3 补卡 */
    private Integer checkInSource;

    /** 下班打卡来源：1 网页打卡 / 2 考勤机 / 3 补卡 */
    private Integer checkOutSource;

    private String checkInLocation;

    private String checkOutLocation;

    /** 工作时长（小时） */
    private BigDecimal workDuration;

    /** 1正常 2迟到 3早退 4缺勤 5假勤 */
    private Integer attendanceStatus;

    private String attendanceStatusLabel;

    private Integer lateMinutes;

    private Integer earlyMinutes;

    /** 缺卡标记：0无 1缺上班卡 2缺下班卡 3缺两次 */
    private Integer cardMissing;

    private String shiftName;

    private Integer settled;

    private String remark;
}
