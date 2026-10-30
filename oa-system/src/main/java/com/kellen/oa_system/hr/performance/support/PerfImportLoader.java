package com.kellen.oa_system.hr.performance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.ExcelImportUtil;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfPlanIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfIndicatorMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanGradeMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanIndicatorMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 绩效结果导入的取数层。
 *
 * <p>作用与考勤机导入的 Loader 相同：把「周期 / 方案指标 / 指标实体 / 等级表 / 员工 / 已有考核表 /
 * 已有指标行」这七次查询集中在这里，导入的写入循环里就没有 Mapper 可调。
 * 一次导入几十到几百人，若在循环里补一句 selectOne 就是几百次往返。</p>
 */
@Component
@RequiredArgsConstructor
public class PerfImportLoader {

    private final PerfCycleMapper cycleMapper;
    private final PerfPlanIndicatorMapper planIndicatorMapper;
    private final PerfIndicatorMapper indicatorMapper;
    private final PerfPlanGradeMapper gradeMapper;
    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;
    private final UserMapper userMapper;

    public PerfCycle requireCycle(Long cycleId) {
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        return cycle;
    }

    public List<PerfImportTemplate.PlanLink> loadLinks(Long planId) {
        return planIndicatorMapper.selectList(new LambdaQueryWrapper<PerfPlanIndicator>()
                        .eq(PerfPlanIndicator::getPlanId, planId)
                        .orderByAsc(PerfPlanIndicator::getSort))
                .stream()
                .map(r -> new PerfImportTemplate.PlanLink(r.getIndicatorId(), r.getWeight(), r.getSort()))
                .toList();
    }

    /** 指标实体：取名称与类型（指标列的表头与加减分判定都依赖它） */
    public Map<Long, PerfIndicator> loadIndicators(List<PerfImportTemplate.PlanLink> links) {
        List<Long> ids = links.stream().map(PerfImportTemplate.PlanLink::indicatorId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return indicatorMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PerfIndicator::getId, i -> i, (a, b) -> a));
    }

    public List<PerfPlanGrade> loadGrades(Long planId) {
        return gradeMapper.selectList(new LambdaQueryWrapper<PerfPlanGrade>()
                .eq(PerfPlanGrade::getPlanId, planId)
                .orderByAsc(PerfPlanGrade::getSort));
    }

    /** 工号 → 员工。查不到的工号在调用方逐行报错 */
    public Map<String, User> loadUsers(List<List<String>> rows, PerfImportTemplate.Layout layout) {
        List<String> workNumbers = rows.stream().skip(1)
                .map(r -> ExcelImportUtil.cellAt(r, layout.workNoIndex()))
                .filter(s -> !s.isBlank()).distinct().toList();
        if (workNumbers.isEmpty()) {
            return Map.of();
        }
        Map<String, User> map = new HashMap<>();
        for (User u : userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getWorkNumber, workNumbers))) {
            map.put(u.getWorkNumber(), u);
        }
        return map;
    }

    /** 键为用户 id：该周期下已有的考核表 */
    public Map<Long, PerfSheet> loadSheets(Long cycleId, Collection<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PerfSheet> map = new HashMap<>();
        for (PerfSheet s : sheetMapper.selectList(new LambdaQueryWrapper<PerfSheet>()
                .eq(PerfSheet::getCycleId, cycleId)
                .in(PerfSheet::getUserId, userIds))) {
            map.put(s.getUserId(), s);
        }
        return map;
    }

    /** 键为 {@code sheetId|indicatorId}：已有考核表的指标行，更新时按它回填分数 */
    public Map<String, PerfSheetItem> loadItems(Collection<PerfSheet> sheets) {
        List<Long> sheetIds = sheets.stream().map(PerfSheet::getId).toList();
        if (sheetIds.isEmpty()) {
            return Map.of();
        }
        Map<String, PerfSheetItem> map = new HashMap<>();
        for (PerfSheetItem item : sheetItemMapper.selectList(new LambdaQueryWrapper<PerfSheetItem>()
                .in(PerfSheetItem::getSheetId, sheetIds))) {
            map.put(item.getSheetId() + "|" + item.getIndicatorId(), item);
        }
        return map;
    }
}
