package com.kellen.oa_system.hr.performance.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * 绩效周期保存。
 */
@Data
public class PerfCycleSaveRequest {

    /** 主键 */
    private Long id;

    /** 周期编码 */
    private String cycleCode;

    /** 周期名称 */
    private String cycleName;

    /** 方案ID */
    private Long planId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 目标设定截止 */
    private LocalDate targetDeadline;

    /** 自评截止 */
    private LocalDate selfDeadline;

    /** 评分截止 */
    private LocalDate scoreDeadline;

    /** 校准截止 */
    private LocalDate calibrateDeadline;

    /** 确认截止 */
    private LocalDate confirmDeadline;

    /** 备注 */
    private String remark;

}
