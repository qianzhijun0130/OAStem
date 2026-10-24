package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 候选人保存请求。
 *
 * <p><b>手机号必填且唯一</b>：同一候选人多次投递要合并到同一人，
 * 否则一人两条档案会让后续面试、offer 全部对不上。</p>
 */
@Data
public class RecruitCandidateSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 1男 2女 */
    private Integer gender;

    /** 出生日期 */
    private java.time.LocalDate birthDate;

    /** 应聘职位 */
    private Long postId;

    /** 关联需求 */
    private Long demandId;

    /** 简历附件 */
    private Long resumeFileId;

    /** 1内推 2招聘平台 3猎头 4校招 5主动投递 */
    private Integer source;

    /** 内推人 */
    private Long referrerId;

    /** 期望薪资 */
    private BigDecimal expectSalary;

    /** 当前薪资 */
    private BigDecimal currentSalary;

    /** 学历 */
    private String education;

    /** 经验 */
    private String experience;

    /** 1已获授权 0未授权 */
    @NotNull(message = "必须确认是否已获得候选人授权")
    private Integer authorized;

    /** 1进人才库 0不进 */
    private Integer talentPool;

    /** 备注 */
    private String remark;
}
