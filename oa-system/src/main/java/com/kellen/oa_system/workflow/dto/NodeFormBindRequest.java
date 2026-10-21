package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程节点绑定表单入参。
 */
@Data
public class NodeFormBindRequest {

    /** 流程模板 id */
    @NotNull(message = "流程模板 id 不能为空")
    private Long flowDefId;

    /** BPMN taskDefinitionKey；发起环节统一用 init */
    @Size(max = 100, message = "节点标识长度不能超过 100")
    private String nodeKey = "init";

    @Size(max = 100, message = "节点名称长度不能超过 100")
    private String nodeName;

    /** 目标表单 id */
    @NotNull(message = "表单 id 不能为空")
    private Long formId;

    /** 1=发起环节主表单；0=非主表单。默认 1 */
    private Integer isPrimary = 1;
}
