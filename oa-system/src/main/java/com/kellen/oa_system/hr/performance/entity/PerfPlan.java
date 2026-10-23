package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 绩效方案。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_plan")
public class PerfPlan extends AuditEntity {

    /** 方案编码 */
    private String planCode;

    /** 方案名称 */
    private String planName;

    /** 1月度 2季度 3半年 4年度 */
    private Integer cycleType;

    /** 1百分制 2五级制 */
    private Integer scoreMode;

    /** 适用部门（逗号分隔，空=全部） */
    private String deptIds;

    /** 适用岗位序列 */
    private String series;

    /** 1开启强制分布（仅预警） */
    private Integer forceDistribution;

    /** A 等级建议占比上限 */
    private BigDecimal distributionLimit;

    /** 1草稿 2启用 3停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
