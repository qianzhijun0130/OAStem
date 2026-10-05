package com.kellen.oa_system.dashboard.vo;

import lombok.Data;

/**
 * 图表通用数据项：一个分类名 + 一个数值。
 *
 * <p>月度趋势与类型占比共用此结构，前端 ECharts 直接消费，无需为两种图各写一套取数逻辑。</p>
 */
@Data
public class ChartItemVO {

    /** 分类名：月份（yyyy-MM）或审批类型名称 */
    private String label;

    private Long total;
}
