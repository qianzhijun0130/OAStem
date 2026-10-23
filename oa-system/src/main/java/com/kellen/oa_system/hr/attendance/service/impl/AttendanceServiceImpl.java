package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.attendance.dto.AttendancePageQuery;
import com.kellen.oa_system.hr.attendance.dto.ClockRequest;
import com.kellen.oa_system.hr.attendance.dto.ScheduleBatchRequest;
import com.kellen.oa_system.hr.attendance.dto.ShiftSaveRequest;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.entity.BusinessTrip;
import com.kellen.oa_system.hr.attendance.entity.Overtime;
import com.kellen.oa_system.hr.attendance.entity.Holiday;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.mapper.BusinessTripMapper;
import com.kellen.oa_system.hr.attendance.mapper.OvertimeMapper;
import com.kellen.oa_system.hr.attendance.mapper.HolidayMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.hr.attendance.vo.AttendanceVO;
import com.kellen.oa_system.hr.attendance.vo.ScheduleVO;
import com.kellen.oa_system.hr.attendance.vo.ShiftVO;
import com.kellen.oa_system.hr.attendance.vo.TodayAttendanceVO;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.leave.entity.Leave;
import com.kellen.oa_system.hr.leave.mapper.LeaveMapper;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 考勤业务实现。
 *
 * <p>三个关键口径（与《开发文档/人力资源/02》一致，改动前请先看文档）：</p>
 * <ol>
 *   <li>未排班的日期<b>不产生考勤记录</b>，也不判缺勤——否则新员工入职前会被算成旷工；</li>
 *   <li>只缺一次卡记 {@code card_missing}，<b>不判旷工</b>，等补卡输入后再重算；</li>
 *   <li>日结幂等：同一人同一天只有一行（唯一索引 + upsert），重跑不会翻倍。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 仅用于把月汇总里的 JSON 明细转成展示文本，与业务序列化无关 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_LATE = 2;
    private static final int STATUS_EARLY = 3;
    private static final int STATUS_ABSENT = 4;
    /** 当天有已通过的请假：算正常出勤的一部分，不能计迟到/早退/缺卡（否则工资会双重扣） */
    private static final int STATUS_LEAVE = 5;
    /** 当天在出差中：同上，出差期间免打卡 */
    private static final int STATUS_TRIP = 6;

    /** 假别名称（与字典 hr_leave_type 一致，用于月汇总的 leave_days_json） */
    private static final Map<Integer, String> LEAVE_TYPE_LABEL = Map.of(
            1, "事假", 2, "病假", 3, "年假", 4, "婚假",
            5, "产假", 6, "陪产假", 7, "丧假", 8, "调休");

    private final ShiftMapper shiftMapper;
    private final HolidayMapper holidayMapper;
    private final ShiftScheduleMapper scheduleMapper;
    private final AttendanceMapper attendanceMapper;
    private final AttendanceMonthMapper monthMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final LeaveMapper leaveMapper;
    private final BusinessTripMapper tripMapper;
    private final OvertimeMapper overtimeMapper;
    private final ObjectMapper objectMapper;

    // ==================================================================
    // 班次
    // ==================================================================

    @Override
    public List<ShiftVO> listShifts() {
        List<Shift> list = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .orderByAsc(Shift::getId));
        List<ShiftVO> vos = new ArrayList<>(list.size());
        for (Shift s : list) {
            ShiftVO vo = new ShiftVO();
            vo.setId(s.getId());
            vo.setShiftCode(s.getShiftCode());
            vo.setShiftName(s.getShiftName());
            vo.setWorkStart(s.getWorkStart());
            vo.setWorkEnd(s.getWorkEnd());
            vo.setRestMinutes(s.getRestMinutes());
            vo.setLateGrace(s.getLateGrace());
            vo.setEarlyGrace(s.getEarlyGrace());
            vo.setFlexible(s.getFlexible());
            vo.setFreeClock(s.getFreeClock());
            vo.setStatus(s.getStatus());
            vo.setStandardHours(standardHours(s));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveShift(ShiftSaveRequest request) {
        LambdaQueryWrapper<Shift> dup = new LambdaQueryWrapper<Shift>()
                .eq(Shift::getShiftCode, request.getShiftCode().trim());
        if (request.getId() != null) {
            dup.ne(Shift::getId, request.getId());
        }
        if (shiftMapper.selectCount(dup) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次编码已存在");
        }
        Shift entity = request.getId() == null ? new Shift() : shiftMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        entity.setShiftCode(request.getShiftCode().trim());
        entity.setShiftName(request.getShiftName().trim());
        entity.setWorkStart(request.getWorkStart());
        entity.setWorkEnd(request.getWorkEnd());
        entity.setRestMinutes(request.getRestMinutes() == null ? 60 : request.getRestMinutes());
        entity.setLateGrace(request.getLateGrace() == null ? 5 : request.getLateGrace());
        entity.setEarlyGrace(request.getEarlyGrace() == null ? 0 : request.getEarlyGrace());
        entity.setFlexible(request.getFlexible() == null ? 0 : request.getFlexible());
        entity.setFreeClock(request.getFreeClock() == null ? 0 : request.getFreeClock());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (request.getId() == null) {
            shiftMapper.insert(entity);
        } else {
            shiftMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void deleteShift(Long id) {
        Shift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        // 已排班的班次不允许删除：删了会让历史排班指向不存在的班次，考勤无法重算
        Long used = scheduleMapper.selectCount(new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getShiftId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "该班次已被 " + used + " 条排班引用，请先停用而不是删除");
        }
        shiftMapper.deleteById(id);
    }

    @Override
    public void updateShiftStatus(Long id, Integer status) {
        Shift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        Shift patch = new Shift();
        patch.setId(id);
        patch.setStatus(status);
        shiftMapper.updateById(patch);
    }

    // ==================================================================
    // 排班
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSchedule(ScheduleBatchRequest request) {
        Shift shift = shiftMapper.selectById(request.getShiftId());
        if (shift == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        YearMonth ym = parseMonth(request.getMonth());
        LocalDate start = request.getStartDate() != null ? request.getStartDate() : ym.atDay(1);
        LocalDate end = request.getEndDate() != null ? request.getEndDate() : ym.atEndOfMonth();

        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getDeptId, hrDataScopeSupport.subtreeIds(request.getDeptId()))
                .in(User::getEmploymentStatus, List.of(2, 3, 4)));
        if (users.isEmpty()) {
            return 0;
        }
        boolean includeWeekend = request.getIncludeWeekend() != null && request.getIncludeWeekend() == 1;
        boolean overwrite = request.getOverwrite() != null && request.getOverwrite() == 1;

        Set<LocalDate> holidays = holidayMapper.selectList(new LambdaQueryWrapper<Holiday>()
                        .between(Holiday::getHolidayDate, start, end))
                .stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());

        int count = 0;
        for (User user : users) {
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                boolean weekend = date.getDayOfWeek().getValue() >= 6;
                boolean offDay = holidays.contains(date) || (weekend && !includeWeekend);
                ShiftSchedule existing = scheduleMapper.selectOne(new LambdaQueryWrapper<ShiftSchedule>()
                        .eq(ShiftSchedule::getUserId, user.getId())
                        .eq(ShiftSchedule::getScheduleDate, date)
                        .last("LIMIT 1"));
                if (existing != null && !overwrite) {
                    continue;
                }
                ShiftSchedule schedule = existing == null ? new ShiftSchedule() : existing;
                schedule.setUserId(user.getId());
                schedule.setScheduleDate(date);
                schedule.setShiftId(offDay ? null : shift.getId());
                schedule.setDayType(offDay ? (holidays.contains(date) ? 3 : 2) : 1);
                schedule.setSettled(0);
                if (existing == null) {
                    scheduleMapper.insert(schedule);
                } else {
                    scheduleMapper.updateById(schedule);
                }
                count++;
            }
        }
        log.info("批量排班完成: deptId={} month={} shiftId={} count={}",
                request.getDeptId(), ym, shift.getId(), count);
        return count;
    }

    @Override
    public List<ScheduleVO> listSchedule(Long deptId, String month) {
        YearMonth ym = parseMonth(month);
        LambdaQueryWrapper<ShiftSchedule> wrapper = new LambdaQueryWrapper<ShiftSchedule>()
                .between(ShiftSchedule::getScheduleDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByAsc(ShiftSchedule::getScheduleDate);
        if (deptId != null) {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .select(User::getId)
                    .in(User::getDeptId, hrDataScopeSupport.subtreeIds(deptId)));
            if (users.isEmpty()) {
                return List.of();
            }
            wrapper.in(ShiftSchedule::getUserId, users.stream().map(User::getId).toList());
        }
        List<ShiftSchedule> list = scheduleMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(list.stream()
                        .map(ShiftSchedule::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> shiftNames = shiftMapper.selectList(null).stream()
                .collect(Collectors.toMap(Shift::getId, Shift::getShiftName, (a, b) -> a));

        List<ScheduleVO> vos = new ArrayList<>(list.size());
        for (ShiftSchedule s : list) {
            ScheduleVO vo = new ScheduleVO();
            vo.setId(s.getId());
            vo.setUserId(s.getUserId());
            User u = users.get(s.getUserId());
            vo.setRealName(u == null ? null : u.getRealName());
            vo.setWorkNumber(u == null ? null : u.getWorkNumber());
            vo.setScheduleDate(s.getScheduleDate());
            vo.setShiftId(s.getShiftId());
            vo.setShiftName(shiftNames.get(s.getShiftId()));
            vo.setDayType(s.getDayType());
            vo.setSettled(s.getSettled());
            vos.add(vo);
        }
        return vos;
    }

    // ==================================================================
    // 打卡与查询
    // ==================================================================

    @Override
    public TodayAttendanceVO today() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LocalDate date = LocalDate.now();
        TodayAttendanceVO vo = new TodayAttendanceVO();
        vo.setDate(date);

        ShiftSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getUserId, userId)
                .eq(ShiftSchedule::getScheduleDate, date)
                .last("LIMIT 1"));
        if (schedule == null || schedule.getShiftId() == null) {
            // 未排班 / 休息日：不产生考勤记录，前端不展示打卡按钮
            vo.setScheduled(false);
            vo.setCanClockIn(false);
            vo.setCanClockOut(false);
            return vo;
        }
        Shift shift = shiftMapper.selectById(schedule.getShiftId());
        vo.setScheduled(true);
        if (shift != null) {
            vo.setShiftName(shift.getShiftName());
            vo.setWorkStart(shift.getWorkStart());
            vo.setWorkEnd(shift.getWorkEnd());
        }
        Attendance attendance = findAttendance(userId, date);
        if (attendance != null) {
            vo.setCheckInTime(attendance.getCheckInTime());
            vo.setCheckOutTime(attendance.getCheckOutTime());
            vo.setAttendanceStatus(attendance.getAttendanceStatus());
            vo.setAttendanceStatusLabel(statusLabel(attendance.getAttendanceStatus()));
            vo.setLateMinutes(attendance.getLateMinutes());
            vo.setEarlyMinutes(attendance.getEarlyMinutes());
            if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                vo.setWorkedMinutes(Duration.between(attendance.getCheckInTime(),
                        attendance.getCheckOutTime()).toMinutes());
            }
        }
        vo.setCanClockIn(attendance == null || attendance.getCheckInTime() == null);
        vo.setCanClockOut((attendance != null && attendance.getCheckInTime() != null
                && attendance.getCheckOutTime() == null));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clock(ClockRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LocalDate date = LocalDate.now();
        ShiftSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getUserId, userId)
                .eq(ShiftSchedule::getScheduleDate, date)
                .last("LIMIT 1"));
        if (schedule == null || schedule.getShiftId() == null) {
            throw new BizException(ErrorCode.HR_ATTENDANCE_NOT_SCHEDULED);
        }
        Shift shift = shiftMapper.selectById(schedule.getShiftId());
        if (shift == null) {
            throw new BizException(ErrorCode.HR_ATTENDANCE_NOT_SCHEDULED);
        }

        Attendance attendance = findAttendance(userId, date);
        if (attendance == null) {
            attendance = new Attendance();
            attendance.setUserId(userId);
            attendance.setAttendanceDate(date);
            attendance.setShiftId(shift.getId());
            if (request.getDirection() == 1) {
                attendance.setCheckInTime(LocalDateTime.now());
                attendance.setCheckInLocation(request.getLocation());
                attendance.setCheckInSource(1);
            } else {
                attendance.setCheckOutTime(LocalDateTime.now());
                attendance.setCheckOutLocation(request.getLocation());
                attendance.setCheckOutSource(1);
            }
            applyStatus(attendance, shift, date);
            attendanceMapper.insert(attendance);
            return;
        }
        if (request.getDirection() == 1) {
            if (attendance.getCheckInTime() != null) {
                throw new BizException(ErrorCode.HR_ATTENDANCE_ALREADY_CLOCKED, "今日已打过上班卡");
            }
            attendance.setCheckInTime(LocalDateTime.now());
            attendance.setCheckInLocation(request.getLocation());
            attendance.setCheckInSource(1);
        } else {
            if (attendance.getCheckOutTime() != null) {
                throw new BizException(ErrorCode.HR_ATTENDANCE_ALREADY_CLOCKED, "今日已打过下班卡");
            }
            if (attendance.getCheckInTime() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "请先打上班卡");
            }
            attendance.setCheckOutTime(LocalDateTime.now());
            attendance.setCheckOutLocation(request.getLocation());
            attendance.setCheckOutSource(1);
        }
        attendance.setShiftId(shift.getId());
        applyStatus(attendance, shift, date);
        attendanceMapper.updateById(attendance);
    }

    @Override
    @DataScope
    public PageResult<AttendanceVO> page(AttendancePageQuery query) {
        YearMonth ym = parseMonth(query.getMonth());
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<Attendance>()
                .between(Attendance::getAttendanceDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByDesc(Attendance::getAttendanceDate)
                .orderByAsc(Attendance::getUserId);
        applyUserScope(wrapper);
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
        return PageResult.of(toAttendanceVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<AttendanceVO> myMonth(String month) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        YearMonth ym = parseMonth(month);
        List<Attendance> list = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .between(Attendance::getAttendanceDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByAsc(Attendance::getAttendanceDate));
        return toAttendanceVOs(list);
    }

    // ==================================================================
    // 汇总与结算
    // ==================================================================

    @Override
    @DataScope
    public PageResult<AttendanceMonthVO> pageMonth(String month, Long deptId, Integer pageNo, Integer pageSize) {
        YearMonth ym = parseMonth(month);
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String monthText = ym.format(MONTH_FORMAT);

        LambdaQueryWrapper<AttendanceMonth> wrapper = new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, monthText)
                .orderByAsc(AttendanceMonth::getUserId);
        List<Long> visible = visibleUserIds();
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
        return PageResult.of(toMonthVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleDate(LocalDate date, boolean force) {
        LambdaQueryWrapper<ShiftSchedule> wrapper = new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getScheduleDate, date);
        if (!force) {
            wrapper.eq(ShiftSchedule::getSettled, 0);
        }
        List<ShiftSchedule> schedules = scheduleMapper.selectList(wrapper);
        if (schedules.isEmpty()) {
            return 0;
        }
        // 一次性把当天「已通过的请假 / 出差」查出来：日结是按人循环，逐人查库会变成 N+1
        Set<Long> onLeave = approvedLeaveUserIds(date, date);
        Set<Long> onTrip = approvedTripUserIds(date, date);

        int handled = 0;
        for (ShiftSchedule schedule : schedules) {
            Shift shift = schedule.getShiftId() == null ? null : shiftMapper.selectById(schedule.getShiftId());
            if (shift == null) {
                // 休息日 / 法定节假日：不产生考勤记录，只把排班标记为已处理
                ShiftSchedule patch = new ShiftSchedule();
                patch.setId(schedule.getId());
                patch.setSettled(1);
                scheduleMapper.updateById(patch);
                continue;
            }
            Attendance attendance = findAttendance(schedule.getUserId(), date);
            if (attendance == null) {
                attendance = new Attendance();
                attendance.setUserId(schedule.getUserId());
                attendance.setAttendanceDate(date);
                attendance.setShiftId(shift.getId());
                applyStatus(attendance, shift, date);
                applyLeaveOrTrip(attendance, schedule.getUserId(), onLeave, onTrip);
                attendance.setSettled(1);
                attendanceMapper.insert(attendance);
            } else {
                attendance.setShiftId(shift.getId());
                applyStatus(attendance, shift, date);
                applyLeaveOrTrip(attendance, schedule.getUserId(), onLeave, onTrip);
                attendance.setSettled(1);
                attendanceMapper.updateById(attendance);
            }
            ShiftSchedule patch = new ShiftSchedule();
            patch.setId(schedule.getId());
            patch.setSettled(1);
            scheduleMapper.updateById(patch);
            handled++;
        }
        return handled;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleMonth(String month) {
        YearMonth ym = parseMonth(month);
        String text = ym.format(MONTH_FORMAT);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<ShiftSchedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<ShiftSchedule>()
                .between(ShiftSchedule::getScheduleDate, start, end));
        List<Attendance> attendances = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .between(Attendance::getAttendanceDate, start, end));

        Map<Long, BigDecimal> shouldDays = new HashMap<>();
        for (ShiftSchedule s : schedules) {
            if (s.getShiftId() != null) {
                shouldDays.merge(s.getUserId(), BigDecimal.ONE, BigDecimal::add);
            }
        }
        Map<Long, List<Attendance>> byUser = attendances.stream()
                .collect(Collectors.groupingBy(Attendance::getUserId));

        Set<Long> userIds = new HashSet<>(shouldDays.keySet());
        userIds.addAll(byUser.keySet());

        // 月汇总里的「假别天数 / 加班时数 / 出差天数」是薪资扣款的依据，必须由月结固化，
        // 不能让薪资模块自己去猜（否则两边算法一有差异就对不上账）
        Set<Long> onLeaveAll = approvedLeaveUserIds(start, end);
        Set<Long> onTripAll = approvedTripUserIds(start, end);
        Map<Long, Map<String, BigDecimal>> leaveDays = new HashMap<>();
        Map<Long, BigDecimal> tripDays = new HashMap<>();
        Map<Long, Map<String, BigDecimal>> overtimeHours = new HashMap<>();
        aggregateLeaveDays(start, end, onLeaveAll, leaveDays);
        aggregateTripDays(start, end, onTripAll, tripDays);
        aggregateOvertime(start, end, overtimeHours);

        int count = 0;
        for (Long userId : userIds) {
            AttendanceMonth existing = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                    .eq(AttendanceMonth::getUserId, userId)
                    .eq(AttendanceMonth::getMonth, text)
                    .last("LIMIT 1"));
            // 已封账的月份绝不覆盖：封账的意义就是「结果不能再变」
            if (existing != null && Objects.equals(existing.getStatus(), 2)) {
                continue;
            }
            List<Attendance> mine = byUser.getOrDefault(userId, List.of());
            BigDecimal actual = BigDecimal.ZERO;
            BigDecimal absent = BigDecimal.ZERO;
            int late = 0;
            int early = 0;
            int missing = 0;
            for (Attendance a : mine) {
                if (a.getCheckInTime() != null) {
                    actual = actual.add(BigDecimal.ONE);
                }
                if (Objects.equals(a.getAttendanceStatus(), STATUS_ABSENT)) {
                    absent = absent.add(BigDecimal.ONE);
                }
                if (Objects.equals(a.getAttendanceStatus(), STATUS_LATE)) {
                    late++;
                }
                if (Objects.equals(a.getAttendanceStatus(), STATUS_EARLY)) {
                    early++;
                }
                if (a.getCardMissing() != null && a.getCardMissing() > 0) {
                    missing++;
                }
            }
            AttendanceMonth entity = existing == null ? new AttendanceMonth() : existing;
            entity.setUserId(userId);
            entity.setMonth(text);
            entity.setShouldDays(shouldDays.getOrDefault(userId, BigDecimal.ZERO));
            entity.setActualDays(actual);
            entity.setLateCount(late);
            entity.setEarlyCount(early);
            entity.setAbsentDays(absent);
            entity.setMissingCount(missing);
            entity.setLeaveDaysJson(toJson(leaveDays.get(userId)));
            entity.setOvertimeJson(toJson(overtimeHours.get(userId)));
            entity.setTripDays(tripDays.getOrDefault(userId, BigDecimal.ZERO));
            entity.setStatus(1);
            if (existing == null) {
                monthMapper.insert(entity);
            } else {
                monthMapper.updateById(entity);
            }
            count++;
        }
        log.info("月度考勤汇总完成: month={} users={}", text, count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuild(String month) {
        YearMonth ym = parseMonth(month);
        int days = 0;
        for (LocalDate date = ym.atDay(1); !date.isAfter(ym.atEndOfMonth()); date = date.plusDays(1)) {
            days += settleDate(date, true);
        }
        int users = settleMonth(month);
        log.info("考勤重算完成: month={} days={} users={}", month, days, users);
        return days + users;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockMonth(String month) {
        String text = parseMonth(month).format(MONTH_FORMAT);
        List<AttendanceMonth> list = monthMapper.selectList(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, text));
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该月暂无考勤汇总，请先执行月结");
        }
        Long operator = SecurityContextUtil.getCurrentUserId();
        for (AttendanceMonth m : list) {
            if (Objects.equals(m.getStatus(), 2)) {
                continue;
            }
            AttendanceMonth patch = new AttendanceMonth();
            patch.setId(m.getId());
            patch.setStatus(2);
            patch.setLockTime(LocalDateTime.now());
            patch.setLockBy(operator);
            monthMapper.updateById(patch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockMonth(String month, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "解封必须填写原因");
        }
        String text = parseMonth(month).format(MONTH_FORMAT);
        List<AttendanceMonth> list = monthMapper.selectList(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, text));
        for (AttendanceMonth m : list) {
            AttendanceMonth patch = new AttendanceMonth();
            patch.setId(m.getId());
            patch.setStatus(1);
            monthMapper.updateById(patch);
        }
        log.warn("考勤月份解封: month={} reason={} operator={}", text, reason,
                SecurityContextUtil.getCurrentUserId());
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 根据打卡情况计算状态、迟到/早退分钟与缺卡标记。
     *
     * <p><b>只缺一次卡不判缺勤</b>：标 {@code card_missing} 并落正常/迟到/早退，
     * 等补卡通过后重算。把「少打一次卡」直接算旷工会误扣工资，是最常见的实现错误。</p>
     */
    /**
     * 已通过的请假/出差覆盖当天考勤状态。
     *
     * <p><b>为什么必须覆盖</b>：请假当天通常没有打卡记录，若按常规逻辑会判成「缺勤 + 缺卡」，
     * 于是薪资既扣旷工又扣满勤，员工请一天假被扣两次。
     * 出差同理——出差期间本来就不该打卡。</p>
     *
     * <p>请假优先于出差：两者时间重叠时以请假为准（请假要扣额度，是更强的事实）。</p>
     */
    private void applyLeaveOrTrip(Attendance attendance, Long userId,
                                  Set<Long> onLeave, Set<Long> onTrip) {
        boolean leave = onLeave.contains(userId);
        boolean trip = !leave && onTrip.contains(userId);
        if (!leave && !trip) {
            return;
        }
        attendance.setAttendanceStatus(leave ? STATUS_LEAVE : STATUS_TRIP);
        // 假勤/出差不产生迟到、早退、缺卡，也不计工时——全部由当天假勤单解释
        attendance.setLateMinutes(0);
        attendance.setEarlyMinutes(0);
        attendance.setCardMissing(0);
        attendance.setWorkDuration(BigDecimal.ZERO);
    }

    /** 区间内「存在已通过请假」的用户集合（一次查询，供日结/月结复用） */
    private Set<Long> approvedLeaveUserIds(LocalDate start, LocalDate end) {
        List<Leave> leaves = leaveMapper.selectList(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getLeaveStatus, 2)
                .le(Leave::getStartTime, end.atTime(23, 59, 59))
                .ge(Leave::getEndTime, start.atStartOfDay()));
        return leaves.stream().map(Leave::getUserId).collect(Collectors.toSet());
    }

    /** 区间内「存在已通过出差」的用户集合 */
    private Set<Long> approvedTripUserIds(LocalDate start, LocalDate end) {
        List<BusinessTrip> trips = tripMapper.selectList(new LambdaQueryWrapper<BusinessTrip>()
                .eq(BusinessTrip::getTripStatus, 2)
                .le(BusinessTrip::getStartDate, end)
                .ge(BusinessTrip::getEndDate, start));
        return trips.stream().map(BusinessTrip::getUserId).collect(Collectors.toSet());
    }

    /**
     * 按假别汇总本月天数。
     *
     * <p>跨月的请假单按「重叠天数 / 总跨度天数 × 请假天数」折算，
     * 这样 9/29~10/3 的 5 天假在两个月的月汇总里各占自己的部分，合计仍等于 5 天。</p>
     */
    private void aggregateLeaveDays(LocalDate start, LocalDate end, Set<Long> userIds,
                                    Map<Long, Map<String, BigDecimal>> target) {
        if (userIds.isEmpty()) {
            return;
        }
        List<Leave> leaves = leaveMapper.selectList(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getLeaveStatus, 2)
                .le(Leave::getStartTime, end.atTime(23, 59, 59))
                .ge(Leave::getEndTime, start.atStartOfDay()));
        for (Leave leave : leaves) {
            LocalDate from = leave.getStartTime().toLocalDate();
            LocalDate to = leave.getEndTime().toLocalDate();
            LocalDate overlapFrom = from.isBefore(start) ? start : from;
            LocalDate to2 = to.isAfter(end) ? end : to;
            if (to2.isBefore(overlapFrom)) {
                continue;
            }
            long overlapDays = Duration.between(overlapFrom.atStartOfDay(), to2.plusDays(1).atStartOfDay()).toDays();
            long spanDays = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays();
            BigDecimal total = leave.getLeaveDays() == null ? BigDecimal.valueOf(spanDays) : leave.getLeaveDays();
            BigDecimal days = spanDays <= overlapDays || spanDays <= 0
                    ? total
                    : total.multiply(BigDecimal.valueOf(overlapDays))
                    .divide(BigDecimal.valueOf(spanDays), 1, RoundingMode.HALF_UP);
            String label = LEAVE_TYPE_LABEL.getOrDefault(leave.getLeaveType(), "其他");
            target.computeIfAbsent(leave.getUserId(), k -> new LinkedHashMap<>())
                    .merge(label, days, BigDecimal::add);
        }
    }

    /** 出差天数：按自然日重叠天数累加（出差不区分假别，只影响「是否算出勤」） */
    private void aggregateTripDays(LocalDate start, LocalDate end, Set<Long> userIds,
                                   Map<Long, BigDecimal> target) {
        if (userIds.isEmpty()) {
            return;
        }
        List<BusinessTrip> trips = tripMapper.selectList(new LambdaQueryWrapper<BusinessTrip>()
                .eq(BusinessTrip::getTripStatus, 2)
                .le(BusinessTrip::getStartDate, end)
                .ge(BusinessTrip::getEndDate, start));
        for (BusinessTrip trip : trips) {
            LocalDate overlapFrom = trip.getStartDate().isBefore(start) ? start : trip.getStartDate();
            LocalDate overlapTo = trip.getEndDate().isAfter(end) ? end : trip.getEndDate();
            if (overlapTo.isBefore(overlapFrom)) {
                continue;
            }
            long days = Duration.between(overlapFrom.atStartOfDay(), overlapTo.plusDays(1).atStartOfDay()).toDays();
            target.merge(trip.getUserId(), BigDecimal.valueOf(days), BigDecimal::add);
        }
    }

    /** 加班时数：按加班类型（1 工作日 / 2 休息日 / 3 节假日）汇总，薪资按类型乘不同倍率 */
    private void aggregateOvertime(LocalDate start, LocalDate end,
                                   Map<Long, Map<String, BigDecimal>> target) {
        List<Overtime> list = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                .eq(Overtime::getOvertimeStatus, 2)
                .between(Overtime::getOvertimeDate, start, end));
        for (Overtime item : list) {
            String label = switch (item.getOvertimeType() == null ? 1 : item.getOvertimeType()) {
                case 2 -> "休息日";
                case 3 -> "节假日";
                default -> "工作日";
            };
            target.computeIfAbsent(item.getUserId(), k -> new LinkedHashMap<>())
                    .merge(label, item.getHours() == null ? BigDecimal.ZERO : item.getHours(), BigDecimal::add);
        }
    }

    /** 空值输出 null 而不是 "{}"：月汇总里没请假就是没数据，前端据此不显示该列 */
    private String toJson(Map<String, BigDecimal> values) {
        return values == null || values.isEmpty() ? null : objectMapper.writeValueAsString(values);
    }

    private void applyStatus(Attendance attendance, Shift shift, LocalDate date) {
        LocalDateTime start = LocalDateTime.of(date, shift.getWorkStart());
        LocalDateTime end = LocalDateTime.of(date, shift.getWorkEnd());
        if (!shift.getWorkEnd().isAfter(shift.getWorkStart())) {
            // 跨天班次：下班时间落在次日
            end = end.plusDays(1);
        }

        // 工作时长：两端都有才算，负值归零
        if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
            long minutes = Duration.between(attendance.getCheckInTime(), attendance.getCheckOutTime()).toMinutes()
                    - (shift.getRestMinutes() == null ? 0 : shift.getRestMinutes());
            if (minutes < 0) {
                minutes = 0;
            }
            attendance.setWorkDuration(BigDecimal.valueOf(minutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }

        if (lead(shift.getFlexible()) == 1) {
            // 弹性班：不判迟到早退，只记工时
            attendance.setAttendanceStatus(STATUS_NORMAL);
            attendance.setLateMinutes(0);
            attendance.setEarlyMinutes(0);
            attendance.setCardMissing(attendance.getCheckInTime() == null && attendance.getCheckOutTime() == null
                    ? 3 : (attendance.getCheckInTime() == null ? 1
                    : (attendance.getCheckOutTime() == null ? 2 : 0)));
            return;
        }

        int status = STATUS_NORMAL;
        int lateMinutes = 0;
        int earlyMinutes = 0;
        int missing = 0;

        if (attendance.getCheckInTime() == null) {
            missing = 1;
        } else {
            long diff = Duration.between(start, attendance.getCheckInTime()).toMinutes();
            if (diff > (shift.getLateGrace() == null ? 0 : shift.getLateGrace())) {
                lateMinutes = (int) diff;
                status = STATUS_LATE;
            }
        }
        if (attendance.getCheckOutTime() == null) {
            missing = missing == 1 ? 3 : 2;
        } else {
            long diff = Duration.between(attendance.getCheckOutTime(), end).toMinutes();
            if (diff > (shift.getEarlyGrace() == null ? 0 : shift.getEarlyGrace())) {
                earlyMinutes = (int) diff;
                if (status == STATUS_NORMAL) {
                    status = STATUS_EARLY;
                }
            }
        }
        // 两次都没打卡且当天没有任何记录 → 缺勤
        if (attendance.getCheckInTime() == null && attendance.getCheckOutTime() == null) {
            status = STATUS_ABSENT;
            missing = 3;
        }
        attendance.setAttendanceStatus(status);
        attendance.setLateMinutes(lateMinutes);
        attendance.setEarlyMinutes(earlyMinutes);
        attendance.setCardMissing(missing);
    }

    /** 数据范围 → 用户ID列表；返回 null 表示不受限（全部数据） */
    private List<Long> visibleUserIds() {
        if (hrDataScopeSupport.isUnrestricted()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        hrDataScopeSupport.applyToUser(wrapper);
        return userMapper.selectList(wrapper.select(User::getId)).stream().map(User::getId).toList();
    }

    /** 把数据范围直接拼到考勤查询上（按 user_id 收敛） */
    private void applyUserScope(LambdaQueryWrapper<Attendance> wrapper) {
        List<Long> ids = visibleUserIds();
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(Attendance::getUserId, ids);
    }

    private Attendance findAttendance(Long userId, LocalDate date) {
        return attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .eq(Attendance::getAttendanceDate, date)
                .last("LIMIT 1"));
    }

    private List<AttendanceVO> toAttendanceVOs(List<Attendance> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(list.stream()
                        .map(Attendance::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Map<Long, String> shiftNames = shiftMapper.selectList(null).stream()
                .collect(Collectors.toMap(Shift::getId, Shift::getShiftName, (a, b) -> a));

        List<AttendanceVO> vos = new ArrayList<>(list.size());
        for (Attendance a : list) {
            AttendanceVO vo = new AttendanceVO();
            vo.setId(a.getId());
            vo.setUserId(a.getUserId());
            User u = users.get(a.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptId(u.getDeptId());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
            vo.setAttendanceDate(a.getAttendanceDate());
            vo.setCheckInTime(a.getCheckInTime());
            vo.setCheckOutTime(a.getCheckOutTime());
            vo.setCheckInLocation(a.getCheckInLocation());
            vo.setCheckOutLocation(a.getCheckOutLocation());
            vo.setWorkDuration(a.getWorkDuration());
            vo.setAttendanceStatus(a.getAttendanceStatus());
            vo.setAttendanceStatusLabel(statusLabel(a.getAttendanceStatus()));
            vo.setLateMinutes(a.getLateMinutes());
            vo.setEarlyMinutes(a.getEarlyMinutes());
            vo.setCardMissing(a.getCardMissing());
            vo.setShiftName(shiftNames.get(a.getShiftId()));
            vo.setSettled(a.getSettled());
            vo.setRemark(a.getRemark());
            vos.add(vo);
        }
        return vos;
    }

    private List<AttendanceMonthVO> toMonthVOs(List<AttendanceMonth> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(list.stream()
                        .map(AttendanceMonth::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        List<AttendanceMonthVO> vos = new ArrayList<>(list.size());
        for (AttendanceMonth m : list) {
            AttendanceMonthVO vo = new AttendanceMonthVO();
            vo.setId(m.getId());
            vo.setUserId(m.getUserId());
            User u = users.get(m.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptId(u.getDeptId());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
            vo.setMonth(m.getMonth());
            vo.setShouldDays(m.getShouldDays());
            vo.setActualDays(m.getActualDays());
            vo.setLateCount(m.getLateCount());
            vo.setEarlyCount(m.getEarlyCount());
            vo.setAbsentDays(m.getAbsentDays());
            vo.setMissingCount(m.getMissingCount());
            vo.setTripDays(m.getTripDays());
            vo.setLeaveDaysJson(m.getLeaveDaysJson());
            vo.setLeaveDaysText(readable(m.getLeaveDaysJson(), "天"));
            vo.setOvertimeJson(m.getOvertimeJson());
            vo.setOvertimeText(readable(m.getOvertimeJson(), "小时"));
            vo.setStatus(m.getStatus());
            vo.setLockTime(m.getLockTime());
            vos.add(vo);
        }
        return vos;
    }

    private static YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份格式应为 yyyy-MM");
        }
    }

    private static String standardHours(Shift shift) {
        if (shift.getWorkStart() == null || shift.getWorkEnd() == null) {
            return null;
        }
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), shift.getWorkStart());
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), shift.getWorkEnd());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        long minutes = Duration.between(start, end).toMinutes()
                - (shift.getRestMinutes() == null ? 0 : shift.getRestMinutes());
        return BigDecimal.valueOf(Math.max(minutes, 0))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resettleLeaveRange(Long userId, LocalDate from, LocalDate to) {
        if (userId == null || from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        List<Attendance> stale = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .between(Attendance::getAttendanceDate, from, to)
                .eq(Attendance::getAttendanceStatus, STATUS_LEAVE)
                .isNull(Attendance::getCheckInTime)
                .isNull(Attendance::getCheckOutTime));
        for (Attendance item : stale) {
            // 逻辑删：唯一索引带 active_flag，删掉后同一天可以重新日结出新的行
            attendanceMapper.deleteById(item.getId());
        }
        // 排班置「未结算」，下一次日结才会重新判定这几天到底是正常、缺勤还是又请了假
        scheduleMapper.update(null, new LambdaUpdateWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getUserId, userId)
                .between(ShiftSchedule::getScheduleDate, from, to)
                .set(ShiftSchedule::getSettled, 0));

        // 涉及的月份汇总同样作废重算；已封账的月份不动（封账即结果不可变）
        Set<String> months = new LinkedHashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            months.add(d.format(MONTH_FORMAT));
        }
        for (String month : months) {
            monthMapper.delete(new LambdaQueryWrapper<AttendanceMonth>()
                    .eq(AttendanceMonth::getUserId, userId)
                    .eq(AttendanceMonth::getMonth, month)
                    .ne(AttendanceMonth::getStatus, 2));
        }
        log.info("销假后作废假勤记录: userId={} {}~{} 天数={}", userId, from, to, stale.size());
        return stale.size();
    }

    @Override
    public void exportMonth(String month, Long deptId, HttpServletResponse response) {
        List<AttendanceMonthVO> rows = toMonthVOs(monthMapper.selectList(deptQuery(month, deptId)));
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
                    Objects.equals(vo.getStatus(), 2) ? "已封账" : "未封账"));
        }
        ExcelExportUtil.write(response, "考勤月度汇总-" + month + ".xlsx", "月度汇总", headers, data);
    }

    /** 月度汇总查询条件（页面与导出共用，避免两处条件写得不一样） */
    private LambdaQueryWrapper<AttendanceMonth> deptQuery(String month, Long deptId) {
        LambdaQueryWrapper<AttendanceMonth> wrapper = new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, month)
                // 表里没有 dept_id（部门来自 oa_user），先按人排，导出时再按部门归组展示
                .orderByAsc(AttendanceMonth::getUserId);
        if (deptId != null) {
            List<Long> deptIds = new ArrayList<>();
            deptIds.add(deptId);
            collectChildDepts(deptId, deptIds);
            List<Long> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .in(User::getDeptId, deptIds).select(User::getId))
                    .stream().map(User::getId).toList();
            if (users.isEmpty()) {
                // 空集合不能拼 IN ()：用一个不可能成立的 id 让查询自然返回空
                wrapper.eq(AttendanceMonth::getUserId, -1L);
            } else {
                wrapper.in(AttendanceMonth::getUserId, users);
            }
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

    /**
     * 把 {"年假":2.0,"病假":1.0} 转成「年假 2 天 · 病假 1 天」。
     *
     * <p>解析失败时原样返回——展示层不能因为一条脏数据把整个列表搞崩，
     * 让运维看到原始 JSON 反而更容易定位问题。</p>
     */
    private static String readable(String json, String unit) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            if (map == null || map.isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            map.forEach((k, v) -> parts.add(k + " " + stripZero(v) + " " + unit));
            return String.join(" · ", parts);
        } catch (RuntimeException e) {
            return json;
        }
    }

    private static String stripZero(Object value) {
        if (value == null) {
            return "0";
        }
        BigDecimal decimal = new BigDecimal(String.valueOf(value)).stripTrailingZeros();
        return decimal.scale() <= 0 ? decimal.toBigInteger().toString() : decimal.toPlainString();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "迟到";
            case 3 -> "早退";
            case 4 -> "缺勤";
            case 5 -> "假勤";
            case 6 -> "出差";
            default -> "未知";
        };
    }

    private static int lead(Integer value) {
        return value == null ? 0 : value;
    }
}
