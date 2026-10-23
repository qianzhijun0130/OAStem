package com.kellen.oa_system.hr.performance.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 考核表指标行。
 */
@Data
public class PerfSheetItemVO {

    /** 行ID */
    private Long id;

    /** 指标ID */
    private Long indicatorId;

    /** 指标编码 */
    private String indicatorCode;

    /** 指标名称 */
    private String indicatorName;

    /** 1定量 2定性 3加分 4扣分 */
    private Integer indicatorType;

    /** 权重 */
    private BigDecimal weight;

    /** 目标值 */
    private String targetValue;

    /** 衡量标准 */
    private String measureStandard;

    /** 自评分 */
    private BigDecimal selfScore;

    /** 自评说明 */
    private String selfComment;

    /** 上级评分 */
    private BigDecimal managerScore;

    /** 上级评语 */
    private String managerComment;

    /** 最终分 */
    private BigDecimal finalScore;

    /** 加权得分（展示用） */
    private BigDecimal weightedScore;

}
