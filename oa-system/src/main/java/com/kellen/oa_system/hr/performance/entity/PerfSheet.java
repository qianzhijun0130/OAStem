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

    /**
     * 结果来源：1 系统评分 / 2 结果导入（历史迁移）。
     *
     * <p>必须区分：导入的分数不是走完「自评→上级评→校准→确认」得来的，
     * 员工看到一条 85 分要能判断它是不是自己上司打的，申诉时 HR 也要说得清来源。</p>
     */
    private Integer source;

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
