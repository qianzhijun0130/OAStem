package com.kellen.oa_system.hr.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.performance.dto.GoalItem;
import com.kellen.oa_system.hr.performance.dto.PerfAppealHandleRequest;
import com.kellen.oa_system.hr.performance.dto.PerfAppealRequest;
import com.kellen.oa_system.hr.performance.dto.PerfCalibrateRequest;
import com.kellen.oa_system.hr.performance.dto.PerfScoreRequest;
import com.kellen.oa_system.hr.performance.dto.PerfSheetPageQuery;
import com.kellen.oa_system.hr.performance.dto.PerfTargetSaveRequest;
import com.kellen.oa_system.hr.performance.dto.ScoreItem;
import com.kellen.oa_system.hr.performance.entity.PerfAppeal;
import com.kellen.oa_system.hr.performance.entity.PerfCalibrateLog;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfPlan;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfAppealMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfCalibrateLogMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanGradeMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.hr.performance.service.PerfScoreService;
import com.kellen.oa_system.hr.performance.support.PerfScoreCalculator;
import com.kellen.oa_system.hr.performance.vo.PerfAppealVO;
import com.kellen.oa_system.hr.performance.vo.PerfDistributionVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetDetailVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetItemVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 绩效评分链路实现。
 *
 * <p>四道「不许跳过」的门槛：</p>
 * <ol>
 *   <li>目标未锁定不能自评（否则「先打分再补目标」会让目标变成事后合理化）；</li>
 *   <li>自评未提交（或标记未自评）不能上级评分；</li>
 *   <li>上级未评分不能校准——校准是「微调」不是「代评」；</li>
 *   <li>归档后所有写操作一律 6512。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfScoreServiceImpl implements PerfScoreService {

    private static final Map<Integer, String> STAGE_LABEL = Map.of(
            1, "目标设定", 2, "自评中", 3, "上级评分", 4, "校准", 5, "结果确认", 6, "已归档");

    private static final Map<Integer, String> TARGET_STATUS_LABEL = Map.of(
            0, "未提交", 1, "待审核", 2, "已锁定", 3, "已驳回");

    private static final Map<Integer, String> APPEAL_STATUS_LABEL = Map.of(
            1, "待处理", 2, "维持原结果", 3, "调整");

    private static final int STAGE_TARGET = 1;
    private static final int STAGE_SELF = 2;
    private static final int STAGE_MANAGER = 3;
    private static final int STAGE_CALIBRATE = 4;
    private static final int STAGE_CONFIRM = 5;
    private static final int STAGE_ARCHIVED = 6;

    /** 申诉窗口：结果确认后 5 个工作日 */
    private static final int APPEAL_WORK_DAYS = 5;

    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;
    private final PerfCycleMapper cycleMapper;
    private final PerfPlanGradeMapper gradeMapper;
    private final PerfPlanMapper planMapper;
    private final PerfCalibrateLogMapper calibrateLogMapper;
    private final PerfAppealMapper appealMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final DataScopeSupport dataScopeSupport;

    // ==================================================================
    // 我的
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public PerfSheetDetailVO mySheet(Long cycleId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        PerfSheet sheet = sheetMapper.selectOne(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)
                .eq(PerfSheet::getUserId, userId)
                .last("LIMIT 1"));
        if (sheet == null) {
            throw new BizException(ErrorCode.PERF_NO_SHEET);
        }
        return toDetail(sheet, true, true);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<PerfSheetVO> myHistory(Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Long userId = SecurityContextUtil.getCurrentUserId();
        Page<PerfSheet> page = sheetMapper.selectPage(new Page<>(no, size),
                new LambdaQueryWrapper<PerfSheet>()
                        .eq(PerfSheet::getUserId, userId)
                        .orderByDesc(PerfSheet::getCycleId));
        return PageResult.of(toSheetVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    // ==================================================================
    // 目标
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTarget(PerfTargetSaveRequest request) {
        PerfSheet sheet = requireSheet(request.getSheetId());
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        assertOwner(sheet);
        assertWritable(cycle);
        if (cycle.getStage() != STAGE_TARGET) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "当前不是目标设定阶段");
        }
        if (Objects.equals(sheet.getTargetStatus(), 2)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "目标已锁定，如需变更请走目标调整");
        }
        List<PerfSheetItem> items = listItems(sheet.getId());
        Map<Long, GoalItem> goals = (request.getItems() == null ? List.<GoalItem>of() : request.getItems())
                .stream().filter(g -> g.getItemId() != null)
                .collect(Collectors.toMap(GoalItem::getItemId, g -> g, (a, b) -> a));
        boolean anyTarget = false;
        for (PerfSheetItem item : items) {
            GoalItem goal = goals.get(item.getId());
            if (goal == null) {
                continue;
            }
            if (StringUtils.hasText(goal.getTargetValue())) {
                anyTarget = true;
            }
            item.setTargetValue(goal.getTargetValue());
            item.setMeasureStandard(goal.getMeasureStandard());
            sheetItemMapper.updateById(item);
        }
        if (!anyTarget) {
            throw new BizException(ErrorCode.PERF_TARGET_REQUIRED);
        }
        PerfSheet patch = new PerfSheet();
        patch.setId(sheet.getId());
        patch.setTargetStatus(1);
        sheetMapper.updateById(patch);
        log.info("绩效目标提交: sheetId={} userId={}", sheet.getId(), sheet.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTarget(Long sheetId, Boolean pass, String opinion) {
        PerfSheet sheet = requireSheet(sheetId);
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        if (!Objects.equals(sheet.getLeaderId(), currentUserId) && !HrRoles.isHr()) {
            throw new BizException(ErrorCode.PERF_NOT_LEADER);
        }
        assertWritable(cycle);
        if (!Objects.equals(sheet.getTargetStatus(), 1)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "该考核表没有待审核的目标");
        }
        PerfSheet patch = new PerfSheet();
        patch.setId(sheetId);
        patch.setTargetStatus(Boolean.TRUE.equals(pass) ? 2 : 3);
        patch.setRemark(StringUtils.hasText(opinion) ? opinion : sheet.getRemark());
        sheetMapper.updateById(patch);
        log.info("绩效目标审核: sheetId={} 结果={} 意见={}", sheetId, Boolean.TRUE.equals(pass) ? "通过" : "驳回", opinion);
    }

    // ==================================================================
    // 评分
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSelf(PerfScoreRequest request) {
        PerfSheet sheet = requireSheet(request.getSheetId());
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        assertOwner(sheet);
        assertWritable(cycle);
        if (cycle.getStage() != STAGE_SELF) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "当前不是自评阶段");
        }
        if (!Objects.equals(sheet.getTargetStatus(), 2)) {
            throw new BizException(ErrorCode.PERF_TARGET_NOT_LOCKED);
        }
        List<PerfSheetItem> items = applyScores(sheet.getId(), request.getItems(), true);
        PerfSheet patch = new PerfSheet();
        patch.setId(sheet.getId());
        patch.setSelfTotal(PerfScoreCalculator.total(items, true));
        patch.setSelfStatus(1);
        sheetMapper.updateById(patch);
        log.info("绩效自评提交: sheetId={} 自评总分={}", sheet.getId(), patch.getSelfTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitManager(PerfScoreRequest request) {
        PerfSheet sheet = requireSheet(request.getSheetId());
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        boolean hr = HrRoles.isHr();
        if (!Objects.equals(sheet.getLeaderId(), currentUserId) && !hr) {
            throw new BizException(ErrorCode.PERF_NOT_LEADER);
        }
        assertWritable(cycle);
        if (cycle.getStage() < STAGE_MANAGER) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "尚未进入上级评分阶段");
        }
        if (!Objects.equals(sheet.getSelfStatus(), 1) && !Objects.equals(sheet.getSelfMissing(), 1)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "员工尚未自评，暂不能评分");
        }
        if (!StringUtils.hasText(request.getComment())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上级评分必须填写总评语");
        }
        List<PerfSheetItem> items = applyScores(sheet.getId(), request.getItems(), false);
        BigDecimal total = PerfScoreCalculator.total(items, false);
        List<PerfPlanGrade> grades = listGrades(sheet.getPlanId());
        PerfPlanGrade grade = PerfScoreCalculator.gradeOf(total, grades);

        PerfSheet patch = new PerfSheet();
        patch.setId(sheet.getId());
        patch.setManagerTotal(total);
        // 最终分先按上级评分落，HR 校准可以覆盖
        patch.setTotalScore(total);
        patch.setGradeCode(grade == null ? null : grade.getGradeCode());
        patch.setCoefficient(grade == null ? BigDecimal.ONE : grade.getCoefficient());
        patch.setManagerStatus(1);
        patch.setManagerComment(request.getComment());
        // 非直属上级来打分（HR 代评）要留痕：否则事后看不出"这分不是主管打的"
        if (!Objects.equals(sheet.getLeaderId(), currentUserId)) {
            patch.setHrScored(1);
        }
        sheetMapper.updateById(patch);
        log.info("绩效上级评分: sheetId={} 总分={} 等级={} 代评={}", sheet.getId(), total,
                patch.getGradeCode(), patch.getHrScored());
    }

    /**
     * 逐项写分。
     *
     * <p>校验分两类：普通指标 0~100；加分项合计 ≤10、扣分项合计 ≤20（`04` 3.1）。
     * 越界直接拒绝而不是截断——截断会让员工看到的分和实际算的分不一致。</p>
     */
    private List<PerfSheetItem> applyScores(Long sheetId, List<ScoreItem> scoreItems, boolean self) {
        List<PerfSheetItem> items = listItems(sheetId);
        Map<Long, ScoreItem> scores = (scoreItems == null ? List.<ScoreItem>of() : scoreItems)
                .stream().filter(s -> s.getItemId() != null)
                .collect(Collectors.toMap(ScoreItem::getItemId, s -> s, (a, b) -> a));
        BigDecimal bonus = BigDecimal.ZERO;
        BigDecimal penalty = BigDecimal.ZERO;
        for (PerfSheetItem item : items) {
            ScoreItem score = scores.get(item.getId());
            if (score == null || score.getScore() == null) {
                continue;
            }
            BigDecimal value = score.getScore();
            int type = item.getIndicatorType() == null ? 1 : item.getIndicatorType();
            if (type == 3) {
                bonus = bonus.add(value);
            } else if (type == 4) {
                penalty = penalty.add(value);
            }
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                throw new BizException(ErrorCode.PERF_SCORE_OUT_OF_RANGE,
                        "「" + item.getIndicatorName() + "」分数须在 0~100 之间");
            }
            if (self) {
                item.setSelfScore(value);
                item.setSelfComment(score.getComment());
            } else {
                item.setManagerScore(value);
                item.setManagerComment(score.getComment());
                // 未校准前，最终分跟随上级评分，保证「看到的分数」与「算出来的分数」一致
                item.setFinalScore(value);
            }
            sheetItemMapper.updateById(item);
        }
        if (bonus.compareTo(PerfScoreCalculator.MAX_BONUS) > 0) {
            throw new BizException(ErrorCode.PERF_SCORE_OUT_OF_RANGE,
                    "加分项合计上限 " + PerfScoreCalculator.MAX_BONUS.toPlainString() + " 分，当前 "
                            + bonus.stripTrailingZeros().toPlainString() + " 分");
        }
        if (penalty.compareTo(PerfScoreCalculator.MAX_PENALTY) > 0) {
            throw new BizException(ErrorCode.PERF_SCORE_OUT_OF_RANGE,
                    "扣分项合计上限 " + PerfScoreCalculator.MAX_PENALTY.toPlainString() + " 分，当前 "
                            + penalty.stripTrailingZeros().toPlainString() + " 分");
        }
        return items;
    }

    // ==================================================================
    // 校准 / 确认
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calibrate(PerfCalibrateRequest request) {
        PerfSheet sheet = requireSheet(request.getSheetId());
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        assertWritable(cycle);
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ErrorCode.PERF_CALIBRATE_REASON_REQUIRED);
        }
        if (!Objects.equals(sheet.getManagerStatus(), 1)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "上级尚未评分，不能校准");
        }
        BigDecimal beforeScore = sheet.getTotalScore();
        String beforeGrade = sheet.getGradeCode();
        BigDecimal afterScore = request.getScore() == null ? beforeScore : request.getScore();
        if (afterScore == null) {
            afterScore = sheet.getManagerTotal();
        }
        List<PerfPlanGrade> grades = listGrades(sheet.getPlanId());
        String afterGrade = StringUtils.hasText(request.getGradeCode())
                ? request.getGradeCode()
                : (PerfScoreCalculator.gradeOf(afterScore, grades) == null ? beforeGrade
                : PerfScoreCalculator.gradeOf(afterScore, grades).getGradeCode());
        BigDecimal coefficient = PerfScoreCalculator.coefficientOf(afterGrade, grades);

        PerfSheet patch = new PerfSheet();
        patch.setId(sheet.getId());
        patch.setTotalScore(PerfScoreCalculator.clamp(afterScore));
        patch.setGradeCode(afterGrade);
        patch.setCoefficient(coefficient);
        patch.setCalibrateStatus(1);
        sheetMapper.updateById(patch);

        PerfCalibrateLog entry = new PerfCalibrateLog();
        entry.setSheetId(sheet.getId());
        entry.setBeforeScore(beforeScore);
        entry.setAfterScore(patch.getTotalScore());
        entry.setBeforeGrade(beforeGrade);
        entry.setAfterGrade(afterGrade);
        entry.setReason(request.getReason());
        entry.setOperatorId(SecurityContextUtil.getCurrentUserId());
        entry.setCreateTime(LocalDateTime.now());
        calibrateLogMapper.insert(entry);
        log.info("绩效校准: sheetId={} {}→{} {}→{} 理由={}", sheet.getId(), beforeScore,
                patch.getTotalScore(), beforeGrade, afterGrade, request.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long sheetId) {
        PerfSheet sheet = requireSheet(sheetId);
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        assertOwner(sheet);
        assertWritable(cycle);
        if (cycle.getStage() < STAGE_CONFIRM) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "尚未进入结果确认阶段");
        }
        PerfSheet patch = new PerfSheet();
        patch.setId(sheetId);
        patch.setConfirmStatus(1);
        sheetMapper.updateById(patch);
        log.info("绩效结果确认: sheetId={} userId={}", sheetId, sheet.getUserId());
    }

    // ==================================================================
    // 申诉
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appeal(PerfAppealRequest request) {
        PerfSheet sheet = requireSheet(request.getSheetId());
        PerfCycle cycle = requireCycle(sheet.getCycleId());
        assertOwner(sheet);
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请填写申诉理由");
        }
        if (!Objects.equals(sheet.getConfirmStatus(), 1)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "请先确认结果再申诉");
        }
        Long exists = appealMapper.selectCount(new LambdaQueryWrapper<PerfAppeal>()
                .eq(PerfAppeal::getSheetId, sheet.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PERF_APPEAL_DUPLICATE);
        }
        LocalDateTime confirmedAt = sheet.getUpdateTime() == null ? LocalDateTime.now() : sheet.getUpdateTime();
        if (LocalDateTime.now().isAfter(addWorkDays(confirmedAt, APPEAL_WORK_DAYS))) {
            throw new BizException(ErrorCode.PERF_APPEAL_WINDOW_CLOSED);
        }
        PerfAppeal appeal = new PerfAppeal();
        appeal.setSheetId(sheet.getId());
        appeal.setUserId(sheet.getUserId());
        appeal.setReason(request.getReason());
        appeal.setStatus(1);
        appeal.setBeforeScore(sheet.getTotalScore());
        appeal.setBeforeGrade(sheet.getGradeCode());
        appealMapper.insert(appeal);
        log.info("绩效申诉提交: sheetId={} cycle={}", sheet.getId(), cycle.getCycleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAppeal(PerfAppealHandleRequest request) {
        PerfAppeal appeal = appealMapper.selectById(request.getAppealId());
        if (appeal == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "申诉不存在");
        }
        if (!Objects.equals(appeal.getStatus(), 1)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该申诉已处理");
        }
        if (request.getStatus() == null || (request.getStatus() != 2 && request.getStatus() != 3)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "处理结论只能是「维持原结果」或「调整」");
        }
        PerfSheet sheet = requireSheet(appeal.getSheetId());
        if (request.getStatus() == 3) {
            if (request.getScore() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "调整结论必须填写调整后分数");
            }
            List<PerfPlanGrade> grades = listGrades(sheet.getPlanId());
            PerfPlanGrade grade = PerfScoreCalculator.gradeOf(request.getScore(), grades);
            PerfSheet patch = new PerfSheet();
            patch.setId(sheet.getId());
            patch.setTotalScore(PerfScoreCalculator.clamp(request.getScore()));
            patch.setGradeCode(grade == null ? sheet.getGradeCode() : grade.getGradeCode());
            patch.setCoefficient(PerfScoreCalculator.coefficientOf(patch.getGradeCode(), grades));
            sheetMapper.updateById(patch);

            PerfCalibrateLog entry = new PerfCalibrateLog();
            entry.setSheetId(sheet.getId());
            entry.setBeforeScore(sheet.getTotalScore());
            entry.setAfterScore(patch.getTotalScore());
            entry.setBeforeGrade(sheet.getGradeCode());
            entry.setAfterGrade(patch.getGradeCode());
            entry.setReason("申诉调整：" + (StringUtils.hasText(request.getOpinion()) ? request.getOpinion() : "无意见"));
            entry.setOperatorId(SecurityContextUtil.getCurrentUserId());
            entry.setCreateTime(LocalDateTime.now());
            calibrateLogMapper.insert(entry);

            appeal.setAfterScore(patch.getTotalScore());
            appeal.setAfterGrade(patch.getGradeCode());
        }
        appeal.setStatus(request.getStatus());
        appeal.setHandleBy(SecurityContextUtil.getCurrentUserId());
        appeal.setHandleTime(LocalDateTime.now());
        appeal.setHandleOpinion(request.getOpinion());
        appealMapper.updateById(appeal);
        log.info("绩效申诉处理: appealId={} 结论={}", appeal.getId(),
                APPEAL_STATUS_LABEL.getOrDefault(request.getStatus(), ""));
    }

    @Transactional(readOnly = true)
    @Override
    public List<PerfAppealVO> pendingAppeals(Long cycleId) {
        List<PerfSheet> sheets = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .eq(cycleId != null, PerfSheet::getCycleId, cycleId));
        Set<Long> sheetIds = sheets.stream().map(PerfSheet::getId).collect(Collectors.toSet());
        if (sheetIds.isEmpty()) {
            return List.of();
        }
        return appealMapper.selectList(new LambdaQueryWrapper<PerfAppeal>()
                        .in(PerfAppeal::getSheetId, sheetIds)
                        .orderByAsc(PerfAppeal::getStatus)
                        .orderByDesc(PerfAppeal::getId))
                .stream().map(this::toAppealVO).toList();
    }

    // ==================================================================
    // 结果视图
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public PageResult<PerfSheetVO> pageSheets(PerfSheetPageQuery query) {
        return pageByWrapper(query, false);
    }

    @Transactional(readOnly = true)
    @Override
    @DataScope
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
            List<Long> matched = matchedUserIds(query.getKeyword());
            if (matched.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(PerfSheet::getUserId, matched);
        }
        if (teamOnly) {
            // 「只能看直属下属」：不按部门放宽，避免主管看到隔壁组
            wrapper.eq(PerfSheet::getLeaderId, currentUserId);
        } else if (!HrRoles.isHr()) {
            Set<Long> visible = visibleUserIds();
            if (visible.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(PerfSheet::getUserId, visible);
        }
        wrapper.orderByDesc(PerfSheet::getCycleId).orderByAsc(PerfSheet::getUserId);
        Page<PerfSheet> page = sheetMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toSheetVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Transactional(readOnly = true)
    @Override
    public PerfSheetDetailVO detail(Long sheetId) {
        PerfSheet sheet = requireSheet(sheetId);
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
        return toDetail(sheet, true, showSelfScore);
    }

    @Transactional(readOnly = true)
    @Override
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

    @Transactional(readOnly = true)
    @Override
    public void export(Long cycleId, HttpServletResponse response) {
        PerfCycle cycle = requireCycle(cycleId);
        List<PerfSheetVO> rows = toSheetVOs(sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)
                .orderByAsc(PerfSheet::getDeptId)
                .orderByAsc(PerfSheet::getUserId)));
        List<String> headers = List.of("工号", "姓名", "部门", "周期", "最终总分", "等级", "系数",
                "目标", "自评", "上级评分", "校准", "确认", "备注");
        List<List<Object>> data = new ArrayList<>();
        for (PerfSheetVO vo : rows) {
            data.add(List.of(
                    blank(vo.getWorkNumber()), blank(vo.getRealName()), blank(vo.getDeptName()),
                    blank(vo.getCycleName()),
                    vo.getTotalScore() == null ? "" : vo.getTotalScore(),
                    blank(vo.getGradeCode()),
                    vo.getCoefficient() == null ? "" : vo.getCoefficient(),
                    blank(vo.getTargetStatusLabel()),
                    Objects.equals(vo.getSelfStatus(), 1) ? "已自评"
                            : (Objects.equals(vo.getSelfMissing(), 1) ? "未自评" : "未提交"),
                    Objects.equals(vo.getManagerStatus(), 1)
                            ? (Objects.equals(vo.getHrScored(), 1) ? "HR 代评" : "已评分") : "未评分",
                    Objects.equals(vo.getCalibrateStatus(), 1) ? "已校准" : "未校准",
                    Objects.equals(vo.getConfirmStatus(), 1) ? "已确认" : "未确认",
                    blank(vo.getRemark())));
        }
        ExcelExportUtil.write(response, "绩效结果-" + cycle.getCycleCode() + ".xlsx", "绩效结果", headers, data);
    }

    @Override
    public BigDecimal coefficientOf(Long userId, String month) {
        // 统一走批量实现：单人也走同一条 SQL 路径，避免两份口径不一致
        if (userId == null || !StringUtils.hasText(month)) {
            return null;
        }
        return coefficientMap(java.util.List.of(userId), month).get(userId);
    }

    @Override
    public java.util.Map<Long, BigDecimal> coefficientMap(java.util.Collection<Long> userIds, String month) {
        if (userIds == null || userIds.isEmpty() || !StringUtils.hasText(month)) {
            return java.util.Map.of();
        }
        List<PerfCycle> cycles = archivedCycles(month);
        if (cycles.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Set<Long> cycleIds = cycles.stream().map(PerfCycle::getId).collect(Collectors.toSet());
        List<PerfSheet> sheets = sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .in(PerfSheet::getUserId, userIds)
                .in(PerfSheet::getCycleId, cycleIds)
                .isNotNull(PerfSheet::getCoefficient)
                .orderByDesc(PerfSheet::getCycleId));
        java.util.Map<Long, BigDecimal> result = new java.util.HashMap<>();
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

    // ==================================================================
    // 内部工具
    // ==================================================================

    private PerfSheet requireSheet(Long sheetId) {
        if (sheetId == null) {
            throw new BizException(ErrorCode.PERF_SHEET_NOT_FOUND);
        }
        PerfSheet sheet = sheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new BizException(ErrorCode.PERF_SHEET_NOT_FOUND);
        }
        return sheet;
    }

    private PerfCycle requireCycle(Long cycleId) {
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        return cycle;
    }

    private void assertOwner(PerfSheet sheet) {
        if (!Objects.equals(sheet.getUserId(), SecurityContextUtil.getCurrentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的考核表");
        }
    }

    private void assertWritable(PerfCycle cycle) {
        if (Objects.equals(cycle.getStatus(), 2) || Objects.equals(cycle.getStage(), STAGE_ARCHIVED)) {
            throw new BizException(ErrorCode.PERF_ARCHIVED_READONLY);
        }
    }

    private List<PerfSheetItem> listItems(Long sheetId) {
        return sheetItemMapper.selectList(new LambdaQueryWrapper<PerfSheetItem>()
                .eq(PerfSheetItem::getSheetId, sheetId)
                .orderByAsc(PerfSheetItem::getSort)
                .orderByAsc(PerfSheetItem::getId));
    }

    private List<PerfPlanGrade> listGrades(Long planId) {
        return gradeMapper.selectList(new LambdaQueryWrapper<PerfPlanGrade>()
                .eq(PerfPlanGrade::getPlanId, planId)
                .orderByAsc(PerfPlanGrade::getSort));
    }

    private List<Long> matchedUserIds(String keyword) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .and(w -> w.like(User::getRealName, keyword).or().like(User::getWorkNumber, keyword))
                        .select(User::getId))
                .stream().map(User::getId).toList();
    }

    private Set<Long> visibleUserIds() {
        // 快照为空 = 调用方漏标 @DataScope。此处直接失败而不是返回"全部用户"：
        // 后者会让 wrapper.in(userId, 全部用户) 等价于不过滤，形成静默越权（BE-OPT-02 根因）。
        if (dataScopeSupport.currentSnapshot() == null) {
            throw new IllegalStateException(
                    "调用方未标 @DataScope，数据权限无法判定：" + getClass().getSimpleName() + "#visibleUserIds");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        hrDataScopeSupport.applyToUser(wrapper);
        return userMapper.selectList(wrapper.select(User::getId)).stream()
                .map(User::getId).collect(Collectors.toSet());
    }

    /** 加 N 个工作日（跳过周六周日）；节假日表未接，仅按周末计算 */
    private LocalDateTime addWorkDays(LocalDateTime from, int days) {
        LocalDateTime result = from;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            DayOfWeek dow = result.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    // ==================================================================
    // 组装
    // ==================================================================

    private List<PerfSheetVO> toSheetVOs(List<PerfSheet> sheets) {
        if (sheets.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = sheets.stream().map(PerfSheet::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Set<Long> leaderIds = sheets.stream().map(PerfSheet::getLeaderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.requireNonNullElse(u.getRealName(), ""),
                        (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Map<Long, String> positionNames = positionMapper.selectList(new LambdaQueryWrapper<Position>()
                        .select(Position::getId, Position::getPositionName))
                .stream().collect(Collectors.toMap(Position::getId, Position::getPositionName, (a, b) -> a));
        Map<Long, PerfCycle> cycles = cycleMapper.selectBatchIds(
                        sheets.stream().map(PerfSheet::getCycleId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PerfCycle::getId, c -> c, (a, b) -> a));
        Map<Long, Integer> appeals = appealStatusMap(sheets);

        List<PerfSheetVO> vos = new ArrayList<>(sheets.size());
        for (PerfSheet sheet : sheets) {
            PerfSheetVO vo = new PerfSheetVO();
            vo.setId(sheet.getId());
            vo.setCycleId(sheet.getCycleId());
            PerfCycle cycle = cycles.get(sheet.getCycleId());
            if (cycle != null) {
                vo.setCycleCode(cycle.getCycleCode());
                vo.setCycleName(cycle.getCycleName());
            }
            vo.setUserId(sheet.getUserId());
            User user = users.get(sheet.getUserId());
            if (user != null) {
                vo.setRealName(user.getRealName());
                vo.setWorkNumber(user.getWorkNumber());
                vo.setPositionName(user.getPositionId() == null ? null
                        : positionNames.get(user.getPositionId()));
            }
            vo.setDeptName(deptNames.get(sheet.getDeptId()));
            vo.setLeaderName(sheet.getLeaderId() == null ? null : leaderNames.get(sheet.getLeaderId()));
            vo.setTargetStatus(sheet.getTargetStatus());
            vo.setTargetStatusLabel(TARGET_STATUS_LABEL.getOrDefault(sheet.getTargetStatus(), "未知"));
            vo.setSelfStatus(sheet.getSelfStatus());
            vo.setManagerStatus(sheet.getManagerStatus());
            vo.setCalibrateStatus(sheet.getCalibrateStatus());
            vo.setConfirmStatus(sheet.getConfirmStatus());
            vo.setTotalScore(sheet.getTotalScore());
            vo.setGradeCode(sheet.getGradeCode());
            vo.setCoefficient(sheet.getCoefficient());
            vo.setSelfMissing(sheet.getSelfMissing());
            vo.setHrScored(sheet.getHrScored());
            vo.setAppealStatus(appeals.get(sheet.getId()));
            vo.setRemark(sheet.getRemark());
            vos.add(vo);
        }
        return vos;
    }

    private Map<Long, Integer> appealStatusMap(List<PerfSheet> sheets) {
        Set<Long> ids = sheets.stream().map(PerfSheet::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return appealMapper.selectList(new LambdaQueryWrapper<PerfAppeal>()
                        .in(PerfAppeal::getSheetId, ids))
                .stream().collect(Collectors.toMap(PerfAppeal::getSheetId, PerfAppeal::getStatus, (a, b) -> a));
    }

    /**
     * @param withDetail  是否带指标行与申诉
     * @param showSelfScore 是否显示自评分（上级评分阶段默认隐藏，避免锚定效应）
     */
    private PerfSheetDetailVO toDetail(PerfSheet sheet, boolean withDetail, boolean showSelfScore) {
        PerfSheetDetailVO vo = new PerfSheetDetailVO();
        vo.setId(sheet.getId());
        vo.setCycleId(sheet.getCycleId());
        PerfCycle cycle = cycleMapper.selectById(sheet.getCycleId());
        if (cycle != null) {
            vo.setCycleCode(cycle.getCycleCode());
            vo.setCycleName(cycle.getCycleName());
            vo.setStage(cycle.getStage());
            vo.setStageLabel(STAGE_LABEL.getOrDefault(cycle.getStage(), "未知"));
        }
        vo.setUserId(sheet.getUserId());
        User user = userMapper.selectById(sheet.getUserId());
        if (user != null) {
            vo.setRealName(user.getRealName());
            vo.setWorkNumber(user.getWorkNumber());
            Position position = user.getPositionId() == null ? null : positionMapper.selectById(user.getPositionId());
            vo.setPositionName(position == null ? null : position.getPositionName());
            Department dept = user.getDeptId() == null ? null : departmentMapper.selectById(user.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getDeptName());
        }
        if (sheet.getLeaderId() != null) {
            User leader = userMapper.selectById(sheet.getLeaderId());
            vo.setLeaderName(leader == null ? null : leader.getRealName());
        }
        vo.setTargetStatus(sheet.getTargetStatus());
        vo.setSelfStatus(sheet.getSelfStatus());
        vo.setManagerStatus(sheet.getManagerStatus());
        vo.setCalibrateStatus(sheet.getCalibrateStatus());
        vo.setConfirmStatus(sheet.getConfirmStatus());
        vo.setManagerTotal(sheet.getManagerTotal());
        vo.setTotalScore(sheet.getTotalScore());
        vo.setGradeCode(sheet.getGradeCode());
        vo.setCoefficient(sheet.getCoefficient());
        vo.setManagerComment(sheet.getManagerComment());
        vo.setSelfMissing(sheet.getSelfMissing());
        vo.setHrScored(sheet.getHrScored());
        vo.setRemark(sheet.getRemark());
        if (showSelfScore) {
            vo.setSelfTotal(sheet.getSelfTotal());
        }
        if (StringUtils.hasText(sheet.getGradeCode())) {
            listGrades(sheet.getPlanId()).stream()
                    .filter(g -> Objects.equals(g.getGradeCode(), sheet.getGradeCode()))
                    .findFirst()
                    .ifPresent(g -> vo.setGradeName(g.getGradeName()));
        }
        if (withDetail) {
            List<PerfSheetItemVO> items = new ArrayList<>();
            for (PerfSheetItem item : listItems(sheet.getId())) {
                PerfSheetItemVO iv = new PerfSheetItemVO();
                iv.setId(item.getId());
                iv.setIndicatorId(item.getIndicatorId());
                iv.setIndicatorCode(item.getIndicatorCode());
                iv.setIndicatorName(item.getIndicatorName());
                iv.setIndicatorType(item.getIndicatorType());
                iv.setWeight(item.getWeight());
                iv.setTargetValue(item.getTargetValue());
                iv.setMeasureStandard(item.getMeasureStandard());
                iv.setManagerScore(item.getManagerScore());
                iv.setManagerComment(item.getManagerComment());
                iv.setFinalScore(item.getFinalScore());
                iv.setWeightedScore(PerfScoreCalculator.weightedScore(item.getManagerScore(), item.getWeight()));
                if (showSelfScore) {
                    iv.setSelfScore(item.getSelfScore());
                    iv.setSelfComment(item.getSelfComment());
                }
                items.add(iv);
            }
            vo.setItems(items);
            PerfAppeal appeal = appealMapper.selectOne(new LambdaQueryWrapper<PerfAppeal>()
                    .eq(PerfAppeal::getSheetId, sheet.getId()).last("LIMIT 1"));
            if (appeal != null) {
                vo.setAppeal(toAppealVO(appeal));
            }
        }
        return vo;
    }

    private PerfAppealVO toAppealVO(PerfAppeal appeal) {
        PerfAppealVO vo = new PerfAppealVO();
        vo.setId(appeal.getId());
        vo.setSheetId(appeal.getSheetId());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setStatusLabel(APPEAL_STATUS_LABEL.getOrDefault(appeal.getStatus(), "未知"));
        vo.setHandleOpinion(appeal.getHandleOpinion());
        vo.setHandleTime(appeal.getHandleTime());
        vo.setBeforeScore(appeal.getBeforeScore());
        vo.setAfterScore(appeal.getAfterScore());
        vo.setBeforeGrade(appeal.getBeforeGrade());
        vo.setAfterGrade(appeal.getAfterGrade());
        vo.setCreateTime(appeal.getCreateTime());
        User user = userMapper.selectById(appeal.getUserId());
        vo.setRealName(user == null ? null : user.getRealName());
        if (appeal.getHandleBy() != null) {
            User handler = userMapper.selectById(appeal.getHandleBy());
            vo.setHandlerName(handler == null ? null : handler.getRealName());
        }
        return vo;
    }
}
