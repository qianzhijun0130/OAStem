package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceScopeSupport;
import com.kellen.oa_system.hr.attendance.support.AttendanceVoAssembler;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 考勤月度汇总导出。
 *
 * <p>导出等于「把全员考勤带走」，所以单独成类、单独挂 {@code hr:attendance:export} 权限码：
 * 看一眼列表和拿走全表不该是同一个权限。数据范围由门面的 {@code @DataScope} 提供，
 * 这里只消费——漏消费会直接抛错而不是静默导出全公司（BE-OPT-02）。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceExportService {

    private final AttendanceMonthMapper monthMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final AttendanceScopeSupport scopeSupport;
    private final AttendanceVoAssembler assembler;

    public void exportMonth(String month, Long deptId, HttpServletResponse response) {
        List<AttendanceMonthVO> rows = assembler.toMonthVOs(monthMapper.selectList(deptQuery(month, deptId)));
        List<String> headers = List.of("工号", "姓名", "部门", "月份", "应出勤(天)", "实际出勤(天)",
                "迟到(次)", "早退(次)", "旷工(天)", "缺卡(次)", "出差(天)", "假别明细", "状态");
        List<List<Object>> data = new ArrayList<>();
        for (AttendanceMonthVO vo : rows) {
            data.add(List.of(
                    nvl(vo.getWorkNumber()), nvl(vo.getRealName()), nvl(vo.getDeptName()), nvl(vo.getMonth()),
                    vo.getShouldDays() == null ? BigDecimal.ZERO : vo.getShouldDays(),
                    vo.getActualDays() == null ? BigDecimal.ZERO : vo.getActualDays(),
                    vo.getLateCount() == null ? 0 : vo.getLateCount(),
                    vo.getEarlyCount() == null ? 0 : vo.getEarlyCount(),
                    vo.getAbsentDays() == null ? BigDecimal.ZERO : vo.getAbsentDays(),
                    vo.getMissingCount() == null ? 0 : vo.getMissingCount(),
                    vo.getTripDays() == null ? BigDecimal.ZERO : vo.getTripDays(),
                    nvl(vo.getLeaveDaysText()),
                    Objects.equals(vo.getStatus(), AttendanceConstants.MONTH_LOCKED) ? "已封账" : "未封账"));
        }
        ExcelExportUtil.write(response, "考勤月度汇总-" + month + ".xlsx", "月度汇总", headers, data);
    }

    /** 月度汇总查询条件（页面与导出共用，避免两处条件写得不一样） */
    private LambdaQueryWrapper<AttendanceMonth> deptQuery(String month, Long deptId) {
        LambdaQueryWrapper<AttendanceMonth> wrapper = new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, month)
                // 表里没有 dept_id（部门来自 oa_user），先按人排，导出时再按部门归组展示
                .orderByAsc(AttendanceMonth::getUserId);

        // 数据范围（本部门+下级 / 仅本人 / 全部）。exportMonth 已标 @DataScope；
        // 一旦漏标，visibleUserIds 会直接抛错，而不是让导出静默返回全公司数据（BE-OPT-02）。
        List<Long> scopedUsers = scopeSupport.visibleUserIds("exportMonth");

        List<Long> deptUsers = null;
        if (deptId != null) {
            List<Long> deptIds = new ArrayList<>();
            deptIds.add(deptId);
            collectChildDepts(deptId, deptIds);
            deptUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .in(User::getDeptId, deptIds).select(User::getId))
                    .stream().map(User::getId).toList();
        }

        // 部门筛选与数据范围取交集；两者都没有时不受限
        List<Long> allowed;
        if (scopedUsers == null) {
            allowed = deptUsers;
        } else if (deptUsers == null) {
            allowed = scopedUsers;
        } else {
            Set<Long> scope = new HashSet<>(scopedUsers);
            allowed = deptUsers.stream().filter(scope::contains).toList();
        }

        if (allowed == null) {
            return wrapper;
        }
        if (allowed.isEmpty()) {
            // 空集合不能拼 IN ()：用一个不可能成立的 id 让查询自然返回空
            wrapper.eq(AttendanceMonth::getUserId, -1L);
        } else {
            wrapper.in(AttendanceMonth::getUserId, allowed);
        }
        return wrapper;
    }

    private void collectChildDepts(Long parentId, List<Long> target) {
        List<Department> children = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(Department::getParentId, parentId).select(Department::getId));
        for (Department child : children) {
            if (!target.contains(child.getId())) {
                target.add(child.getId());
                collectChildDepts(child.getId(), target);
            }
        }
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
