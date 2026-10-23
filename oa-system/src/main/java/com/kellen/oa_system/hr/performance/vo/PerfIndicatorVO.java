package com.kellen.oa_system.hr.performance.vo;


import lombok.Data;

/**
 * 绩效指标视图。
 */
@Data
public class PerfIndicatorVO {

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

    /** 类型文本 */
    private String indicatorTypeLabel;

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
