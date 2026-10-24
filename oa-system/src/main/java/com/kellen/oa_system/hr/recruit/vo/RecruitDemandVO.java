package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘需求 VO。
 */
@Data
public class RecruitDemandVO {

    private Long id;
    private String demandCode;
    private Long deptId;
    private String deptName;
    private Long positionId;
    private String positionName;
    private Integer headCount;
    private Integer urgency;
    private String urgencyLabel;
    private LocalDate expectDate;
    private String education;
    private String experience;
    private String skillRequire;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Integer reasonType;
    private String reasonTypeLabel;
    private String reason;
    private Integer status;
    private String statusLabel;
    private Long hrId;
    private String hrName;
    private LocalDateTime approveTime;
    private LocalDateTime finishTime;

    /** 编制总数（该部门该岗位） */
    private Integer quotaTotal;

    /** 在岗人数 */
    private Integer quotaUsed;

    /** 编制缺口 */
    private Integer quotaGap;

    /**
     * 是否超编制。
     *
     * <p>超编制<b>只提示不拦截</b>——超编招聘在现实中存在，由审批人判断。
     * 但前端要明确标出来，否则申请人会误以为系统没算缺口。</p>
     */
    private Integer overQuota;

    /** 招聘周期（天）：审批通过 → 完成 */
    private Integer cycleDays;

    /** 已招聘到岗人数 */
    private Integer hiredCount;

    private String remark;
    private LocalDateTime createTime;
}
