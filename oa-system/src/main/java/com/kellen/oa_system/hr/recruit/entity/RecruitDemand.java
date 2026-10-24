package com.kellen.oa_system.hr.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘需求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit_demand")
public class RecruitDemand extends AuditEntity {

    /** 需求编号 ZP-{yyyyMM}-{4位流水} */
    private String demandCode;

    /** 需求部门 */
    private Long deptId;

    /** 需求岗位 */
    private Long positionId;

    /** 招聘人数 */
    private Integer headCount;

    /** 1普通 2紧急 */
    private Integer urgency;

    /** 期望到岗日期 */
    private LocalDate expectDate;

    /** 学历要求 */
    private String education;

    /** 经验年限要求 */
    private String experience;

    /** 技能要求 */
    private String skillRequire;

    /** 薪资范围下限 */
    private BigDecimal salaryMin;

    /** 薪资范围上限 */
    private BigDecimal salaryMax;

    /** 1新增 2替补 3储备 */
    private Integer reasonType;

    /** 招聘原因说明 */
    private String reason;

    /** 1待审批 2招聘中 3已暂停 4已完成 5已关闭 6已驳回 */
    private Integer status;

    /** 负责HR */
    private Long hrId;

    /** 审批通过时间 */
    private LocalDateTime approveTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;
}
