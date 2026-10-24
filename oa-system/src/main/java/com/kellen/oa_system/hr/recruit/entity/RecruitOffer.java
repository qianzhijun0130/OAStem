package com.kellen.oa_system.hr.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘 Offer。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit_offer")
public class RecruitOffer extends AuditEntity {

    /** Offer 编号 */
    private String offerNo;

    /** 候选人 */
    private Long candidateId;

    /** 职位 */
    private Long postId;

    /** 岗位 */
    private Long positionId;

    /** 职级/岗位名称快照 */
    private String positionName;

    /** 薪资 */
    private BigDecimal salary;

    /** 试用期月数 */
    private Integer probationMonths;

    /** 报到日期 */
    private LocalDate reportDate;

    /** 有效期截止 */
    private LocalDate expireDate;

    /** 1待审批 2已发出 3已接受 4已拒绝 5已过期 6已作废 */
    private Integer status;

    /** 1邮件 2短信 3纸质 */
    private Integer sendWay;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 接受时间 */
    private LocalDateTime acceptTime;

    /** 拒绝理由 */
    private String refuseReason;

    /** 转为员工后关联 oa_user.id */
    private Long userId;

    /** 转员工留痕（复用/新建 + 操作人） */
    private String convertLog;

    /** 备注 */
    private String remark;
}
