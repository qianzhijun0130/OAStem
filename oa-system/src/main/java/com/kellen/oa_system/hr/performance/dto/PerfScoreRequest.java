package com.kellen.oa_system.hr.performance.dto;

import com.kellen.oa_system.hr.performance.dto.ScoreItem;
import java.util.List;

import lombok.Data;

/**
 * 评分（自评 / 上级评分共用）。
 */
@Data
public class PerfScoreRequest {

    /** 考核表ID */
    private Long sheetId;

    /** 逐项评分 */
    private List<ScoreItem> items;

    /** 总评语（上级评分时必填） */
    private String comment;

}
