package com.kellen.oa_system.hr.performance.dto;


import lombok.Data;

/**
 * 绩效申诉。
 */
@Data
public class PerfAppealRequest {

    /** 考核表ID */
    private Long sheetId;

    /** 申诉理由 */
    private String reason;

}
