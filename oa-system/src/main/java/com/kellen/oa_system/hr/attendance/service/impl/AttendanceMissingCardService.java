package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.CardSupplement;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.CardSupplementMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceDates;
import com.kellen.oa_system.hr.attendance.support.AttendanceRules;
import com.kellen.oa_system.hr.attendance.support.AttendanceScopeSupport;
import com.kellen.oa_system.hr.attendance.vo.MissingCardVO;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 漏卡待处理清单。
 *
 * <p>清单的口径是「{@code card_missing > 0} 且该方向还没有审批中/已通过的补卡单」。
 * 它存在的意义不是又一个列表页，而是把「补卡」分成两种事实来源：</p>
 *
 * <ul>
 *   <li><b>漏卡</b>——系统已经知道缺哪一侧（有排班 + 有另一侧记录），员工只是认领；</li>
 *   <li><b>主动补卡</b>——当天连排班记录都没有，靠自己声明。</li>
 * </ul>
 *
 * <p>每月 3 次的额度只约束后者。否则「考勤机只刷到一次卡」这种最常见的情形会迅速吃满额度，
 * 而它恰恰是制度上应该被允许修正的差错（参见业务优化方案 Q11 的结论）。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceMissingCardService {

    /** 补卡单状态：1 审批中 / 2 已通过 —— 这两种都算「已处理」，不再提示补卡 */
    private static final List<Integer> HANDLED_STATUS = List.of(1, 2);

    private final AttendanceMapper attendanceMapper;
    private final CardSupplementMapper cardMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final AttendanceScopeSupport scopeSupport;

    /** HR 视角：受数据范围约束 */
    public PageResult<MissingCardVO> pageMissing(String month, Long deptId, String keyword,
                                                 Integer pageNo, Integer pageSize) {
        YearMonth ym = AttendanceDates.parseMonth(month);
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Attendance> wrapper = baseWrapper(ym);
        scopeSupport.applyUserScope(wrapper, "pageMissing");
        if (deptId != null) {
            List<Long> ids = userIdsOfDept(deptId);
            if (ids.isEmpty()) {
                return PageResult.empty((long) no, (long) size);
            }
            wrapper.in(Attendance::getUserId, ids);
        }
        if (StringUtils.hasText(keyword)) {
            List<Long> ids = userIdsOfKeyword(keyword.trim());
            if (ids.isEmpty()) {
                return PageResult.empty((long) no, (long) size);
            }
            wrapper.in(Attendance::getUserId, ids);
        }
        Page<Attendance> page = attendanceMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 我的漏卡：只查自己，不受数据范围影响 */
    public List<MissingCardVO> myMissing(String month) {
        YearMonth ym = AttendanceDates.parseMonth(month);
        List<Attendance> list = attendanceMapper.selectList(baseWrapper(ym)
                .eq(Attendance::getUserId, SecurityContextUtil.getCurrentUserId()));
        return toVOs(list);
    }

    private LambdaQueryWrapper<Attendance> baseWrapper(YearMonth ym) {
        return new LambdaQueryWrapper<Attendance>()
                .gt(Attendance::getCardMissing, 0)
                .between(Attendance::getAttendanceDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByDesc(Attendance::getAttendanceDate)
                .orderByAsc(Attendance::getUserId);
    }

    private List<Long> userIdsOfDept(Long deptId) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .in(User::getDeptId, hrDataScopeSupport.subtreeIds(deptId)))
                .stream().map(User::getId).toList();
    }

    private List<Long> userIdsOfKeyword(String keyword) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .and(w -> w.like(User::getRealName, keyword).or().like(User::getWorkNumber, keyword)))
                .stream().map(User::getId).toList();
    }

    // ==================================================================
    // VO 组装：三张表各查一次，不随行数增长
    // ==================================================================

    private List<MissingCardVO> toVOs(List<Attendance> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = list.stream().map(Attendance::getUserId).distinct().toList();
        LocalDate from = list.stream().map(Attendance::getAttendanceDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate to = list.stream().map(Attendance::getAttendanceDate).max(LocalDate::compareTo).orElseThrow();

        Map<Long, User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentNames(users.values());
        Set<String> handled = handledDirections(userIds, from, to);

        List<MissingCardVO> result = new ArrayList<>(list.size());
        for (Attendance a : list) {
            MissingCardVO vo = new MissingCardVO();
            vo.setAttendanceId(a.getId());
            vo.setUserId(a.getUserId());
            vo.setAttendanceDate(a.getAttendanceDate());
            User user = users.get(a.getUserId());
            if (user != null) {
                vo.setWorkNumber(user.getWorkNumber());
                vo.setRealName(user.getRealName());
                vo.setDeptId(user.getDeptId());
                vo.setDeptName(deptNames.get(user.getDeptId()));
            }
            vo.setCardMissing(a.getCardMissing());
            vo.setMissingDirections(directionsOf(a.getCardMissing()));
            vo.setMissingLabel(labelOf(a.getCardMissing()));
            vo.setPendingDirections(vo.getMissingDirections().stream()
                    .filter(d -> handled.contains(a.getUserId() + "|" + a.getAttendanceDate() + "|" + d))
                    .toList());
            vo.setAttendanceStatus(a.getAttendanceStatus());
            vo.setAttendanceStatusLabel(AttendanceRules.statusLabel(a.getAttendanceStatus()));
            result.add(vo);
        }
        return result;
    }

    private Map<Long, String> departmentNames(java.util.Collection<User> users) {
        List<Long> deptIds = users.stream().map(User::getDeptId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Department d : departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .in(Department::getId, deptIds))) {
            map.put(d.getId(), d.getDeptName());
        }
        return map;
    }

    /** 已发起补卡的方向集合，键为 {@code 用户|日期|方向} */
    private Set<String> handledDirections(List<Long> userIds, LocalDate from, LocalDate to) {
        List<CardSupplement> cards = cardMapper.selectList(new LambdaQueryWrapper<CardSupplement>()
                .in(CardSupplement::getUserId, userIds)
                .between(CardSupplement::getCardDate, from, to)
                .in(CardSupplement::getCardStatus, HANDLED_STATUS));
        Set<String> set = new HashSet<>();
        for (CardSupplement c : cards) {
            set.add(c.getUserId() + "|" + c.getCardDate() + "|" + c.getDirection());
        }
        return set;
    }

    public static List<Integer> directionsOf(Integer cardMissing) {
        if (cardMissing == null || cardMissing == 0) {
            return List.of();
        }
        return switch (cardMissing) {
            case 1 -> List.of(AttendanceConstants.DIRECTION_IN);
            case 2 -> List.of(AttendanceConstants.DIRECTION_OUT);
            default -> List.of(AttendanceConstants.DIRECTION_IN, AttendanceConstants.DIRECTION_OUT);
        };
    }

    private static String labelOf(Integer cardMissing) {
        if (cardMissing == null || cardMissing == 0) {
            return null;
        }
        return switch (cardMissing) {
            case 1 -> "缺上班卡";
            case 2 -> "缺下班卡";
            default -> "缺上下班卡";
        };
    }
}
