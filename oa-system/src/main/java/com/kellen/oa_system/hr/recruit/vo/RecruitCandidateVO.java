package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候选人 VO。
 */
@Data
public class RecruitCandidateVO {

    private Long id;
    private String name;
    private String mobile;
    /** 手机号脱敏（列表展示用，避免截图外泄） */
    private String mobileMasked;
    private String email;
    private Integer gender;
    private String genderLabel;
    private LocalDate birthDate;
    private Long postId;
    private String postName;
    private Long demandId;
    private String demandCode;
    private Long resumeFileId;
    private String resumeFileName;
    private Integer source;
    private String sourceLabel;
    private Long referrerId;
    private String referrerName;
    private Integer stage;
    private String stageLabel;
    private Integer rejectType;
    private String rejectReason;
    private BigDecimal expectSalary;
    private BigDecimal currentSalary;
    private String education;
    private String experience;
    private Integer authorized;
    private Integer talentPool;
    private LocalDate poolExpireDate;
    private Long userId;
    private String remark;
    private LocalDateTime createTime;

    /** 面试次数 */
    private Integer interviewCount;
}
