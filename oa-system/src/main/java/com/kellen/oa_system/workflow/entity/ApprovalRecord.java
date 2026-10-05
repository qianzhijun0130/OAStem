package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批操作记录实体，对应表 oa_approval_record。
 *
 * <p>每一次动作（提交/同意/驳回/转办/撤销）记一行。该表同时驱动「我的已办」列表
 * 与审批详情页的「历史时间轴」，避免直接依赖 Flowable 历史表做按候选人检索。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_record")
public class ApprovalRecord extends AuditEntity {

    /** 关联审批单 */
    private Long instanceId;

    /** Flowable 任务 id */
    private String taskId;

    /** 节点 id */
    private String nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 操作人 */
    private Long handlerId;

    /** submit / approve / reject / transfer / withdraw */
    private String action;

    /** 审批意见 */
    private String opinion;
}
