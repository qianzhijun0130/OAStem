package com.kellen.oa_system.hr.performance.dto;


import lombok.Data;

/**
 * 绩效指标保存。
 */
@Data
public class PerfIndicatorSaveRequest {

    /** 主键 */
    private Long id;

    /** 指标编码 */
    private String indicatorCode;

    /** 指标名称 */
    private String indicatorName;

    /** 指标定义 */
    private String definition;

    /** 1定量 2定性 3加分 4扣分 */
    private Integer indicatorType;

    /** 计量单位 */
    private String unit;

    /** 五档评分标准 */
    private String scoreStandard;

    /** 适用序列 */
    private String series;

    /** 状态 */
    private Integer status;

    /** 备注 */
    private String remark;

}
