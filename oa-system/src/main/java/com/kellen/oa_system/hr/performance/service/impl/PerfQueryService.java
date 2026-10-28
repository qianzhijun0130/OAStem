package com.kellen.oa_system.hr.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.performance.dto.PerfSheetPageQuery;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfPlan;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.hr.performance.support.PerfSheetSupport;
import com.kellen.oa_system.hr.performance.support.PerfVoAssembler;
import com.kellen.oa_system.hr.performance.vo.PerfDistributionVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetDetailVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 绩效结果视图（BE-OPT-08 从 {@code PerfScoreServiceImpl} 拆出）。
 *
 * <p>列表 / 详情 / 分布 / 导出 / 系数查询。两条硬规则：</p>
 * <ul>
 *   <li>员工看不到同部门同事的分数（{@code detail} 只放本人、直属上级、HR）；</li>
 *   <li>系数只取<b>已归档</b>周期——未归档的结果还可能变，不能先进工资（{@code archivedCycles}）。</li>
 * </ul>
 *
 * <p><b>{@code @DataScope} 留在门面方法上</b>：数据范围快照由切面在门面调用前后写入 ThreadLocal，
 * {@code visibleUserIds()} 依赖该快照；把它下沉到本类没有额外收益，反而让「哪个入口会做数据过滤」
 * 变得不直观。</p>
 */
@Service
@RequiredArgsConstructor
public class PerfQueryService {

    private final PerfSheetMapper sheetMapper;
    private final PerfCycleMapper cycleMapper;
    private final PerfPlanMapper planMapper;
    private final DepartmentMapper departmentMapper;
    private final PerfSheetSupport sheetSupport;
    private final PerfVoAssembler assembler;

    public PerfSheetDetailVO mySheet(Long cycleId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        PerfSheet sheet = sheetMapper.selectOne(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)
                .eq(PerfSheet::getUserId, userId)
                .last("LIMIT 1"));
        if (sheet == null) {
            throw new BizException(ErrorCode.PERF_NO_SHEET);
        }
        return assembler.toDetail(sheet, true, true);
    }

    public PageResult<PerfSheetVO> myHistory(Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Long userId = SecurityContextUtil.getCurrentUserId();
        Page<PerfSheet> page = sheetMapper.selectPage(new Page<>(no, size),
                new LambdaQueryWrapper<PerfSheet>()
                        .eq(PerfSheet::getUserId, userId)
                        .orderByDesc(PerfSheet::getCycleId));
        return PageResult.of(assembler.toSheetVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public PageResult<PerfSheetVO> pageSheets(PerfSheetPageQuery query) {
        return pageByWrapper(query, false);
    }

    public PageResult<PerfSheetVO> pageTeam(PerfSheetPageQuery query) {
        return pageByWrapper(query, true);
    }

    /**
     * @param teamOnly true=只看直属下属（主管视角）；false=按数据范围看（HR 视角）
     */
    private PageResult<PerfSheetVO> pageByWrapper(PerfSheetPageQuery query, boolean teamOnly) {
        int no = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int size = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        Long currentUserId = SecurityContextUtil.getCurrentUserId();

        LambdaQueryWrapper<PerfSheet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getCycleId() != null, PerfSheet::getCycleId, query.getCycleId());
        wrapper.eq(query.getDeptId() != null, PerfSheet::getDeptId, query.getDeptId());
        if (StringUtils.hasText(query.getStage())) {
            switch (query.getStage()) {
                case "target" -> wrapper.ne(PerfSheet::getTargetStatus, 2);
                case "self" -> wrapper.eq(PerfSheet::getSelfStatus, 0);
                case "manager" -> wrapper.and(w -> w.eq(PerfSheet::getSelfStatus, 1)
                        .eq(PerfSheet::getManagerStatus, 0));
                case "confirm" -> wrapper.eq(PerfSheet::getManagerStatus, 1)
                        .eq(PerfSheet::getConfirmStatus, 0);
                default -> {
                    // all：不加条件
                }
            }
        }
        if (StringUtils.hasText(query.getKeyword())) {
            List<Long> matched = sheetSupport.matchedUserIds(query.getKeyword());
            if (matched.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(PerfSheet::getUserId, matched);
        }
        if (teamOnly) {
            // 「只能看直属下属」：不按部门放宽，避免主管看到隔壁组
            wrapper.eq(PerfSheet::getLeaderId, currentUserId);
        } else if (!HrRoles.isHr()) {
            Set<Long> visible = sheetSupport.visibleUserIds();
            if (visible.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(PerfSheet::getUserId, visible);
        }
        wrapper.orderByDesc(PerfSheet::getCycleId).orderByAsc(PerfSheet::getUserId);
        Page<PerfSheet> page = sheetMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toSheetVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public PerfSheetDetailVO detail(Long sheetId) {
        PerfSheet sheet = sheetSupport.requireSheet(sheetId);
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        boolean self = Objects.equals(sheet.getUserId(), currentUserId);
        boolean hr = HrRoles.isHr();
        boolean leader = Objects.equals(sheet.getLeaderId(), currentUserId);
        if (!self && !hr && !leader) {
            // 员工看不到同部门同事的分数与排名——这是硬要求
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该考核表");
        }
        // 上级评分阶段默认不显示自评分（避免锚定效应）；本人与 HR 不受限
        boolean showSelfScore = self || hr;
        return assembler.toDetail(sheet, true, showSelfScore);
    }

    public List<PerfDistributionVO> distribution(Long cycleId) {
        List<PerfSheet> sheets = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)
                .isNotNull(PerfSheet::getGradeCode));
        if (sheets.isEmpty()) {
            return List.of();
        }
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        BigDecimal limit = new BigDecimal("0.20");
        if (cycle != null) {
            PerfPlan plan = planMapper.selectById(cycle.getPlanId());
            if (plan != null && plan.getDistributionLimit() != null
                    && plan.getDistributionLimit().compareTo(BigDecimal.ZERO) > 0) {
                limit = plan.getDistributionLimit();
            }
        }
        final BigDecimal aLimit = limit;
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        Map<String, List<PerfSheet>> byDept = new LinkedHashMap<>();
        for (PerfSheet sheet : sheets) {
            String name = deptNames.getOrDefault(sheet.getDeptId(), "未分配部门");
            byDept.computeIfAbsent(name, k -> new ArrayList<>()).add(sheet);
        }
        List<PerfDistributionVO> result = new ArrayList<>();
        for (Map.Entry<String, List<PerfSheet>> entry : byDept.entrySet()) {
            result.add(buildDistribution(entry.getKey(), entry.getValue(), aLimit));
        }
        result.sort(Comparator.comparing(PerfDistributionVO::getDimension));
        return result;
    }

    private PerfDistributionVO buildDistribution(String dimension, List<PerfSheet> sheets, BigDecimal limit) {
        PerfDistributionVO vo = new PerfDistributionVO();
        vo.setDimension(dimension);
        vo.setTotal(sheets.size());
        vo.setCountA((int) sheets.stream().filter(s -> "A".equals(s.getGradeCode())).count());
        vo.setCountB((int) sheets.stream().filter(s -> "B".equals(s.getGradeCode())).count());
        vo.setCountC((int) sheets.stream().filter(s -> "C".equals(s.getGradeCode())).count());
        vo.setCountD((int) sheets.stream().filter(s -> "D".equals(s.getGradeCode())).count());
        BigDecimal sum = sheets.stream().map(s -> s.getTotalScore() == null ? BigDecimal.ZERO : s.getTotalScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setAvgScore(sum.divide(BigDecimal.valueOf(sheets.size()), 1, RoundingMode.HALF_UP));
        BigDecimal ratio = BigDecimal.valueOf(vo.getCountA())
                .divide(BigDecimal.valueOf(sheets.size()), 4, RoundingMode.HALF_UP);
        vo.setARatio(ratio);
        // 只预警不拦截：小团队强制分布会出现「必须有人拿 D」的荒谬结果
        vo.setOverLimit(ratio.compareTo(limit) > 0 ? 1 : 0);
        return vo;
    }

    public void export(Long cycleId, HttpServletResponse response) {
        PerfCycle cycle = sheetSupport.requireCycle(cycleId);
        List<PerfSheetVO> rows = assembler.toSheetVOs(sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)
                .orderByAsc(PerfSheet::getDeptId)
                .orderByAsc(PerfSheet::getUserId)));
        List<String> headers = List.of("工号", "姓名", "部门", "周期", "最终总分", "等级", "系数",
                "目标", "自评", "上级评分", "校准", "确认", "备注");
        List<List<Object>> data = new ArrayList<>();
        for (PerfSheetVO vo : rows) {
            data.add(List.of(
                    PerfSheetSupport.blank(vo.getWorkNumber()), PerfSheetSupport.blank(vo.getRealName()),
                    PerfSheetSupport.blank(vo.getDeptName()),
                    PerfSheetSupport.blank(vo.getCycleName()),
                    vo.getTotalScore() == null ? "" : vo.getTotalScore(),
                    PerfSheetSupport.blank(vo.getGradeCode()),
                    vo.getCoefficient() == null ? "" : vo.getCoefficient(),
                    PerfSheetSupport.blank(vo.getTargetStatusLabel()),
                    Objects.equals(vo.getSelfStatus(), 1) ? "已自评"
                            : (Objects.equals(vo.getSelfMissing(), 1) ? "未自评" : "未提交"),
                    Objects.equals(vo.getManagerStatus(), 1)
                            ? (Objects.equals(vo.getHrScored(), 1) ? "HR 代评" : "已评分") : "未评分",
                    Objects.equals(vo.getCalibrateStatus(), 1) ? "已校准" : "未校准",
                    Objects.equals(vo.getConfirmStatus(), 1) ? "已确认" : "未确认",
                    PerfSheetSupport.blank(vo.getRemark())));
        }
        ExcelExportUtil.write(response, "绩效结果-" + cycle.getCycleCode() + ".xlsx", "绩效结果", headers, data);
    }

    public BigDecimal coefficientOf(Long userId, String month) {
        // 统一走批量实现：单人也走同一条 SQL 路径，避免两份口径不一致
        if (userId == null || !StringUtils.hasText(month)) {
            return null;
        }
        return coefficientMap(List.of(userId), month).get(userId);
    }

    public Map<Long, BigDecimal> coefficientMap(java.util.Collection<Long> userIds, String month) {
        if (userIds == null || userIds.isEmpty() || !StringUtils.hasText(month)) {
            return Map.of();
        }
        List<PerfCycle> cycles = archivedCycles(month);
        if (cycles.isEmpty()) {
            return Map.of();
        }
        Set<Long> cycleIds = cycles.stream().map(PerfCycle::getId).collect(Collectors.toSet());
        List<PerfSheet> sheets = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .in(PerfSheet::getUserId, userIds)
                .in(PerfSheet::getCycleId, cycleIds)
                .isNotNull(PerfSheet::getCoefficient)
                .orderByDesc(PerfSheet::getCycleId));
        Map<Long, BigDecimal> result = new HashMap<>();
        // 已按 cycleId 倒序，putIfAbsent 等价于原实现的「取最新周期那条」
        for (PerfSheet sheet : sheets) {
            result.putIfAbsent(sheet.getUserId(), sheet.getCoefficient());
        }
        return result;
    }

    /** 区间内已归档周期（与 userId 无关，批量与单人共用） */
    private List<PerfCycle> archivedCycles(String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (RuntimeException e) {
            return List.of();
        }
        // 只看已归档周期：未归档的结果还可能变，不能先进工资
        return cycleMapper.selectList(new LambdaQueryWrapper<PerfCycle>()
                .eq(PerfCycle::getStatus, 2)
                .le(PerfCycle::getStartDate, ym.atEndOfMonth())
                .ge(PerfCycle::getEndDate, ym.atDay(1))
                .orderByDesc(PerfCycle::getEndDate));
    }
}
