package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发起审批请求。发起人取当前登录用户，无需前端传。
 */
@Data
public class ApprovalStartRequest {

    @NotBlank(message = "流程编码不能为空")
    private String flowCode;

    @NotBlank(message = "申请标题不能为空")
    private String title;

    /** 表单数据 JSON（字段名→值），由前端按模板 formFields 动态渲染后收集 */
    @NotBlank(message = "表单数据不能为空")
    private String formData;
}
