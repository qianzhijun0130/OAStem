package com.kellen.oa_system.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作台聚合视图：一次请求返回首屏所需的全部数据，避免前端串行请求多个接口。
 */
@Data
public class DashboardOverviewVO {

    /** 员工总数（启用中，不含禁用与已删除） */
    private Long userCount;

    private Long deptCount;

    /** 我的待办数量 */
    private Long todoCount;

    /** 我的发起数量 */
    private Long myApplyCount;

    /** 近 6 个月审批数量趋势，无数据的月份补 0 */
    private List<ChartItemVO> monthlyTrend;

    /** 审批类型占比 */
    private List<ChartItemVO> typeDistribution;

    /** 最新公告（置顶优先） */
    private List<LatestNoticeVO> latestNotices;
}
