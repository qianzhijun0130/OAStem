package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 转办请求：把当前待办转给另一用户处理。
 */
@Data
public class ApprovalTransferRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotNull(message = "转办目标用户不能为空")
    private Long targetUserId;
}
