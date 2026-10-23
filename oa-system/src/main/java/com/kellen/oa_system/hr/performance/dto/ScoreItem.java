package com.kellen.oa_system.hr.performance.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 单项评分。
 */
@Data
public class ScoreItem {

    /** 考核表指标行ID */
    private Long itemId;

    /** 分数 */
    private BigDecimal score;

    /** 说明 */
    private String comment;

}
