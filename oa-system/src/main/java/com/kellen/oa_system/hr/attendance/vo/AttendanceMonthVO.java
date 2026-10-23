package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度考勤汇总视图。
 */
@Data
public class AttendanceMonthVO {

    private Long id;

    private Long userId;

    private String realName;

    private String workNumber;

    private Long deptId;

    private String deptName;

    /** yyyy-MM */
    private String month;

    private BigDecimal shouldDays;

    private BigDecimal actualDays;

    private Integer lateCount;

    private Integer earlyCount;

    private BigDecimal absentDays;

    private Integer missingCount;

    private BigDecimal tripDays;

    /** 1 未封账 2 已封账 */
    private Integer status;

    private LocalDateTime lockTime;
}
