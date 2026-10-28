package com.kellen.oa_system.hr.performance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.performance.entity.PerfAppeal;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfAppealMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.vo.PerfAppealVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetDetailVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetItemVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetVO;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 绩效 VO 组装（BE-OPT-08 拆分时抽出）。
 *
 * <p>列表、详情、申诉三个视图都要拼「人 + 部门 + 岗位 + 周期」，且都要批量取避免 N+1。
 * 抽出来还有一个好处：<b>「上级评分阶段隐藏自评分」这条规则只在一处实现</b>
 * （{@code showSelfScore} 参数）——它原本散在详情与指标行两处，改漏一处就会
 * 把自评分提前泄给上级，产生锚定效应。</p>
 */
@Component
@RequiredArgsConstructor
public class PerfVoAssembler {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final PerfCycleMapper cycleMapper;
    private final PerfAppealMapper appealMapper;
    private final PerfSheetSupport sheetSupport;

    public List<PerfSheetVO> toSheetVOs(List<PerfSheet> sheets) {
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
            vo.setTargetStatusLabel(PerfConstants.TARGET_STATUS_LABEL.getOrDefault(sheet.getTargetStatus(), "未知"));
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

    public Map<Long, Integer> appealStatusMap(List<PerfSheet> sheets) {
        Set<Long> ids = sheets.stream().map(PerfSheet::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return appealMapper.selectList(new LambdaQueryWrapper<PerfAppeal>()
                        .in(PerfAppeal::getSheetId, ids))
                .stream().collect(Collectors.toMap(PerfAppeal::getSheetId, PerfAppeal::getStatus, (a, b) -> a));
    }

    /**
     * @param withDetail     是否带指标行与申诉
     * @param showSelfScore  是否显示自评分（上级评分阶段默认隐藏，避免锚定效应）
     */
    public PerfSheetDetailVO toDetail(PerfSheet sheet, boolean withDetail, boolean showSelfScore) {
        PerfSheetDetailVO vo = new PerfSheetDetailVO();
        vo.setId(sheet.getId());
        vo.setCycleId(sheet.getCycleId());
        PerfCycle cycle = cycleMapper.selectById(sheet.getCycleId());
        if (cycle != null) {
            vo.setCycleCode(cycle.getCycleCode());
            vo.setCycleName(cycle.getCycleName());
            vo.setStage(cycle.getStage());
            vo.setStageLabel(PerfConstants.STAGE_LABEL.getOrDefault(cycle.getStage(), "未知"));
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
            sheetSupport.listGrades(sheet.getPlanId()).stream()
                    .filter(g -> Objects.equals(g.getGradeCode(), sheet.getGradeCode()))
                    .findFirst()
                    .ifPresent(g -> vo.setGradeName(g.getGradeName()));
        }
        if (withDetail) {
            List<PerfSheetItemVO> items = new ArrayList<>();
            for (PerfSheetItem item : sheetSupport.listItems(sheet.getId())) {
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

    public PerfAppealVO toAppealVO(PerfAppeal appeal) {
        PerfAppealVO vo = new PerfAppealVO();
        vo.setId(appeal.getId());
        vo.setSheetId(appeal.getSheetId());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setStatusLabel(PerfConstants.APPEAL_STATUS_LABEL.getOrDefault(appeal.getStatus(), "未知"));
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
