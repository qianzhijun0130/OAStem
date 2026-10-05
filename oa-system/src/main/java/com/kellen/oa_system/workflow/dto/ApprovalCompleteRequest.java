package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审批（同意/驳回）请求。
 */
@Data
public class ApprovalCompleteRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /** approve 同意 / reject 驳回 */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    private String opinion;
}
