package com.kellen.oa_system.dashboard.service;

import com.kellen.oa_system.dashboard.vo.DashboardOverviewVO;

/**
 * 工作台统计服务。
 */
public interface DashboardService {

    /**
     * 首屏聚合数据：卡片统计 + 图表数据 + 最新公告。
     *
     * <p>当前登录用户由实现内部通过 {@code SecurityContextUtil} 获取，Controller 不传 userId。</p>
     */
    DashboardOverviewVO overview();
}
