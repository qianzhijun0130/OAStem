package com.kellen.oa_system.hr.performance.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 申诉处理。
 */
@Data
public class PerfAppealHandleRequest {

    /** 申诉ID */
    private Long appealId;

    /** 2维持原结果 3调整 */
    private Integer status;

    /** 处理意见 */
    private String opinion;

    /** 调整后分数（status=3 时必填） */
    private BigDecimal score;

}
