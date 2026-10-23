package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


/**
 * 月度考勤汇总，对应表 oa_attendance_month。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_attendance_month")
public class AttendanceMonth extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 月份 yyyy-MM */
    private String month;

    /** 应出勤天数 */
    private java.math.BigDecimal shouldDays;

    /** 实际出勤天数 */
    private java.math.BigDecimal actualDays;

    /** 迟到次数 */
    private Integer lateCount;

    /** 早退次数 */
    private Integer earlyCount;

    /** 旷工天数 */
    private java.math.BigDecimal absentDays;

    /** 缺卡次数 */
    private Integer missingCount;

    /** 各假别天数 */
    private String leaveDaysJson;

    /** 各类加班小时 */
    private String overtimeJson;

    /** 出差天数 */
    private java.math.BigDecimal tripDays;

    /** 状态：1未封账 2已封账 */
    private Integer status;

    /** 封账时间 */
    private LocalDateTime lockTime;

    /** 封账人 */
    private Long lockBy;
}
