package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起审批请求。发起人取当前登录用户，无需前端传。
 */
@Data
public class ApprovalStartRequest {

    @NotBlank(message = "流程编码不能为空")
    private String flowCode;

    /**
     * 申请标题。
     *
     * <p>长度上限 200 与 {@code oa_approval_instance.title} 的 {@code varchar(200)} 对齐：
     * 不在入口拦截，超长值会撞 MySQL 严格模式的 Data too long，
     * 把一个本可预料的输入问题变成 5000 系统错误（D6 缺陷）。</p>
     */
    @NotBlank(message = "申请标题不能为空")
    @Size(max = 200, message = "申请标题长度不能超过200个字符")
    private String title;

    /** 表单数据 JSON（字段名→值），由前端按模板 formFields 动态渲染后收集 */
    @NotBlank(message = "表单数据不能为空")
    private String formData;
}
