package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点表单字段权限实体，对应表 oa_flow_node_form_field。
 *
 * <p>回答的问题是「这份表单在<b>这个节点</b>上，每个字段能看/能改到什么程度」，
 * 与 {@link FlowNodeForm}（这个节点用哪份表单）职责分离。</p>
 *
 * <p><b>未配置即默认只读</b>：审批场景下默认放开风险更高 —— 审批人误改申请人填的内容，
 * 比看不到字段更难追责。因此渲染时以「有配置用配置、无配置按只读」为准。</p>
 *
 * <p>唯一索引 {@code (flow_def_id, node_key, prop, active_flag)} 保证同一节点同一字段
 * 只有一条存活配置，软删除后可立即重新配置。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_node_form_field")
public class FlowNodeFormField extends AuditEntity {

    /** 关联 oa_flow_definition.id */
    private Long flowDefId;

    /**
     * BPMN taskDefinitionKey（如 deptLeaderAudit）；发起环节用约定的 init。
     *
     * <p>刻意存 key 而非节点名称：节点改名是常见操作，若用名称做键，改名会丢配置。</p>
     */
    private String nodeKey;

    /** 节点名称（冗余存储，便于列表直接展示） */
    private String nodeName;

    /** 关联 oa_form.id（冗余 oa_flow_node_form.form_id，便于按表单反查） */
    private Long formId;

    /** FormCreate 规则里的 prop（如 days、cancelDate） */
    private String prop;

    /** 字段显示名（冗余存储，便于列表直接展示） */
    private String fieldLabel;

    /** 权限：1只读 2可编辑 3隐藏。缺省 1 */
    private Integer permission;

    /** 创建人 id（与 AuditEntity 分离记录，供审计追溯） */
    private Long createBy;

    /** 更新人 id */
    private Long updateBy;
}
