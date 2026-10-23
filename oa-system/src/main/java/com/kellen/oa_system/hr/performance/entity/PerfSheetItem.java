package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 考核表指标行（快照指标名与权重）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_sheet_item")
public class PerfSheetItem extends BaseEntity {

    /** 考核表ID */
    private Long sheetId;

    /** 指标ID */
    private Long indicatorId;

    /** 指标编码快照 */
    private String indicatorCode;

    /** 指标名称快照 */
    private String indicatorName;

    /** 指标类型快照 */
    private Integer indicatorType;

    /** 权重快照 */
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

    /** 排序 */
    private Integer sort;
}
