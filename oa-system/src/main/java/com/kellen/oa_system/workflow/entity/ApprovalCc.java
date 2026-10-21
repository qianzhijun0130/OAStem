package com.kellen.oa_system.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批抄送记录，对应表 oa_approval_cc。
 *
 * <p>一条记录 = 「某张审批单被抄送给某人」。抄送只用于告知，不产生待办、不影响流程流转。
 * 唯一键 {@code (instance_id, user_id, active_flag)} 保证同一单据对同一人只抄送一次，
 * 逻辑删除后 active_flag 变 NULL 让位，可再次抄送（与项目其他表的唯一索引策略一致）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_cc")
public class ApprovalCc extends AuditEntity {

    private Long instanceId;

    /** 被抄送人 */
    private Long userId;

    /** 执行抄送操作的人 */
    private Long ccBy;

    private String remark;

    /** 0 未读、1 已读 */
    private Integer readFlag;

    private LocalDateTime readTime;
}
