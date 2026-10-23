package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 绩效考核周期。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_cycle")
public class PerfCycle extends AuditEntity {

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

    /** 1目标设定 2自评 3上级评分 4校准 5确认 6归档 */
    private Integer stage;

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

    /** 1进行中 2已归档 */
    private Integer status;

    /** 备注 */
    private String remark;
}
