package com.kellen.oa_system.dashboard.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.dashboard.service.DashboardService;
import com.kellen.oa_system.dashboard.vo.DashboardOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台接口。
 *
 * <p>不加按钮权限码：工作台是每个角色登录后的默认落地页，展示的统计口径已按当前登录用户收窄
 * （待办与发起仅统计本人）。若在此加码，等于要求每个新建角色都必须额外授权一次才能进首页。</p>
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        return Result.success(dashboardService.overview());
    }
}
