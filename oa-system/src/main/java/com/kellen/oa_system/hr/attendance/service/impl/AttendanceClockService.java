package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.dto.ClockRequest;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceRules;
import com.kellen.oa_system.hr.attendance.vo.TodayAttendanceVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡与「今日考勤」。
 *
 * <p>打卡只做两件事：写时间点、按当前班次重算当天状态。判缺勤/迟到早退的规则全在
 * {@link AttendanceRules}，这里不重复实现——否则补卡重算与打卡会得出两种结论。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceClockService {

    private final ShiftScheduleMapper scheduleMapper;
    private final ShiftMapper shiftMapper;
    private final AttendanceMapper attendanceMapper;
    private final AttendanceRules rules;

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
            vo.setAttendanceStatusLabel(AttendanceRules.statusLabel(attendance.getAttendanceStatus()));
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
            rules.applyStatus(attendance, shift, date);
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
        rules.applyStatus(attendance, shift, date);
        attendanceMapper.updateById(attendance);
    }

    /** 当天该员工的考勤行（唯一索引保证最多一条） */
    public Attendance findAttendance(Long userId, LocalDate date) {
        return attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .eq(Attendance::getAttendanceDate, date)
                .last("LIMIT 1"));
    }
}
