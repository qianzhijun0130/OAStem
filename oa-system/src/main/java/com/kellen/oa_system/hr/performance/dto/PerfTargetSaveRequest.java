package com.kellen.oa_system.hr.performance.dto;

import com.kellen.oa_system.hr.performance.dto.GoalItem;
import java.util.List;

import lombok.Data;

/**
 * 目标设定（按指标行填目标值与衡量标准）。
 */
@Data
public class PerfTargetSaveRequest {

    /** 考核表ID（本人） */
    private Long sheetId;

    /** 指标目标 */
    private List<GoalItem> items;

}
