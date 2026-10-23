package com.kellen.oa_system.hr.performance.vo;

import com.kellen.oa_system.hr.performance.vo.PerfGradeVO;
import com.kellen.oa_system.hr.performance.vo.PerfPlanIndicatorVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 绩效方案视图。
 */
@Data
public class PerfPlanVO {

    /** 主键 */
    private Long id;

    /** 方案编码 */
    private String planCode;

    /** 方案名称 */
    private String planName;

    /** 周期类型 */
    private Integer cycleType;

    /** 周期类型文本 */
    private String cycleTypeLabel;

    /** 评分制 */
    private Integer scoreMode;

    /** 适用部门 */
    private String deptIds;

    /** 适用部门名称 */
    private String deptNames;

    /** 适用序列 */
    private String series;

    /** 强制分布 */
    private Integer forceDistribution;

    /** A 级占比上限 */
    private BigDecimal distributionLimit;

    /** 状态 */
    private Integer status;

    /** 状态文本 */
    private String statusLabel;

    /** 备注 */
    private String remark;

    /** 等级映射 */
    private List<PerfGradeVO> grades;

    /** 指标与权重 */
    private List<PerfPlanIndicatorVO> indicators;

    /** 权重合计（校验用） */
    private BigDecimal weightTotal;

    /** 创建时间 */
    private LocalDateTime createTime;

}
