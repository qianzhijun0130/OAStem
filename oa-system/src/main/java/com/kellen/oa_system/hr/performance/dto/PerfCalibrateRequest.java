package com.kellen.oa_system.hr.performance.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * HR 校准（改等级必须填理由）。
 */
@Data
public class PerfCalibrateRequest {

    /** 考核表ID */
    private Long sheetId;

    /** 调整后总分（可空=不改分） */
    private BigDecimal score;

    /** 调整后等级（可空=按分数自动映射） */
    private String gradeCode;

    /** 校准理由（必填） */
    private String reason;

}
