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

    /** 假别明细原文（JSON），前端可直接渲染成「年假 2 天 · 病假 1 天」 */
    private String leaveDaysJson;

    /** 假别明细的展示文本（后端拼好，导出与页面共用一套口径） */
    private String leaveDaysText;

    /** 加班时数原文（JSON，按工作日/休息日/节假日分档） */
    private String overtimeJson;

    private String overtimeText;
}
