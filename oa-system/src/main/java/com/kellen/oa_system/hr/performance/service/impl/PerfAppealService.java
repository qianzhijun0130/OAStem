package com.kellen.oa_system.hr.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.performance.dto.PerfAppealHandleRequest;
import com.kellen.oa_system.hr.performance.dto.PerfAppealRequest;
import com.kellen.oa_system.hr.performance.entity.PerfAppeal;
import com.kellen.oa_system.hr.performance.entity.PerfCalibrateLog;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.mapper.PerfAppealMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfCalibrateLogMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.hr.performance.support.PerfConstants;
import com.kellen.oa_system.hr.performance.support.PerfScoreCalculator;
import com.kellen.oa_system.hr.performance.support.PerfSheetSupport;
import com.kellen.oa_system.hr.performance.support.PerfVoAssembler;
import com.kellen.oa_system.hr.performance.vo.PerfAppealVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 绩效申诉（BE-OPT-08 从 {@code PerfScoreServiceImpl} 拆出）。
 *
 * <p>三条规则：结果确认后才能申诉、每张表只能申诉一次、确认后 5 个工作日内。
 * 「调整」结论必须给分数并写校准留痕——申诉调整与 HR 校准走同一张日志表，
 * 这样「分数为什么变了」只有一个查询入口。</p>
 *
 * <p>不含事务注解，事务由门面持有。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfAppealService {

    private final PerfAppealMapper appealMapper;
    private final PerfSheetMapper sheetMapper;
    private final PerfCalibrateLogMapper calibrateLogMapper;
    private final PerfSheetSupport sheetSupport;
    private final PerfVoAssembler assembler;

    public void appeal(PerfAppealRequest request) {
        PerfSheet sheet = sheetSupport.requireSheet(request.getSheetId());
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        sheetSupport.assertOwner(sheet);
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
        if (LocalDateTime.now().isAfter(sheetSupport.addWorkDays(confirmedAt, PerfConstants.APPEAL_WORK_DAYS))) {
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
        PerfSheet sheet = sheetSupport.requireSheet(appeal.getSheetId());
        if (request.getStatus() == 3) {
            if (request.getScore() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "调整结论必须填写调整后分数");
            }
            List<PerfPlanGrade> grades = sheetSupport.listGrades(sheet.getPlanId());
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
                PerfConstants.APPEAL_STATUS_LABEL.getOrDefault(request.getStatus(), ""));
    }

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
                .stream().map(assembler::toAppealVO).toList();
    }
}
