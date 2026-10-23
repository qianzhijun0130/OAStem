package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 方案等级映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_plan_grade")
public class PerfPlanGrade extends BaseEntity {

    /** 方案ID */
    private Long planId;

    /** A/B/C/D */
    private String gradeCode;

    /** 等级名称 */
    private String gradeName;

    /** 分数下限 */
    private BigDecimal minScore;

    /** 分数上限 */
    private BigDecimal maxScore;

    /** 绩效系数 */
    private BigDecimal coefficient;

    /** 排序 */
    private Integer sort;
}
