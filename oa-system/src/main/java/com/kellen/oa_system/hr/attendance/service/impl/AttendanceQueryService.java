package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.attendance.dto.AttendancePageQuery;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceDates;
import com.kellen.oa_system.hr.attendance.support.AttendanceScopeSupport;
import com.kellen.oa_system.hr.attendance.support.AttendanceVoAssembler;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.hr.attendance.vo.AttendanceVO;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.util.List;

/**
 * 考勤查询：日明细（管理视角 / 本人）与月度汇总。
 *
 * <p>三个入口都必须先过数据范围再拼条件。方法本身<b>不带</b> {@code @DataScope}——
 * 快照由门面上的注解写入，这里只消费；一旦某个入口漏了消费，
 * {@link AttendanceScopeSupport} 会直接抛错而不是放行（BE-OPT-02）。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

    private final AttendanceMapper attendanceMapper;
    private final AttendanceMonthMapper monthMapper;
    private final UserMapper userMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final AttendanceScopeSupport scopeSupport;
    private final AttendanceVoAssembler assembler;

    public PageResult<AttendanceVO> page(AttendancePageQuery query) {
        YearMonth ym = AttendanceDates.parseMonth(query.getMonth());
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<Attendance>()
                .between(Attendance::getAttendanceDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByDesc(Attendance::getAttendanceDate)
                .orderByAsc(Attendance::getUserId);
        scopeSupport.applyUserScope(wrapper, "page");
        if (query.getUserId() != null) {
            wrapper.eq(Attendance::getUserId, query.getUserId());
        }
        if (query.getDeptId() != null) {
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .in(User::getDeptId, hrDataScopeSupport.subtreeIds(query.getDeptId())))
                    .stream().map(User::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.empty((long) pageNo, (long) pageSize);
            }
            wrapper.in(Attendance::getUserId, ids);
        }
        if (query.getAttendanceStatus() != null) {
            wrapper.eq(Attendance::getAttendanceStatus, query.getAttendanceStatus());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .and(w -> w.like(User::getRealName, kw).or().like(User::getWorkNumber, kw)))
                    .stream().map(User::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.empty((long) pageNo, (long) pageSize);
            }
            wrapper.in(Attendance::getUserId, ids);
        }

        Page<Attendance> page = attendanceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(assembler.toAttendanceVOs(page.getRecords()), page.getTotal(),
                page.getCurrent(), page.getSize());
    }

    public List<AttendanceVO> myMonth(String month) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        YearMonth ym = AttendanceDates.parseMonth(month);
        List<Attendance> list = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .between(Attendance::getAttendanceDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByAsc(Attendance::getAttendanceDate));
        return assembler.toAttendanceVOs(list);
    }

    public PageResult<AttendanceMonthVO> pageMonth(String month, Long deptId,
                                                   Integer pageNo, Integer pageSize) {
        YearMonth ym = AttendanceDates.parseMonth(month);
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String monthText = ym.format(AttendanceConstants.MONTH_FORMAT);

        LambdaQueryWrapper<AttendanceMonth> wrapper = new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, monthText)
                .orderByAsc(AttendanceMonth::getUserId);
        List<Long> visible = scopeSupport.visibleUserIds("pageMonth");
        if (visible != null) {
            if (visible.isEmpty()) {
                return PageResult.empty((long) no, (long) size);
            }
            wrapper.in(AttendanceMonth::getUserId, visible);
        }
        if (deptId != null) {
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .in(User::getDeptId, hrDataScopeSupport.subtreeIds(deptId)))
                    .stream().map(User::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.empty((long) no, (long) size);
            }
            wrapper.in(AttendanceMonth::getUserId, ids);
        }
        Page<AttendanceMonth> page = monthMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toMonthVOs(page.getRecords()), page.getTotal(),
                page.getCurrent(), page.getSize());
    }
}
