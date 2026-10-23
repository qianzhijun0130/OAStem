package com.kellen.oa_system.hr.performance.dto;

import com.kellen.oa_system.hr.performance.vo.PerfGradeVO;
import com.kellen.oa_system.hr.performance.vo.PerfPlanIndicatorVO;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 绩效方案保存（含等级映射与指标权重）。
 */
@Data
public class PerfPlanSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 方案编码 */
    private String planCode;

    /** 方案名称 */
    private String planName;

    /** 1月度 2季度 3半年 4年度 */
    private Integer cycleType;

    /** 1百分制 2五级制 */
    private Integer scoreMode;

    /** 适用部门（逗号分隔） */
    private String deptIds;

    /** 适用序列 */
    private String series;

    /** 是否强制分布 */
    private Integer forceDistribution;

    /** A 级建议占比上限 */
    private BigDecimal distributionLimit;

    /** 1草稿 2启用 3停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 等级映射（不传则用默认 A/B/C/D） */
    private List<PerfGradeVO> grades;

    /** 方案指标与权重（合计必须 100） */
    private List<PerfPlanIndicatorVO> indicators;

}
