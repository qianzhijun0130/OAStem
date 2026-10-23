package com.kellen.oa_system.hr.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.performance.dto.PerfIndicatorSaveRequest;
import com.kellen.oa_system.hr.performance.dto.PerfPlanSaveRequest;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfPlan;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfPlanIndicator;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfIndicatorMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanGradeMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanIndicatorMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanMapper;
import com.kellen.oa_system.hr.performance.service.PerfPlanService;
import com.kellen.oa_system.hr.performance.vo.PerfGradeVO;
import com.kellen.oa_system.hr.performance.vo.PerfIndicatorVO;
import com.kellen.oa_system.hr.performance.vo.PerfPlanIndicatorVO;
import com.kellen.oa_system.hr.performance.vo.PerfPlanVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 绩效方案与指标库实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfPlanServiceImpl implements PerfPlanService {

    private static final Map<Integer, String> CYCLE_TYPE_LABEL = Map.of(
            1, "月度", 2, "季度", 3, "半年", 4, "年度");

    private static final Map<Integer, String> PLAN_STATUS_LABEL = Map.of(
            1, "草稿", 2, "启用", 3, "停用");

    private static final Map<Integer, String> INDICATOR_TYPE_LABEL = Map.of(
            1, "定量", 2, "定性", 3, "加分项", 4, "扣分项");

    private final PerfPlanMapper planMapper;
    private final PerfPlanGradeMapper gradeMapper;
    private final PerfPlanIndicatorMapper planIndicatorMapper;
    private final PerfIndicatorMapper indicatorMapper;
    private final PerfCycleMapper cycleMapper;
    private final DepartmentMapper departmentMapper;

    // ==================================================================
    // 方案
    // ==================================================================

    @Override
    public PageResult<PerfPlanVO> pagePlans(String keyword, Integer status, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<PerfPlan> wrapper = new LambdaQueryWrapper<PerfPlan>()
                .orderByDesc(PerfPlan::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PerfPlan::getPlanName, keyword)
                    .or().like(PerfPlan::getPlanCode, keyword));
        }
        if (status != null) {
            wrapper.eq(PerfPlan::getStatus, status);
        }
        Page<PerfPlan> page = planMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toPlanVOs(page.getRecords(), false), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public PerfPlanVO detail(Long id) {
        PerfPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.PERF_PLAN_NOT_FOUND);
        }
        return toPlanVOs(List.of(plan), true).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePlan(PerfPlanSaveRequest request) {
        PerfPlan duplicate = planMapper.selectOne(new LambdaQueryWrapper<PerfPlan>()
                .eq(PerfPlan::getPlanCode, request.getPlanCode()).last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "方案编码已存在");
        }

        // 权重合计必须 100%：差 0.5% 就不是「小问题」，而是有人会被少算一档
        List<PerfPlanIndicatorVO> indicators = request.getIndicators() == null
                ? List.of() : request.getIndicators();
        BigDecimal weightTotal = indicators.stream()
                .map(i -> i.getWeight() == null ? BigDecimal.ZERO : i.getWeight())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!indicators.isEmpty() && weightTotal.subtract(new BigDecimal("100")).abs()
                .compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException(ErrorCode.PERF_WEIGHT_NOT_100,
                    "指标权重合计必须等于 100%，当前为 " + weightTotal.stripTrailingZeros().toPlainString() + "%");
        }
        Set<Long> ids = indicators.stream().map(PerfPlanIndicatorVO::getIndicatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.size() != indicators.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "同一指标不能重复添加");
        }

        PerfPlan entity = request.getId() == null ? new PerfPlan() : planMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.PERF_PLAN_NOT_FOUND);
        }
        entity.setPlanCode(request.getPlanCode());
        entity.setPlanName(request.getPlanName());
        entity.setCycleType(request.getCycleType() == null ? 2 : request.getCycleType());
        entity.setScoreMode(request.getScoreMode() == null ? 1 : request.getScoreMode());
        entity.setDeptIds(request.getDeptIds());
        entity.setSeries(request.getSeries());
        entity.setForceDistribution(request.getForceDistribution() == null ? 0 : request.getForceDistribution());
        entity.setDistributionLimit(request.getDistributionLimit() == null
                ? new BigDecimal("0.20") : request.getDistributionLimit());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            planMapper.insert(entity);
        } else {
            planMapper.updateById(entity);
        }

        // 等级映射与方案指标是「方案的一部分」，改动直接整体替换（历史值已快照到考核表，不会丢）
        gradeMapper.delete(new LambdaQueryWrapper<PerfPlanGrade>().eq(PerfPlanGrade::getPlanId, entity.getId()));
        List<PerfGradeVO> grades = request.getGrades() == null || request.getGrades().isEmpty()
                ? defaultGrades() : request.getGrades();
        int sort = 1;
        for (PerfGradeVO vo : grades) {
            PerfPlanGrade grade = new PerfPlanGrade();
            grade.setPlanId(entity.getId());
            grade.setGradeCode(vo.getGradeCode());
            grade.setGradeName(StringUtils.hasText(vo.getGradeName()) ? vo.getGradeName() : vo.getGradeCode());
            grade.setMinScore(vo.getMinScore() == null ? BigDecimal.ZERO : vo.getMinScore());
            grade.setMaxScore(vo.getMaxScore() == null ? new BigDecimal("120") : vo.getMaxScore());
            grade.setCoefficient(vo.getCoefficient() == null ? BigDecimal.ONE : vo.getCoefficient());
            grade.setSort(vo.getSort() == null ? sort : vo.getSort());
            gradeMapper.insert(grade);
            sort++;
        }

        planIndicatorMapper.delete(new LambdaQueryWrapper<PerfPlanIndicator>()
                .eq(PerfPlanIndicator::getPlanId, entity.getId()));
        int idx = 1;
        for (PerfPlanIndicatorVO vo : indicators) {
            PerfPlanIndicator pi = new PerfPlanIndicator();
            pi.setPlanId(entity.getId());
            pi.setIndicatorId(vo.getIndicatorId());
            pi.setWeight(vo.getWeight() == null ? BigDecimal.ZERO : vo.getWeight());
            pi.setSort(vo.getSort() == null ? idx : vo.getSort());
            planIndicatorMapper.insert(pi);
            idx++;
        }
        log.info("绩效方案保存: id={} code={} 指标数={} 权重合计={}", entity.getId(),
                entity.getPlanCode(), indicators.size(), weightTotal);
        return entity.getId();
    }

    @Override
    public void deletePlan(Long id) {
        Long used = cycleMapper.selectCount(new LambdaQueryWrapper<PerfCycle>().eq(PerfCycle::getPlanId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.PERF_PLAN_IN_USE);
        }
        planMapper.deleteById(id);
    }

    @Override
    public List<PerfPlanVO> planOptions() {
        return toPlanVOs(planMapper.selectList(new LambdaQueryWrapper<PerfPlan>()
                .eq(PerfPlan::getStatus, 2)
                .orderByDesc(PerfPlan::getId)), false);
    }

    /** 未配置等级时的兜底：A≥90 / B80~89 / C70~79 / D<70，系数 1.2/1.0/0.8/0.6 */
    private List<PerfGradeVO> defaultGrades() {
        List<PerfGradeVO> list = new ArrayList<>();
        list.add(grade("A", "优秀", "90", "120", "1.20", 1));
        list.add(grade("B", "良好", "80", "89.9", "1.00", 2));
        list.add(grade("C", "合格", "70", "79.9", "0.80", 3));
        list.add(grade("D", "待改进", "0", "69.9", "0.60", 4));
        return list;
    }

    private PerfGradeVO grade(String code, String name, String min, String max, String coefficient, int sort) {
        PerfGradeVO vo = new PerfGradeVO();
        vo.setGradeCode(code);
        vo.setGradeName(name);
        vo.setMinScore(new BigDecimal(min));
        vo.setMaxScore(new BigDecimal(max));
        vo.setCoefficient(new BigDecimal(coefficient));
        vo.setSort(sort);
        return vo;
    }

    // ==================================================================
    // 指标库
    // ==================================================================

    @Override
    public PageResult<PerfIndicatorVO> pageIndicators(String keyword, Integer type,
                                                      Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<PerfIndicator> wrapper = new LambdaQueryWrapper<PerfIndicator>()
                .orderByAsc(PerfIndicator::getIndicatorType)
                .orderByAsc(PerfIndicator::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PerfIndicator::getIndicatorName, keyword)
                    .or().like(PerfIndicator::getIndicatorCode, keyword));
        }
        if (type != null) {
            wrapper.eq(PerfIndicator::getIndicatorType, type);
        }
        Page<PerfIndicator> page = indicatorMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toIndicatorVO).toList(),
                page.getTotal(), (long) no, (long) size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveIndicator(PerfIndicatorSaveRequest request) {
        PerfIndicator duplicate = indicatorMapper.selectOne(new LambdaQueryWrapper<PerfIndicator>()
                .eq(PerfIndicator::getIndicatorCode, request.getIndicatorCode()).last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
            throw new BizException(ErrorCode.PERF_INDICATOR_DUPLICATE);
        }
        PerfIndicator entity = request.getId() == null ? new PerfIndicator()
                : indicatorMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "指标不存在");
        }
        entity.setIndicatorCode(request.getIndicatorCode());
        entity.setIndicatorName(request.getIndicatorName());
        entity.setDefinition(request.getDefinition());
        entity.setIndicatorType(request.getIndicatorType() == null ? 1 : request.getIndicatorType());
        entity.setUnit(request.getUnit());
        entity.setScoreStandard(request.getScoreStandard());
        entity.setSeries(request.getSeries());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            indicatorMapper.insert(entity);
        } else {
            indicatorMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void deleteIndicator(Long id) {
        // 被方案引用时逻辑删掉会让方案里出现「悬空指标」，直接拒绝更清楚
        Long used = planIndicatorMapper.selectCount(new LambdaQueryWrapper<PerfPlanIndicator>()
                .eq(PerfPlanIndicator::getIndicatorId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该指标已被方案引用，不能删除，可改为停用");
        }
        indicatorMapper.deleteById(id);
    }

    @Override
    public List<PerfIndicatorVO> indicatorOptions() {
        return indicatorMapper.selectList(new LambdaQueryWrapper<PerfIndicator>()
                        .eq(PerfIndicator::getStatus, 1)
                        .orderByAsc(PerfIndicator::getIndicatorType)
                        .orderByAsc(PerfIndicator::getId))
                .stream().map(this::toIndicatorVO).toList();
    }

    // ==================================================================
    // 组装
    // ==================================================================

    private List<PerfPlanVO> toPlanVOs(List<PerfPlan> plans, boolean withDetail) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Set<Long> planIds = plans.stream().map(PerfPlan::getId).collect(Collectors.toSet());
        Map<Long, List<PerfPlanGrade>> gradeMap = gradeMapper.selectList(
                        new LambdaQueryWrapper<PerfPlanGrade>()
                                .in(PerfPlanGrade::getPlanId, planIds)
                                .orderByAsc(PerfPlanGrade::getSort))
                .stream().collect(Collectors.groupingBy(PerfPlanGrade::getPlanId));

        Map<Long, List<PerfPlanIndicator>> linkMap = planIndicatorMapper.selectList(
                        new LambdaQueryWrapper<PerfPlanIndicator>()
                                .in(PerfPlanIndicator::getPlanId, planIds)
                                .orderByAsc(PerfPlanIndicator::getSort))
                .stream().collect(Collectors.groupingBy(PerfPlanIndicator::getPlanId));

        Set<Long> indicatorIds = linkMap.values().stream().flatMap(List::stream)
                .map(PerfPlanIndicator::getIndicatorId).collect(Collectors.toSet());
        Map<Long, PerfIndicator> indicatorMap = indicatorIds.isEmpty() ? new HashMap<>()
                : indicatorMapper.selectBatchIds(indicatorIds).stream()
                .collect(Collectors.toMap(PerfIndicator::getId, i -> i, (a, b) -> a));

        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        List<PerfPlanVO> vos = new ArrayList<>(plans.size());
        for (PerfPlan plan : plans) {
            PerfPlanVO vo = new PerfPlanVO();
            vo.setId(plan.getId());
            vo.setPlanCode(plan.getPlanCode());
            vo.setPlanName(plan.getPlanName());
            vo.setCycleType(plan.getCycleType());
            vo.setCycleTypeLabel(CYCLE_TYPE_LABEL.getOrDefault(plan.getCycleType(), "未知"));
            vo.setScoreMode(plan.getScoreMode());
            vo.setDeptIds(plan.getDeptIds());
            vo.setDeptNames(deptNamesOf(plan.getDeptIds(), deptNames));
            vo.setSeries(plan.getSeries());
            vo.setForceDistribution(plan.getForceDistribution());
            vo.setDistributionLimit(plan.getDistributionLimit());
            vo.setStatus(plan.getStatus());
            vo.setStatusLabel(PLAN_STATUS_LABEL.getOrDefault(plan.getStatus(), "未知"));
            vo.setRemark(plan.getRemark());
            vo.setCreateTime(plan.getCreateTime());

            List<PerfPlanIndicator> links = linkMap.getOrDefault(plan.getId(), List.of());
            BigDecimal total = links.stream().map(l -> l.getWeight() == null ? BigDecimal.ZERO : l.getWeight())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setWeightTotal(total);
            if (withDetail) {
                vo.setGrades(gradeMap.getOrDefault(plan.getId(), List.of()).stream().map(g -> {
                    PerfGradeVO gv = new PerfGradeVO();
                    gv.setGradeCode(g.getGradeCode());
                    gv.setGradeName(g.getGradeName());
                    gv.setMinScore(g.getMinScore());
                    gv.setMaxScore(g.getMaxScore());
                    gv.setCoefficient(g.getCoefficient());
                    gv.setSort(g.getSort());
                    return gv;
                }).toList());
                List<PerfPlanIndicatorVO> pivs = new ArrayList<>();
                for (PerfPlanIndicator link : links) {
                    PerfIndicator indicator = indicatorMap.get(link.getIndicatorId());
                    PerfPlanIndicatorVO pv = new PerfPlanIndicatorVO();
                    pv.setIndicatorId(link.getIndicatorId());
                    pv.setWeight(link.getWeight());
                    pv.setSort(link.getSort());
                    if (indicator != null) {
                        pv.setIndicatorCode(indicator.getIndicatorCode());
                        pv.setIndicatorName(indicator.getIndicatorName());
                        pv.setIndicatorType(indicator.getIndicatorType());
                        pv.setUnit(indicator.getUnit());
                    }
                    pivs.add(pv);
                }
                vo.setIndicators(pivs);
            }
            vos.add(vo);
        }
        return vos;
    }

    private String deptNamesOf(String deptIds, Map<Long, String> deptNames) {
        if (!StringUtils.hasText(deptIds)) {
            return "全部部门";
        }
        return Arrays.stream(deptIds.split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .map(id -> {
                    try {
                        return deptNames.getOrDefault(Long.parseLong(id), id);
                    } catch (NumberFormatException e) {
                        return id;
                    }
                })
                .collect(Collectors.joining("、"));
    }

    private PerfIndicatorVO toIndicatorVO(PerfIndicator entity) {
        PerfIndicatorVO vo = new PerfIndicatorVO();
        vo.setId(entity.getId());
        vo.setIndicatorCode(entity.getIndicatorCode());
        vo.setIndicatorName(entity.getIndicatorName());
        vo.setDefinition(entity.getDefinition());
        vo.setIndicatorType(entity.getIndicatorType());
        vo.setIndicatorTypeLabel(INDICATOR_TYPE_LABEL.getOrDefault(entity.getIndicatorType(), "未知"));
        vo.setUnit(entity.getUnit());
        vo.setScoreStandard(entity.getScoreStandard());
        vo.setSeries(entity.getSeries());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
