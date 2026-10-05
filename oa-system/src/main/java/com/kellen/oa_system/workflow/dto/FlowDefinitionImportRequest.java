package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程模板导入请求：上传一份 BPMN XML，由后端解析并生成流程模板。
 *
 * <p>flowCode / flowName 可留空，留空时取 BPMN 里 {@code <process id/name>} 的值；
 * 节点审批人按 BPMN 中的约定自动解析（详见 BpmnXmlParser），未约定的节点标记为「未配置」。</p>
 */
@Data
public class FlowDefinitionImportRequest {

    /** BPMN XML 文本（前端读取文件后原样提交） */
    @NotBlank(message = "BPMN 文件内容不能为空")
    private String bpmnXml;

    /** 流程编码，可空；留空取 BPMN 的 process id */
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "流程编码只能包含字母、数字、下划线")
    private String flowCode;

    /** 流程名称，可空；留空取 BPMN 的 process name */
    @Size(max = 100, message = "流程名称长度不能超过 100")
    private String flowName;

    @Size(max = 32, message = "分类长度不能超过 32")
    private String category;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
