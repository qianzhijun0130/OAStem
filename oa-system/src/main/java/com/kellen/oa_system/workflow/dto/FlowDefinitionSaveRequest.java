package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程模板保存请求。
 *
 * <p>bpmnXml 由前端 bpmn-js modeler 产出；首次保存或需要调整流程结构时必填，
 * 后端据此部署到 Flowable 并把 deploymentId / bpmnKey 落库。formFields / nodeConfig
 * 为 JSON 字符串，驱动前端动态表单与运行时审批人解析。</p>
 */
@Data
public class FlowDefinitionSaveRequest {

    /** 更新时传原 id */
    private Long id;

    @NotBlank(message = "流程编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "流程编码只能包含字母、数字、下划线")
    private String flowCode;

    @NotBlank(message = "流程名称不能为空")
    @Size(max = 100, message = "流程名称长度不能超过 100")
    private String flowName;

    @Size(max = 32, message = "分类长度不能超过 32")
    private String category;

    /** 前端 modeler 产出的 BPMN XML（调整流程结构时必填） */
    private String bpmnXml;

    /** 表单字段配置 JSON */
    private String formFields;

    /** 节点审批人规则 JSON */
    private String nodeConfig;

    /** 1启用 0停用，缺省 1 */
    @Min(value = 0, message = "状态只能是 0停用 1启用")
    @Max(value = 1, message = "状态只能是 0停用 1启用")
    private Integer status = 1;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
