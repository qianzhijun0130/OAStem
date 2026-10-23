package com.kellen.oa_system.hr.performance.dto;


import lombok.Data;

/**
 * 单条指标目标。
 */
@Data
public class GoalItem {

    /** 考核表指标行ID */
    private Long itemId;

    /** 目标值 */
    private String targetValue;

    /** 衡量标准 */
    private String measureStandard;

}
