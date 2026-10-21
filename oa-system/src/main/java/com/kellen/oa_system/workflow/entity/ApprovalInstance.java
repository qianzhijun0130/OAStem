package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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

    /** 单据编号（系统生成：流程编码-日期-序号），发起或存草稿时写入 */
    private String billCode;

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

    /**
     * 当前节点应处理截止时间（阶段四·超时）。
     *
     * <p>由节点配置 {@code timeoutHours}（缺省取全局 {@code approval.timeout.hours}）算出，
     * 落库而非每次实时向引擎查询，是为了让「流程实例」列表能一次 SQL 判断逾期，避免逐行查任务的 N+1。
     * 无超时配置时为 null；流程结束时清空。</p>
     */
    private LocalDateTime dueTime;

    /**
     * 本次节点超时的处置时间（幂等标记）。
     *
     * <p>定时任务只处理 {@code due_time <= now && timeout_handled_at IS NULL} 的实例，
     * 处置后写入该时间，避免每分钟重复提醒/重复驳回；流程进入下一节点时由
     * {@code assignCurrentTasks} 重置为 null，使新节点能重新计时并再次触发。</p>
     */
    private LocalDateTime timeoutHandledAt;
}
