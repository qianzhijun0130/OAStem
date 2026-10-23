package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 方案指标与权重。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_plan_indicator")
public class PerfPlanIndicator extends BaseEntity {

    /** 方案ID */
    private Long planId;

    /** 指标ID */
    private Long indicatorId;

    /** 权重百分比 */
    private BigDecimal weight;

    /** 排序 */
    private Integer sort;
}
