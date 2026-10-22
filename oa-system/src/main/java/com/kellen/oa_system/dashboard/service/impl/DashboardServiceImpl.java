package com.kellen.oa_system.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.dashboard.mapper.DashboardMapper;
import com.kellen.oa_system.dashboard.service.DashboardService;
import com.kellen.oa_system.dashboard.vo.ChartItemVO;
import com.kellen.oa_system.dashboard.vo.DashboardOverviewVO;
import com.kellen.oa_system.dashboard.vo.LatestNoticeVO;
import com.kellen.oa_system.notice.service.NoticeService;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作台统计实现。
 *
 * <p>数据来源跨了用户、部门、审批、公告四个模块——这是工作台作为「聚合视图」的固有形态，
 * 因此这里直接依赖各模块的 Mapper，而不是给每个模块加一个统计方法（那会让统计口径散落各处）。
 * 唯一的例外是公告：它的可见性判定有明确业务规则，必须复用 {@link NoticeService}，
 * 否则「指定部门/人员」的公告会被所有人看到。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 趋势图展示的月份数（含当月） */
    private static final int TREND_MONTHS = 6;
    /**
     * 工作台「通知公告」展示条数。
     *
     * <p>取 5 条：右栏还要放日历，再多会把整屏撑长；置顶优先的排序在 SQL 里完成
     * （{@code ORDER BY top_flag DESC, publish_time DESC}），所以置顶公告一定落在前几条。</p>
     */
    private static final int LATEST_NOTICE_LIMIT = 5;
    private static final int USER_STATUS_ENABLED = 1;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardMapper dashboardMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final FlowableEngineService engineService;
    private final NoticeService noticeService;

    @Override
    public DashboardOverviewVO overview() {
        Long userId = SecurityContextUtil.getCurrentUserId();

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setUserCount(countEnabledUsers());
        vo.setDeptCount(departmentMapper.selectCount(new LambdaQueryWrapper<Department>()));
        vo.setTodoCount(engineService.countCandidateTasks(String.valueOf(userId)));
        vo.setMyApplyCount(countMyApplications(userId));
        vo.setMonthlyTrend(monthlyTrend());
        vo.setTypeDistribution(dashboardMapper.countTypeDistribution());
        vo.setLatestNotices(latestNotices());
        return vo;
    }

    private Long countEnabledUsers() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, USER_STATUS_ENABLED));
    }

    private Long countMyApplications(Long userId) {
        return approvalInstanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getApplicantId, userId));
    }

    /**
     * 近 6 个月趋势，无数据的月份补 0。
     *
     * <p>补零是必须的：SQL 只会返回「有审批单」的月份，若直接丢给 ECharts，
     * 中间断档的月份会连成一条跨越数月的直线，趋势会被误读。</p>
     */
    private List<ChartItemVO> monthlyTrend() {
        LocalDate startMonth = LocalDate.now().withDayOfMonth(1).minusMonths(TREND_MONTHS - 1L);
        Map<String, Long> counted = dashboardMapper.countMonthlyTrend(startMonth.atStartOfDay()).stream()
                .collect(Collectors.toMap(ChartItemVO::getLabel, ChartItemVO::getTotal, (a, b) -> a));

        List<ChartItemVO> trend = new ArrayList<>(TREND_MONTHS);
        for (int i = 0; i < TREND_MONTHS; i++) {
            String label = startMonth.plusMonths(i).format(MONTH_FORMATTER);
            ChartItemVO item = new ChartItemVO();
            item.setLabel(label);
            item.setTotal(counted.getOrDefault(label, 0L));
            trend.add(item);
        }
        return trend;
    }

    /**
     * 最新公告。
     *
     * <p>走 {@link NoticeService#listLatest(int)} 而不是直接查表：公告有「全部 / 指定部门 /
     * 指定人员」三种发布范围，绕开服务层就绕开了可见性过滤，工作台会变成越权入口。</p>
     */
    private List<LatestNoticeVO> latestNotices() {
        return noticeService.listLatest(LATEST_NOTICE_LIMIT).stream().map(item -> {
            LatestNoticeVO vo = new LatestNoticeVO();
            vo.setId(item.getId());
            vo.setNoticeTitle(item.getNoticeTitle());
            vo.setNoticeType(item.getNoticeType());
            vo.setNoticeLevel(item.getNoticeLevel());
            vo.setTopFlag(item.getTopFlag());
            vo.setPublishTime(item.getPublishTime());
            vo.setPublisherName(item.getPublisherName());
            return vo;
        }).toList();
    }
}
