package com.kellen.oa_system.hr.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.hr.common.enums.EmploymentStatusEnum;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.employee.service.EmployeeCompletenessService;
import com.kellen.oa_system.hr.employee.support.EmployeeCompletenessDimension;
import com.kellen.oa_system.hr.employee.vo.CompletenessItemVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeCompletenessVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 建档完整度实现。
 *
 * <p>几个刻意的选择：</p>
 * <ul>
 *   <li><b>六个维度各查一次 COUNT</b>，而不是一条大 SQL 一把算完。一条 SQL 确实更快，
 *       但改一个维度就要动整条语句，而这里每个维度的条件都是独立子查询，加维度只加一行。</li>
 *   <li><b>口径与花名册一致</b>（在职 2/3/4 + 相同的数据范围），否则会出现
 *       「看板说 30 人无合同，点进去花名册只筛出 12 人」这种互不认账的情况。</li>
 *   <li><b>导出上限 5000 行</b>：与花名册导出保持一致，避免一次性把全表读进内存。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeCompletenessServiceImpl implements EmployeeCompletenessService {

    /** 在职口径：试用 / 正式 / 待离职 */
    private static final List<Integer> ON_DUTY = List.of(2, 3, 4);

    /** 导出上限，与花名册导出一致 */
    private static final int EXPORT_LIMIT = 5000;

    private static final List<String> EXPORT_HEADERS = List.of(
            "工号", "姓名", "部门", "任职状态", "入职日期", "直属上级");

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;

    @Override
    @Transactional(readOnly = true)
    @DataScope
    public EmployeeCompletenessVO summary() {
        Long totalOnDuty = userMapper.selectCount(onDutyWrapper());
        List<EmployeeCompletenessVO.Card> cards = new ArrayList<>();
        for (EmployeeCompletenessDimension dimension : EmployeeCompletenessDimension.values()) {
            Long count = userMapper.selectCount(withDimension(onDutyWrapper(), dimension));
            cards.add(EmployeeCompletenessVO.Card.builder()
                    .dimension(dimension.getCode())
                    .label(dimension.getLabel())
                    .count(count)
                    .percent(percent(count, totalOnDuty))
                    .build());
        }
        return EmployeeCompletenessVO.builder()
                .totalOnDuty(totalOnDuty)
                .cards(cards)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @DataScope
    public PageResult<CompletenessItemVO> items(String dimensionCode, int pageNo, int pageSize) {
        EmployeeCompletenessDimension dimension = requireDimension(dimensionCode);
        int safePageNo = pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize < 1 ? 20 : pageSize;

        LambdaQueryWrapper<User> wrapper = withDimension(onDutyWrapper(), dimension)
                .select(User::getId, User::getWorkNumber, User::getRealName, User::getDeptId,
                        User::getEmploymentStatus, User::getEntryDate, User::getDirectLeaderId)
                .orderByAsc(User::getWorkNumber);

        Page<User> page = userMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        return PageResult.of(toItems(page.getRecords()), page.getTotal(),
                (long) safePageNo, (long) safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    @DataScope
    public void export(String dimensionCode, HttpServletResponse response) {
        EmployeeCompletenessDimension dimension = requireDimension(dimensionCode);
        LambdaQueryWrapper<User> wrapper = withDimension(onDutyWrapper(), dimension)
                .select(User::getId, User::getWorkNumber, User::getRealName, User::getDeptId,
                        User::getEmploymentStatus, User::getEntryDate, User::getDirectLeaderId)
                .orderByAsc(User::getWorkNumber)
                .last("LIMIT " + EXPORT_LIMIT);

        List<CompletenessItemVO> items = toItems(userMapper.selectList(wrapper));
        List<List<Object>> rows = new ArrayList<>(items.size());
        for (CompletenessItemVO item : items) {
            rows.add(List.of(
                    blank(item.getWorkNumber()), blank(item.getRealName()),
                    blank(item.getDeptName()), blank(item.getEmploymentStatusLabel()),
                    item.getEntryDate() == null ? "" : item.getEntryDate().toString(),
                    blank(item.getDirectLeaderName())));
        }
        ExcelExportUtil.write(response, "建档完整度-" + dimension.getLabel() + ".xlsx",
                dimension.getLabel(), EXPORT_HEADERS, rows);
        log.info("导出建档完整度: dimension={}, rows={}", dimension.getCode(), rows.size());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private LambdaQueryWrapper<User> onDutyWrapper() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 数据范围与花名册同源：HR 看到的是本部门及下级，超管看全部。
        // 若这里不加，会出现「看板数字比清单多」——多出来的是他本来无权看的人
        hrDataScopeSupport.applyToUser(wrapper);
        wrapper.in(User::getEmploymentStatus, ON_DUTY);
        return wrapper;
    }

    private LambdaQueryWrapper<User> withDimension(LambdaQueryWrapper<User> wrapper,
                                                   EmployeeCompletenessDimension dimension) {
        dimension.getCondition().accept(wrapper);
        return wrapper;
    }

    private EmployeeCompletenessDimension requireDimension(String code) {
        EmployeeCompletenessDimension dimension = EmployeeCompletenessDimension.ofCode(code);
        if (dimension == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未知的完整度维度：" + code);
        }
        return dimension;
    }

    private static Double percent(Long count, Long total) {
        if (total == null || total == 0 || count == null) {
            return 0.0;
        }
        return Math.round(count * 1000.0 / total) / 10.0;
    }

    /** 批量补部门名与上级姓名，避免逐行查库 */
    private List<CompletenessItemVO> toItems(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = users.stream().map(User::getDeptId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department dept : departmentMapper.selectBatchIds(deptIds)) {
                deptNames.put(dept.getId(), dept.getDeptName());
            }
        }
        Set<Long> leaderIds = users.stream().map(User::getDirectLeaderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = new HashMap<>();
        if (!leaderIds.isEmpty()) {
            for (User leader : userMapper.selectBatchIds(leaderIds)) {
                leaderNames.put(leader.getId(), leader.getRealName());
            }
        }

        List<CompletenessItemVO> items = new ArrayList<>(users.size());
        for (User user : users) {
            items.add(CompletenessItemVO.builder()
                    .userId(user.getId())
                    .workNumber(user.getWorkNumber())
                    .realName(user.getRealName())
                    .deptName(deptNames.get(user.getDeptId()))
                    .employmentStatus(user.getEmploymentStatus())
                    .employmentStatusLabel(statusLabel(user.getEmploymentStatus()))
                    .entryDate(user.getEntryDate())
                    .directLeaderName(leaderNames.get(user.getDirectLeaderId()))
                    .build());
        }
        return items;
    }

    private static String statusLabel(Integer code) {
        try {
            return code == null ? null : EmploymentStatusEnum.of(code).getLabel();
        } catch (BizException e) {
            return null;
        }
    }

    private static String blank(String text) {
        return text == null ? "" : text;
    }
}
