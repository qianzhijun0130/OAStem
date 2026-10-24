package com.kellen.oa_system.hr.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 招聘候选人。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit_candidate")
public class RecruitCandidate extends AuditEntity {

    /** 姓名 */
    private String name;

    /** 手机号（唯一，重复投递合并） */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 1男 2女 */
    private Integer gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 应聘职位 */
    private Long postId;

    /** 关联需求 */
    private Long demandId;

    /** 简历附件 oa_file.id */
    private Long resumeFileId;

    /** 1内推 2招聘平台 3猎头 4校招 5主动投递 */
    private Integer source;

    /** 内推人 */
    private Long referrerId;

    /** 1待筛选 2初试 3复试 4终试 5待定 6已发offer 7已入职 8已淘汰 9已放弃 */
    private Integer stage;

    /** 淘汰原因类型（字典 hr_reject_reason） */
    private Integer rejectType;

    /** 淘汰原因 */
    private String rejectReason;

    /** 期望薪资 */
    private BigDecimal expectSalary;

    /** 当前薪资 */
    private BigDecimal currentSalary;

    /** 学历 */
    private String education;

    /** 经验 */
    private String experience;

    /** 1已获授权 0未授权 */
    private Integer authorized;

    /** 1进人才库 0不进 */
    private Integer talentPool;

    /** 简历保留到期日 */
    private LocalDate poolExpireDate;

    /** 转为员工后关联 oa_user.id */
    private Long userId;

    /** 备注 */
    private String remark;
}
