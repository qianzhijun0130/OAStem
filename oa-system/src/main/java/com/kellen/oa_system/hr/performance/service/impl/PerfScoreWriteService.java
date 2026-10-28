package com.kellen.oa_system.hr.performance.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.performance.dto.PerfCalibrateRequest;
import com.kellen.oa_system.hr.performance.dto.PerfScoreRequest;
import com.kellen.oa_system.hr.performance.dto.ScoreItem;
import com.kellen.oa_system.hr.performance.entity.PerfCalibrateLog;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfCalibrateLogMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.hr.performance.support.PerfConstants;
import com.kellen.oa_system.hr.performance.support.PerfScoreCalculator;
import com.kellen.oa_system.hr.performance.support.PerfSheetSupport;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 绩效评分链路：自评 → 上级评分 → 校准 → 确认（BE-OPT-08 从 {@code PerfScoreServiceImpl} 拆出）。
 *
 * <p>四道「不许跳过」的门槛都在这里，顺序不可调换：</p>
 * <ol>
 *   <li>目标未锁定不能自评；</li>
 *   <li>自评未提交（或标记未自评）不能上级评分；</li>
 *   <li>上级未评分不能校准——校准是「微调」不是「代评」；</li>
 *   <li>归档后一律 6512（由 {@code assertWritable} 拦）。</li>
 * </ol>
 *
 * <p>不含事务注解，事务由门面持有。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfScoreWriteService {

    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;
    private final PerfCalibrateLogMapper calibrateLogMapper;
    private final PerfSheetSupport sheetSupport;

    public void submitSelf(PerfScoreRequest request) {
        PerfSheet sheet = sheetSupport.requireSheet(request.getSheetId());
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        sheetSupport.assertOwner(sheet);
        sheetSupport.assertWritable(cycle);
        if (cycle.getStage() != PerfConstants.STAGE_SELF) {
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

    public void submitManager(PerfScoreRequest request) {
        PerfSheet sheet = sheetSupport.requireSheet(request.getSheetId());
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        boolean hr = HrRoles.isHr();
        if (!Objects.equals(sheet.getLeaderId(), currentUserId) && !hr) {
            throw new BizException(ErrorCode.PERF_NOT_LEADER);
        }
        sheetSupport.assertWritable(cycle);
        if (cycle.getStage() < PerfConstants.STAGE_MANAGER) {
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
        List<PerfPlanGrade> grades = sheetSupport.listGrades(sheet.getPlanId());
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
        List<PerfSheetItem> items = sheetSupport.listItems(sheetId);
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

    public void calibrate(PerfCalibrateRequest request) {
        PerfSheet sheet = sheetSupport.requireSheet(request.getSheetId());
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        sheetSupport.assertWritable(cycle);
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
        List<PerfPlanGrade> grades = sheetSupport.listGrades(sheet.getPlanId());
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

    public void confirm(Long sheetId) {
        PerfSheet sheet = sheetSupport.requireSheet(sheetId);
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        sheetSupport.assertOwner(sheet);
        sheetSupport.assertWritable(cycle);
        if (cycle.getStage() < PerfConstants.STAGE_CONFIRM) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "尚未进入结果确认阶段");
        }
        PerfSheet patch = new PerfSheet();
        patch.setId(sheetId);
        patch.setConfirmStatus(1);
        sheetMapper.updateById(patch);
        log.info("绩效结果确认: sheetId={} userId={}", sheetId, sheet.getUserId());
    }
}
