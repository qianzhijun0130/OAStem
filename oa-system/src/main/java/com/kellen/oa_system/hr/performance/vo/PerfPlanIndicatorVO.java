package com.kellen.oa_system.hr.performance.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 方案下的指标与权重。
 */
@Data
public class PerfPlanIndicatorVO {

    /** 指标ID */
    private Long indicatorId;

    /** 指标编码 */
    private String indicatorCode;

    /** 指标名称 */
    private String indicatorName;

    /** 1定量 2定性 3加分 4扣分 */
    private Integer indicatorType;

    /** 计量单位 */
    private String unit;

    /** 权重 */
    private BigDecimal weight;

    /** 排序 */
    private Integer sort;

}
