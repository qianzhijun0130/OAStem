package com.kellen.oa_system.hr.performance.vo;

import java.time.LocalDate;

import lombok.Data;

/**
 * 绩效周期视图（含进度统计）。
 */
@Data
public class PerfCycleVO {

    /** 主键 */
    private Long id;

    /** 周期编码 */
    private String cycleCode;

    /** 周期名称 */
    private String cycleName;

    /** 方案ID */
    private Long planId;

    /** 方案名称 */
    private String planName;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 阶段 */
    private Integer stage;

    /** 阶段文本 */
    private String stageLabel;

    /** 目标截止 */
    private LocalDate targetDeadline;

    /** 自评截止 */
    private LocalDate selfDeadline;

    /** 评分截止 */
    private LocalDate scoreDeadline;

    /** 校准截止 */
    private LocalDate calibrateDeadline;

    /** 确认截止 */
    private LocalDate confirmDeadline;

    /** 1进行中 2已归档 */
    private Integer status;

    /** 考核表总数 */
    private Integer sheetCount;

    /** 目标已锁定 */
    private Integer targetDone;

    /** 已自评 */
    private Integer selfDone;

    /** 已评分 */
    private Integer managerDone;

    /** 已确认 */
    private Integer confirmDone;

    /** 备注 */
    private String remark;

}
