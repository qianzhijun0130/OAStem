package com.kellen.oa_system.workflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 审批详情视图：申请单 + 历史时间轴 + 当前待办（流程结束后 currentTask 为 null）。
 */
@Data
public class ApprovalDetailVO {

    private ApprovalInstanceVO instance;
    private List<ApprovalRecordVO> records;
    private ApprovalTodoVO currentTask;
}
