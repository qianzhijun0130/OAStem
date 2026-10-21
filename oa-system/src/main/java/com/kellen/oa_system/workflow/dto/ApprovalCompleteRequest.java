package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

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

    /**
     * 本节点可编辑字段的补丁（字段标识 → 新值）。
     *
     * <p>用于「销假节点填销假信息」这类场景：前端把可编辑字段的改动提交上来，
     * 后端<b>只合并该节点被授权可编辑的字段</b>，其余一律忽略。</p>
     *
     * <p>不传表示不改表单数据 —— 审批人不填任何字段时保持原样。</p>
     */
    private Map<String, Object> formDataPatch;
}
