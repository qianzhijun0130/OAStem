package com.kellen.oa_system.hr.performance.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 绩效分布统计。
 */
@Data
public class PerfDistributionVO {

    /** 维度值（部门名 / 等级） */
    private String dimension;

    /** 总人数 */
    private Integer total;

    /** A 人数 */
    private Integer countA;

    /** B 人数 */
    private Integer countB;

    /** C 人数 */
    private Integer countC;

    /** D 人数 */
    private Integer countD;

    /** 平均分 */
    private BigDecimal avgScore;

    /** A 占比 */
    private BigDecimal aRatio;

    /** 1=A 占比超建议上限（仅预警） */
    private Integer overLimit;

}
