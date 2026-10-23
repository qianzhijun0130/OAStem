package com.kellen.oa_system.hr.performance.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 方案等级映射。
 */
@Data
public class PerfGradeVO {

    /** 等级编码 */
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
