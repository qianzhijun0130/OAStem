package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批单实例实体，对应表 oa_approval_instance。
 *
 * <p>每一条申请（请假/报销…）对应一行；status 遵循项目计划定义的状态机：
 * 1草稿 2审批中 3已通过 4已驳回 5已撤销。proc_inst_id 关联 Flowable 流程实例。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_instance")
public class ApprovalInstance extends AuditEntity {

    /** 关联流程模板 */
    private Long flowDefId;

    /** 流程编码 */
    private String flowCode;

    /** 申请标题 */
    private String title;

    /** 发起人 */
    private Long applicantId;

    /** Flowable 流程实例 id */
    private String procInstId;

    /** 表单数据 JSON */
    private String formData;

    /** 1草稿 2审批中 3已通过 4已驳回 5已撤销 */
    private Integer status;

    /** 当前节点 */
    private String currentNode;
}
