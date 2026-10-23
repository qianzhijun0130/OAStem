package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 考勤记录，对应表 oa_attendance。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_attendance")
public class Attendance extends AuditEntity {

    /** 用户ID */
    private Long userId;

    /** 考勤日期 */
    private LocalDate attendanceDate;

    /** 签到时间 */
    private LocalDateTime checkInTime;

    /** 签退时间 */
    private LocalDateTime checkOutTime;

    /** 签到地点 */
    private String checkInLocation;

    /** 签退地点 */
    private String checkOutLocation;

    /** 工作时长（小时） */
    private java.math.BigDecimal workDuration;

    /** 状态：1正常 2迟到 3早退 4缺勤 5假勤 */
    private Integer attendanceStatus;

    /** 迟到分钟数 */
    private Integer lateMinutes;

    /** 早退分钟数 */
    private Integer earlyMinutes;

    private String remark;

    /** 缺卡标记：0无 1缺上班卡 2缺下班卡 3缺两次 */
    private Integer cardMissing;

    /** 上班打卡来源：1系统 2考勤机 3补卡 */
    private Integer checkInSource;

    /** 下班打卡来源：1系统 2考勤机 3补卡 */
    private Integer checkOutSource;

    /** 班次ID */
    private Long shiftId;

    /** 是否已日结：0否 1是 */
    private Integer settled;
}
