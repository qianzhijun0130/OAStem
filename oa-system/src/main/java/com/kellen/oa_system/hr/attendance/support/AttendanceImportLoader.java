package com.kellen.oa_system.hr.attendance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceMachineParser.Punch;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 考勤机导入的取数层。
 *
 * <p>它存在的唯一目的是让导入的写入循环<b>物理上查不了库</b>：一次导入最多 2 万行、
 * 折算 5000 个人天，若有人在逐行处理里补一句 {@code selectOne}，就变成 5000 次往返，
 * 一万人规模的公司导入一次要几分钟。把这四个查询集中在这里，循环体只能拿到已经取好的 Map，
 * 加不进去——比在注释里写「请勿在循环内查库」有用得多。</p>
 *
 * <p>四个查询各一次：员工（按工号）、考勤（按人 + 日期区间）、排班（按人 + 日期区间）、班次（全表）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceImportLoader {

    private final AttendanceMapper attendanceMapper;
    private final ShiftScheduleMapper scheduleMapper;
    private final ShiftMapper shiftMapper;
    private final AttendanceMonthMapper monthMapper;
    private final UserMapper userMapper;

    /** 工号 → 员工。工号是考勤机的唯一标识，查不到的工号在调用方逐行报错 */
    public Map<String, User> loadUsers(List<Punch> punches) {
        Set<String> workNumbers = punches.stream().map(Punch::workNumber)
                .filter(n -> !n.isBlank()).collect(Collectors.toSet());
        if (workNumbers.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getWorkNumber, workNumbers));
        Map<String, User> map = new HashMap<>();
        for (User u : users) {
            map.put(u.getWorkNumber(), u);
        }
        return map;
    }

    /**
     * 只要有任意一行的月份已封账，整批拒绝。
     *
     * <p>封账的语义是「结果不可再变」——导入若悄悄改掉已封账月份的数据，
     * 财务签过字的工资台账就与系统不一致了。宁可整批失败并说清是哪个月。</p>
     */
    public void assertNoLockedMonth(Set<String> dayKeys) {
        Set<String> months = new HashSet<>();
        for (String key : dayKeys) {
            // key = 工号|yyyy-MM-dd
            int sep = key.indexOf('|');
            if (sep > 0 && key.length() >= sep + 8) {
                months.add(key.substring(sep + 1, sep + 8));
            }
        }
        if (months.isEmpty()) {
            return;
        }
        Long locked = monthMapper.selectCount(new LambdaQueryWrapper<AttendanceMonth>()
                .in(AttendanceMonth::getMonth, months)
                .eq(AttendanceMonth::getStatus, AttendanceConstants.MONTH_LOCKED));
        if (locked != null && locked > 0) {
            throw new BizException(ErrorCode.HR_ATTENDANCE_IMPORT_LOCKED,
                    "涉及月份 " + new TreeSet<>(months) + " 中有已封账数据，请先由超级管理员解封");
        }
    }

    /** 键为 {@code 用户id|日期} */
    public Map<String, Attendance> loadAttendances(List<Long> userIds, LocalDate from, LocalDate to) {
        List<Attendance> list = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .in(Attendance::getUserId, userIds)
                .between(Attendance::getAttendanceDate, from, to));
        Map<String, Attendance> map = new HashMap<>();
        for (Attendance a : list) {
            map.put(a.getUserId() + "|" + a.getAttendanceDate(), a);
        }
        return map;
    }

    /** 键为 {@code 用户id|日期} */
    public Map<String, ShiftSchedule> loadSchedules(List<Long> userIds, LocalDate from, LocalDate to) {
        List<ShiftSchedule> list = scheduleMapper.selectList(new LambdaQueryWrapper<ShiftSchedule>()
                .in(ShiftSchedule::getUserId, userIds)
                .between(ShiftSchedule::getScheduleDate, from, to));
        Map<String, ShiftSchedule> map = new HashMap<>();
        for (ShiftSchedule s : list) {
            map.put(s.getUserId() + "|" + s.getScheduleDate(), s);
        }
        return map;
    }

    /** 班次是配置数据（几十行），一次全取 */
    public Map<Long, Shift> loadShifts() {
        List<Shift> list = shiftMapper.selectList(new LambdaQueryWrapper<>());
        if (list.isEmpty()) {
            log.warn("考勤机导入：班次表为空，所有行都会因「班次已被删除」被拒");
        }
        return list.stream().collect(Collectors.toMap(Shift::getId, s -> s, (a, b) -> a));
    }
}
