package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点↔表单绑定实体，对应表 oa_flow_node_form。
 *
 * <p>用独立关联表而非把 formId 塞进 {@code oa_flow_definition.node_config}：
 * node_config 描述的是「谁审批」，formId 描述的是「填什么」，两者生命周期不同——
 * 表单可能被多个流程复用，也可能改版后需要同步更新所有引用。</p>
 *
 * <p>唯一索引 {@code (flow_def_id, node_key, active_flag)} 保证同一流程同一节点只绑一个表单，
 * 删除后（active_flag 变 NULL）可重新绑定。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_node_form")
public class FlowNodeForm extends AuditEntity {

    /** 关联 oa_flow_definition.id */
    private Long flowDefId;

    /** BPMN taskDefinitionKey（如 deptLeaderAudit）；发起环节用约定的 init */
    private String nodeKey;

    /** 节点名称（冗余存储，便于列表直接展示，避免前端额外查流程定义） */
    private String nodeName;

    /** 关联 oa_form.id */
    private Long formId;

    /** 1=发起环节主表单（发起页据此渲染「申请内容」区块）；0=非主表单 */
    private Integer isPrimary;

    /** 排序号 */
    private Integer sort;

    /** 创建人 id（与 AuditEntity 分离记录，供审计追溯） */
    private Long createBy;

    /** 更新人 id */
    private Long updateBy;
}
