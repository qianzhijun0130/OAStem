package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 考核表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_sheet")
public class PerfSheet extends AuditEntity {

    /** 周期ID */
    private Long cycleId;

    /** 员工ID */
    private Long userId;

    /** 部门快照 */
    private Long deptId;

    /** 直属上级快照 */
    private Long leaderId;

    /** 方案ID */
    private Long planId;

    /** 0未提交 1待审核 2已锁定 3已驳回 */
    private Integer targetStatus;

    /** 0未提交 1已提交 */
    private Integer selfStatus;

    /** 0未评分 1已评分 */
    private Integer managerStatus;

    /** 0未校准 1已校准 */
    private Integer calibrateStatus;

    /** 0未确认 1已确认 */
    private Integer confirmStatus;

    /** 最终总分 */
    private BigDecimal totalScore;

    /** 自评总分 */
    private BigDecimal selfTotal;

    /** 上级评分总分 */
    private BigDecimal managerTotal;

    /** 等级 */
    private String gradeCode;

    /** 绩效系数 */
    private BigDecimal coefficient;

    /** 1超期未自评 */
    private Integer selfMissing;

    /** 1HR 代评 */
    private Integer hrScored;

    /** 上级总评语 */
    private String managerComment;

    /** 备注 */
    private String remark;
}
