package com.kellen.oa_system.hr.performance.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.performance.dto.GoalItem;
import com.kellen.oa_system.hr.performance.dto.PerfTargetSaveRequest;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.hr.performance.support.PerfConstants;
import com.kellen.oa_system.hr.performance.support.PerfSheetSupport;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 绩效目标（BE-OPT-08 从 {@code PerfScoreServiceImpl} 拆出）。
 *
 * <p>目标环节只有两个动作：员工填报、上级/HR 审核。它是整条链路的入口——
 * 目标没锁定，自评就无从谈起（「先打分再补目标」等于事后合理化）。</p>
 *
 * <p>不含事务注解，事务由门面持有。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfTargetService {

    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;
    private final PerfSheetSupport sheetSupport;

    public void saveTarget(PerfTargetSaveRequest request) {
        PerfSheet sheet = sheetSupport.requireSheet(request.getSheetId());
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        sheetSupport.assertOwner(sheet);
        sheetSupport.assertWritable(cycle);
        if (cycle.getStage() != PerfConstants.STAGE_TARGET) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "当前不是目标设定阶段");
        }
        if (Objects.equals(sheet.getTargetStatus(), 2)) {
            throw new BizException(ErrorCode.PERF_CYCLE_STAGE_INVALID, "目标已锁定，如需变更请走目标调整");
        }
        List<PerfSheetItem> items = sheetSupport.listItems(sheet.getId());
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

    public void approveTarget(Long sheetId, Boolean pass, String opinion) {
        PerfSheet sheet = sheetSupport.requireSheet(sheetId);
        PerfCycle cycle = sheetSupport.requireCycle(sheet.getCycleId());
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        if (!Objects.equals(sheet.getLeaderId(), currentUserId) && !HrRoles.isHr()) {
            throw new BizException(ErrorCode.PERF_NOT_LEADER);
        }
        sheetSupport.assertWritable(cycle);
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
}
