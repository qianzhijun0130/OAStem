package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板定义实体，对应表 oa_flow_definition。
 *
 * <p>form_fields / node_config / flow_config / bpmn_key / deployment_id 与 Flowable 引擎联动：
 * 保存模板时把前端 modeler 产出的 BPMN XML 部署到 Flowable，并把流程 key、部署 id 落库；
 * node_config 描述每个审批节点的「审批人规则」（指定人员 / 指定角色 / 部门主管）；
 * flow_config 描述排他网关每条出线的「分支条件」，部署前注入 BPMN 的 conditionExpression。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_definition")
public class FlowDefinition extends AuditEntity {

    /** 流程编码（如 leave/overtime），全局唯一，同时作为 Flowable 流程 key 的命名空间 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 分类 */
    private String category;

    /** 流程图标（Lucide 图标名，如 FileText）；为空时前端用默认图标 */
    private String icon;

    /** Flowable 流程定义 key（对应 BPMN process 的 id） */
    private String bpmnKey;

    /** Flowable 部署 id */
    private String deploymentId;

    /** 表单字段配置 JSON（字段名/类型/是否必填/选项值） */
    private String formFields;

    /** 节点审批人规则 JSON：[{nodeId,nodeName,assigneeType,assigneeValue}] */
    private String nodeConfig;

    /**
     * 网关分支条件配置 JSON：[{flowId,gatewayId,gatewayName,targetNodeId,targetNodeName,
     * logic,isDefault,conditions:[{field,op,valueType,value}]}]。
     *
     * <p>结构化条件的唯一事实源；保存时由 BpmnXmlNormalizer#injectConditions 注入 BPMN，
     * 重新打开设计器按此 JSON 回显，无需从 JUEL 文本逆向解析。NULL = 无可视化条件。</p>
     */
    private String flowConfig;

    /** 1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
