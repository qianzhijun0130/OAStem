package com.kellen.oa_system.hr.performance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfCycleMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfPlanGradeMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 绩效链路的共享查询与校验（BE-OPT-08 拆分时抽出）。
 *
 * <p>四个写服务（目标 / 评分 / 申诉）每一个动作都以「取考核表 → 取周期 → 校验归属 →
 * 校验可写 → 取指标/等级」开头。这套前缀放进任一服务都会让其它服务反向依赖它，
 * 因此抽成无状态支撑类，依赖方向恒为「服务 → 支撑」。</p>
 *
 * <p><b>四道门槛的落点在这两个 assert 里</b>（调用方各自补阶段判断）：
 * 归属（只能操作自己的）与可写（归档后一律拒）。</p>
 */
@Component
@RequiredArgsConstructor
public class PerfSheetSupport {

    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;
    private final PerfCycleMapper cycleMapper;
    private final PerfPlanGradeMapper gradeMapper;
    private final UserMapper userMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final DataScopeSupport dataScopeSupport;

    public PerfSheet requireSheet(Long sheetId) {
        if (sheetId == null) {
            throw new BizException(ErrorCode.PERF_SHEET_NOT_FOUND);
        }
        PerfSheet sheet = sheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new BizException(ErrorCode.PERF_SHEET_NOT_FOUND);
        }
        return sheet;
    }

    public PerfCycle requireCycle(Long cycleId) {
        PerfCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "周期不存在");
        }
        return cycle;
    }

    public void assertOwner(PerfSheet sheet) {
        if (!Objects.equals(sheet.getUserId(), SecurityContextUtil.getCurrentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的考核表");
        }
    }

    public void assertWritable(PerfCycle cycle) {
        if (Objects.equals(cycle.getStatus(), 2) || Objects.equals(cycle.getStage(), PerfConstants.STAGE_ARCHIVED)) {
            throw new BizException(ErrorCode.PERF_ARCHIVED_READONLY);
        }
    }

    public List<PerfSheetItem> listItems(Long sheetId) {
        return sheetItemMapper.selectList(new LambdaQueryWrapper<PerfSheetItem>()
                .eq(PerfSheetItem::getSheetId, sheetId)
                .orderByAsc(PerfSheetItem::getSort)
                .orderByAsc(PerfSheetItem::getId));
    }

    public List<PerfPlanGrade> listGrades(Long planId) {
        return gradeMapper.selectList(new LambdaQueryWrapper<PerfPlanGrade>()
                .eq(PerfPlanGrade::getPlanId, planId)
                .orderByAsc(PerfPlanGrade::getSort));
    }

    public List<Long> matchedUserIds(String keyword) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .and(w -> w.like(User::getRealName, keyword).or().like(User::getWorkNumber, keyword))
                        .select(User::getId))
                .stream().map(User::getId).toList();
    }

    /**
     * 当前用户数据范围内的用户 id。
     *
     * <p>快照为空 = 调用方漏标 {@code @DataScope}。此处直接失败而不是返回「全部用户」：
     * 后者会让 {@code wrapper.in(userId, 全部用户)} 等价于不过滤，形成静默越权（BE-OPT-02 根因）。</p>
     */
    public Set<Long> visibleUserIds() {
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
    public LocalDateTime addWorkDays(LocalDateTime from, int days) {
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

    public static String blank(String value) {
        return value == null ? "" : value;
    }
}
