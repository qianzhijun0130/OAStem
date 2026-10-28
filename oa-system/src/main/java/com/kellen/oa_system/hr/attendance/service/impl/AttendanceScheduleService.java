package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.dto.ScheduleBatchRequest;
import com.kellen.oa_system.hr.attendance.entity.Holiday;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.HolidayMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceDates;
import com.kellen.oa_system.hr.attendance.vo.ScheduleVO;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排班：把班次铺到「部门 subtree × 日期区间」的格子上。
 *
 * <p>未排班（或休息日 shift_id 为空）的日期<b>不产生考勤记录</b>，也不判缺勤——
 * 这是本模块的第一条口径，否则新员工入职前会被算成旷工。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceScheduleService {

    private final ShiftMapper shiftMapper;
    private final HolidayMapper holidayMapper;
    private final ShiftScheduleMapper scheduleMapper;
    private final UserMapper userMapper;
    private final HrDataScopeSupport hrDataScopeSupport;

    public int batchSchedule(ScheduleBatchRequest request) {
        Shift shift = shiftMapper.selectById(request.getShiftId());
        if (shift == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        YearMonth ym = AttendanceDates.parseMonth(request.getMonth());
        LocalDate start = request.getStartDate() != null ? request.getStartDate() : ym.atDay(1);
        LocalDate end = request.getEndDate() != null ? request.getEndDate() : ym.atEndOfMonth();

        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getDeptId, hrDataScopeSupport.subtreeIds(request.getDeptId()))
                .in(User::getEmploymentStatus, AttendanceConstants.EMPLOYED));
        if (users.isEmpty()) {
            return 0;
        }
        boolean includeWeekend = request.getIncludeWeekend() != null && request.getIncludeWeekend() == 1;
        boolean overwrite = request.getOverwrite() != null && request.getOverwrite() == 1;

        Set<LocalDate> holidays = holidayMapper.selectList(new LambdaQueryWrapper<Holiday>()
                        .between(Holiday::getHolidayDate, start, end))
                .stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());

        // 循环前一次取出区间内已有排班，按「userId:date」建索引（BE-OPT-03）。
        // 原实现每格一次 selectOne：500 人 × 30 天 = 15000 次纯重复读。
        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<String, ShiftSchedule> existingMap = new HashMap<>();
        for (ShiftSchedule sc : scheduleMapper.selectList(new LambdaQueryWrapper<ShiftSchedule>()
                .in(ShiftSchedule::getUserId, userIds)
                .between(ShiftSchedule::getScheduleDate, start, end))) {
            existingMap.put(sc.getUserId() + ":" + sc.getScheduleDate(), sc);
        }

        int count = 0;
        for (User user : users) {
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                boolean weekend = date.getDayOfWeek().getValue() >= 6;
                boolean offDay = holidays.contains(date) || (weekend && !includeWeekend);
                ShiftSchedule existing = existingMap.get(user.getId() + ":" + date);
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

    public List<ScheduleVO> listSchedule(Long deptId, String month) {
        YearMonth ym = AttendanceDates.parseMonth(month);
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
}
