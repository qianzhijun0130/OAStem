package com.kellen.oa_system.hr.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.performance.dto.PerfCycleSaveRequest;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfPlan;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfPlanIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfIndicatorMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanGradeMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanIndicatorMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.hr.performance.service.PerfCycleService;
import com.kellen.oa_system.hr.performance.support.PerfScoreCalculator;
import com.kellen.oa_system.hr.performance.vo.PerfCycleVO;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 绩效周期实现。
 *
 * <p><b>为什么周期一创建就生成考核表</b>：考核表是「谁要被打分」的名册，
 * 等到自评阶段再生成就会出现「有人漏了没通知到」。生成时把部门、直属上级、
 * 指标名与权重全部快照下来——员工中途调部门、指标事后改名，都不影响这一期怎么算的。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfCycleServiceImpl implements PerfCycleService {

    private static final Map<Integer, String> STAGE_LABEL = Map.of(
            1, "目标设定", 2, "自评中", 3, "上级评分", 4, "校准", 5, "结果确认", 6, "已归档");

    /** 阶段到 6 即归档 */
    private static final int STAGE_ARCHIVED = 6;

    private final PerfCycleMapper cycleMapper;
    private final PerfPlanMapper planMapper;
    private final PerfPlanGradeMapper gradeMapper;
    private final PerfPlanIndicatorMapper planIndicatorMapper;
    private final PerfIndicatorMapper indicatorMapper;
    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;
    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    public PageResult<PerfCycleVO> pageCycles(Integer status, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<PerfCycle> wrapper = new LambdaQueryWrapper<PerfCycle>()
                .orderByDesc(PerfCycle::getStartDate)
                .orderByDesc(PerfCycle::getId);
        if (status != null) {
            wrapper.eq(PerfCycle::getStatus, status);
        }
        Page<PerfCycle> page = cycleMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toCycleVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public PerfCycleVO detail(Long id) {
        PerfCycle cycle = cycleMapper.selectById(id);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        return toCycleVOs(List.of(cycle)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCycle(PerfCycleSaveRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null
                || request.getEndDate().isBefore(request.getStartDate())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期起止日期不合法");
        }
        PerfPlan plan = planMapper.selectById(request.getPlanId());
        if (plan == null) {
            throw new BizException(ErrorCode.PERF_PLAN_NOT_FOUND);
        }
        PerfCycle duplicate = cycleMapper.selectOne(new LambdaQueryWrapper<PerfCycle>()
                .eq(PerfCycle::getCycleCode, request.getCycleCode()).last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期编码已存在");
        }
        // 同方案内日期不能重叠：重叠会导致同一个人在同一天被两个周期考核
        LambdaQueryWrapper<PerfCycle> overlap = new LambdaQueryWrapper<PerfCycle>()
                .eq(PerfCycle::getPlanId, request.getPlanId())
                .le(PerfCycle::getStartDate, request.getEndDate())
                .ge(PerfCycle::getEndDate, request.getStartDate());
        if (request.getId() != null) {
            overlap.ne(PerfCycle::getId, request.getId());
        }
        if (cycleMapper.selectCount(overlap) > 0) {
            throw new BizException(ErrorCode.PERF_CYCLE_DATE_OVERLAP);
        }

        PerfCycle entity = request.getId() == null ? new PerfCycle() : cycleMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        entity.setCycleCode(request.getCycleCode());
        entity.setCycleName(request.getCycleName());
        entity.setPlanId(request.getPlanId());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setTargetDeadline(request.getTargetDeadline());
        entity.setSelfDeadline(request.getSelfDeadline());
        entity.setScoreDeadline(request.getScoreDeadline());
        entity.setCalibrateDeadline(request.getCalibrateDeadline());
        entity.setConfirmDeadline(request.getConfirmDeadline());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            entity.setStage(1);
            entity.setStatus(1);
            cycleMapper.insert(entity);
            generateSheets(entity.getId());
        } else {
            cycleMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateSheets(Long cycleId) {
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        List<PerfPlanIndicator> links = planIndicatorMapper.selectList(
                new LambdaQueryWrapper<PerfPlanIndicator>()
                        .eq(PerfPlanIndicator::getPlanId, cycle.getPlanId())
                        .orderByAsc(PerfPlanIndicator::getSort));
        if (links.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "方案未配置指标，无法生成考核表");
        }
        PerfPlan plan = planMapper.selectById(cycle.getPlanId());
        Set<Long> indicatorIds = links.stream().map(PerfPlanIndicator::getIndicatorId).collect(Collectors.toSet());
        Map<Long, PerfIndicator> indicators = indicatorMapper.selectBatchIds(indicatorIds).stream()
                .collect(Collectors.toMap(PerfIndicator::getId, i -> i, (a, b) -> a));

        List<User> users = eligibleUsers(plan, cycle.getStartDate());
        Set<Long> existing = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                        .eq(PerfSheet::getCycleId, cycleId)
                        .select(PerfSheet::getUserId))
                .stream().map(PerfSheet::getUserId).collect(Collectors.toSet());

        int created = 0;
        for (User user : users) {
            if (existing.contains(user.getId())) {
                continue;
            }
            PerfSheet sheet = new PerfSheet();
            sheet.setCycleId(cycleId);
            sheet.setUserId(user.getId());
            sheet.setDeptId(user.getDeptId());
            sheet.setLeaderId(resolveLeader(user));
            sheet.setPlanId(cycle.getPlanId());
            sheet.setTargetStatus(0);
            sheet.setSelfStatus(0);
            sheet.setManagerStatus(0);
            sheet.setCalibrateStatus(0);
            sheet.setConfirmStatus(0);
            sheet.setSelfMissing(0);
            sheet.setHrScored(0);
            sheetMapper.insert(sheet);

            int sort = 1;
            for (PerfPlanIndicator link : links) {
                PerfIndicator indicator = indicators.get(link.getIndicatorId());
                PerfSheetItem item = new PerfSheetItem();
                item.setSheetId(sheet.getId());
                item.setIndicatorId(link.getIndicatorId());
                item.setIndicatorCode(indicator == null ? "UNKNOWN" : indicator.getIndicatorCode());
                item.setIndicatorName(indicator == null ? "未知指标" : indicator.getIndicatorName());
                item.setIndicatorType(indicator == null ? 1 : indicator.getIndicatorType());
                item.setWeight(link.getWeight() == null ? BigDecimal.ZERO : link.getWeight());
                item.setSort(link.getSort() == null ? sort : link.getSort());
                sheetItemMapper.insert(item);
                sort++;
            }
            created++;
        }
        log.info("生成考核表: cycleId={} 新增={} 候选人={}", cycleId, created, users.size());
        return created;
    }

    /**
     * 解析考核表的上级（直属上级）。
     *
     * <p><b>为什么要兜底</b>：档案里「直属上级」是可选字段，实际数据经常为空
     * （新建员工时没填、或者老数据迁移没补）。若直接把 {@code direct_leader_id}
     * 快照成空，这一期绩效就会变成「没有任何人能评分」——员工交不了自评、
     * 主管在「团队考核表」里看不到自己的下属，整条链路死锁。</p>
     *
     * <p>兜底顺序：员工直属上级 → 本部门负责人 → 空。
     * 用部门负责人兜底而不是「随便找个 HR」，是因为部门负责人本来就是
     * 「这个部门的人归谁管」的答案。</p>
     */
    private Long resolveLeader(User user) {
        if (user.getDirectLeaderId() != null) {
            return user.getDirectLeaderId();
        }
        if (user.getDeptId() == null) {
            return null;
        }
        Department dept = departmentMapper.selectById(user.getDeptId());
        if (dept == null || dept.getLeaderId() == null) {
            return null;
        }
        // 自己就是部门负责人时不能当自己的上级，否则会出现「自己审自己」
        if (Objects.equals(dept.getLeaderId(), user.getId())) {
            return null;
        }
        return dept.getLeaderId();
    }

    /**
     * 参与考核的人：在职 + 在方案适用范围内 + 入职满 1 个月。
     *
     * <p>「入职不满 1 个月不参与」不是歧视新人，而是避免「入职 5 天被打 C」——
     * 那种结果对员工和主管都没有意义。入职日期缺失时按「满足」处理并记日志，
     * 因为数据缺失不该让员工被莫名排除在一期考核之外。</p>
     */
    private List<User> eligibleUsers(PerfPlan plan, LocalDate cycleStart) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4));
        if (plan != null && StringUtils.hasText(plan.getDeptIds())) {
            List<Long> deptIds = Arrays.stream(plan.getDeptIds().split(","))
                    .map(String::trim).filter(StringUtils::hasText)
                    .map(Long::parseLong).toList();
            if (!deptIds.isEmpty()) {
                wrapper.in(User::getDeptId, deptIds);
            }
        }
        List<User> users = userMapper.selectList(wrapper);

        // 序列过滤：方案限定「技术序列」时，只取岗位属于该序列的人
        if (plan != null && StringUtils.hasText(plan.getSeries())) {
            List<Long> positionIds = positionMapper.selectList(new LambdaQueryWrapper<Position>()
                            .eq(Position::getSeries, plan.getSeries()).select(Position::getId))
                    .stream().map(Position::getId).toList();
            if (!positionIds.isEmpty()) {
                users = users.stream()
                        .filter(u -> u.getPositionId() != null && positionIds.contains(u.getPositionId()))
                        .toList();
            }
        }

        LocalDate threshold = cycleStart.minusMonths(1);
        List<User> result = new ArrayList<>();
        for (User user : users) {
            if (user.getEntryDate() == null) {
                log.warn("员工入职日期为空，按满足考核条件处理: userId={} username={}",
                        user.getId(), user.getUsername());
                result.add(user);
            } else if (!user.getEntryDate().isAfter(threshold)) {
                result.add(user);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int advance(Long cycleId) {
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        if (Objects.equals(cycle.getStage(), STAGE_ARCHIVED)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "周期已归档，不能再推进");
        }
        int next = (cycle.getStage() == null ? 1 : cycle.getStage()) + 1;

        // 离开自评阶段时：没自评的人打「未自评」标记，进入上级评分后按 0 分口径处理
        if (next == 3) {
            sheetMapper.update(null, new LambdaUpdateWrapper<PerfSheet>()
                    .eq(PerfSheet::getCycleId, cycleId)
                    .eq(PerfSheet::getSelfStatus, 0)
                    .set(PerfSheet::getSelfMissing, 1));
        }
        cycle.setStage(next);
        if (next == STAGE_ARCHIVED) {
            cycle.setStatus(2);
        }
        cycleMapper.updateById(cycle);
        int affected = sheetMapper.selectCount(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)).intValue();
        if (next == STAGE_ARCHIVED) {
            archive(cycleId);
        }
        log.info("绩效周期推进: cycleId={} 阶段={}({}) 涉及考核表={}", cycleId, next,
                STAGE_LABEL.getOrDefault(next, ""), affected);
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archive(Long cycleId) {
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        List<PerfPlanGrade> grades = gradeMapper.selectList(new LambdaQueryWrapper<PerfPlanGrade>()
                .eq(PerfPlanGrade::getPlanId, cycle.getPlanId())
                .orderByAsc(PerfPlanGrade::getSort));
        if (grades.isEmpty()) {
            throw new BizException(ErrorCode.PERF_GRADE_MISSING);
        }
        List<PerfSheet> sheets = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId));
        int count = 0;
        for (PerfSheet sheet : sheets) {
            // 归档口径：有校准分用校准分，否则用上级评分；两者都没有说明这一期没评，等级留空
            BigDecimal finalScore = sheet.getTotalScore() != null ? sheet.getTotalScore()
                    : sheet.getManagerTotal();
            PerfSheet patch = new PerfSheet();
            patch.setId(sheet.getId());
            if (finalScore != null) {
                PerfPlanGrade grade = PerfScoreCalculator.gradeOf(finalScore, grades);
                patch.setTotalScore(PerfScoreCalculator.clamp(finalScore));
                patch.setGradeCode(grade == null ? null : grade.getGradeCode());
                patch.setCoefficient(grade == null ? BigDecimal.ONE : grade.getCoefficient());
            } else {
                patch.setCoefficient(BigDecimal.ONE);
                patch.setRemark("本期未完成评分，系数按 1.0 处理");
            }
            sheetMapper.updateById(patch);
            count++;
        }
        cycle.setStage(STAGE_ARCHIVED);
        cycle.setStatus(2);
        cycleMapper.updateById(cycle);
        log.info("绩效归档: cycleId={} 考核表={} 等级映射={}", cycleId, count,
                grades.stream().map(PerfPlanGrade::getGradeCode).toList());
        return count;
    }

    @Override
    public void deleteCycle(Long id) {
        Long sheets = sheetMapper.selectCount(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, id));
        if (sheets != null && sheets > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期下已有考核表，不能删除");
        }
        cycleMapper.deleteById(id);
    }

    // ==================================================================
    // 组装
    // ==================================================================

    private List<PerfCycleVO> toCycleVOs(List<PerfCycle> cycles) {
        if (cycles.isEmpty()) {
            return List.of();
        }
        Set<Long> planIds = cycles.stream().map(PerfCycle::getPlanId).collect(Collectors.toSet());
        Map<Long, String> planNames = planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(PerfPlan::getId, PerfPlan::getPlanName, (a, b) -> a));

        Set<Long> cycleIds = cycles.stream().map(PerfCycle::getId).collect(Collectors.toSet());
        Map<Long, List<PerfSheet>> sheetMap = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                        .in(PerfSheet::getCycleId, cycleIds))
                .stream().collect(Collectors.groupingBy(PerfSheet::getCycleId));

        List<PerfCycleVO> vos = new ArrayList<>(cycles.size());
        for (PerfCycle cycle : cycles) {
            PerfCycleVO vo = new PerfCycleVO();
            vo.setId(cycle.getId());
            vo.setCycleCode(cycle.getCycleCode());
            vo.setCycleName(cycle.getCycleName());
            vo.setPlanId(cycle.getPlanId());
            vo.setPlanName(planNames.get(cycle.getPlanId()));
            vo.setStartDate(cycle.getStartDate());
            vo.setEndDate(cycle.getEndDate());
            vo.setStage(cycle.getStage());
            vo.setStageLabel(STAGE_LABEL.getOrDefault(cycle.getStage(), "未知"));
            vo.setTargetDeadline(cycle.getTargetDeadline());
            vo.setSelfDeadline(cycle.getSelfDeadline());
            vo.setScoreDeadline(cycle.getScoreDeadline());
            vo.setCalibrateDeadline(cycle.getCalibrateDeadline());
            vo.setConfirmDeadline(cycle.getConfirmDeadline());
            vo.setStatus(cycle.getStatus());
            vo.setRemark(cycle.getRemark());
            List<PerfSheet> sheets = sheetMap.getOrDefault(cycle.getId(), List.of());
            vo.setSheetCount(sheets.size());
            vo.setTargetDone((int) sheets.stream().filter(s -> Objects.equals(s.getTargetStatus(), 2)).count());
            vo.setSelfDone((int) sheets.stream().filter(s -> Objects.equals(s.getSelfStatus(), 1)).count());
            vo.setManagerDone((int) sheets.stream().filter(s -> Objects.equals(s.getManagerStatus(), 1)).count());
            vo.setConfirmDone((int) sheets.stream().filter(s -> Objects.equals(s.getConfirmStatus(), 1)).count());
            vos.add(vo);
        }
        return vos;
    }
}
