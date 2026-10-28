package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceAggregator;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceDates;
import com.kellen.oa_system.hr.attendance.support.AttendanceRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 日结 / 月结 / 封账。
 *
 * <p>三条口径（与《开发文档/人力资源/02》一致，改动前请先看文档）：</p>
 * <ol>
 *   <li>未排班的日期<b>不产生考勤记录</b>，也不判缺勤——否则新员工入职前会被算成旷工；</li>
 *   <li>只缺一次卡记 {@code card_missing}，<b>不判旷工</b>，等补卡输入后再重算；</li>
 *   <li>日结幂等：同一人同一天只有一行（唯一索引 + upsert），重跑不会翻倍。</li>
 * </ol>
 *
 * <p>日结的判定规则全部走 {@link AttendanceRules}，月结的假勤口径全部走
 * {@link AttendanceAggregator}——本类只负责「按批取数 + 组装 + 落库」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSettleService {

    private final ShiftMapper shiftMapper;
    private final ShiftScheduleMapper scheduleMapper;
    private final AttendanceMapper attendanceMapper;
    private final AttendanceMonthMapper monthMapper;
    private final AttendanceRules rules;
    private final AttendanceAggregator aggregator;

    public int settleDate(LocalDate date, boolean force) {
        LambdaQueryWrapper<ShiftSchedule> wrapper = new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getScheduleDate, date);
        if (!force) {
            wrapper.eq(ShiftSchedule::getSettled, 0);
        }
        return settleSchedules(scheduleMapper.selectList(wrapper), date);
    }

    /**
     * 对给定排班执行日结。
     *
     * <p>供「整天全量日结」与「单人区间重算」共用同一套判定规则——
     * 若各自实现，两边的缺勤/迟到判定稍有差异就会出现「重算结果与原结果不一致」的怪象。</p>
     */
    int settleSchedules(List<ShiftSchedule> schedules, LocalDate date) {
        if (schedules == null || schedules.isEmpty()) {
            return 0;
        }
        // 一次性把当天「已通过的请假 / 出差」查出来：日结是按人循环，逐人查库会变成 N+1
        Set<Long> onLeave = aggregator.approvedLeaveUserIds(date, date);
        Set<Long> onTrip = aggregator.approvedTripUserIds(date, date);

        // 预取班次与当天考勤（BE-OPT-03）：班次是配置数据（几十行），考勤当天每人最多一条。
        // 原实现每条排班 1 次 selectById + 1 次 findAttendance，500 人即 1000 次单行查询。
        Set<Long> shiftIds = schedules.stream().map(ShiftSchedule::getShiftId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Shift> shiftMap = new HashMap<>();
        if (!shiftIds.isEmpty()) {
            for (Shift sh : shiftMapper.selectBatchIds(shiftIds)) {
                shiftMap.put(sh.getId(), sh);
            }
        }
        Map<Long, Attendance> attMap = new HashMap<>();
        for (Attendance a : attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getAttendanceDate, date))) {
            attMap.put(a.getUserId(), a);
        }
        List<Long> settledIds = new ArrayList<>();

        int handled = 0;
        for (ShiftSchedule schedule : schedules) {
            Shift shift = schedule.getShiftId() == null ? null : shiftMap.get(schedule.getShiftId());
            if (shift == null) {
                // 休息日 / 法定节假日：不产生考勤记录，只把排班标记为已处理
                settledIds.add(schedule.getId());
                continue;
            }
            Attendance attendance = attMap.get(schedule.getUserId());
            if (attendance == null) {
                attendance = new Attendance();
                attendance.setUserId(schedule.getUserId());
                attendance.setAttendanceDate(date);
                attendance.setShiftId(shift.getId());
                rules.applyStatus(attendance, shift, date);
                rules.applyLeaveOrTrip(attendance, onLeave, onTrip);
                attendance.setSettled(1);
                attendanceMapper.insert(attendance);
            } else {
                attendance.setShiftId(shift.getId());
                rules.applyStatus(attendance, shift, date);
                rules.applyLeaveOrTrip(attendance, onLeave, onTrip);
                attendance.setSettled(1);
                attendanceMapper.updateById(attendance);
            }
            settledIds.add(schedule.getId());
            handled++;
        }
        // 排班 settled=1 合并成一条 UPDATE：原实现每条排班一次 updateById（500 次）
        if (!settledIds.isEmpty()) {
            scheduleMapper.update(null, new LambdaUpdateWrapper<ShiftSchedule>()
                    .in(ShiftSchedule::getId, settledIds)
                    .set(ShiftSchedule::getSettled, 1));
        }
        return handled;
    }

    /** 逐日执行日结：按日期分组，保证每个日期的假勤集合只查一次 */
    int settleRange(List<ShiftSchedule> schedules, LocalDate from, LocalDate to) {
        Map<LocalDate, List<ShiftSchedule>> byDate = new TreeMap<>();
        for (ShiftSchedule s : schedules) {
            byDate.computeIfAbsent(s.getScheduleDate(), k -> new ArrayList<>()).add(s);
        }
        int total = 0;
        for (Map.Entry<LocalDate, List<ShiftSchedule>> entry : byDate.entrySet()) {
            total += settleSchedules(entry.getValue(), entry.getKey());
        }
        return total;
    }

    public int settleMonth(String month) {
        YearMonth ym = AttendanceDates.parseMonth(month);
        String text = ym.format(AttendanceConstants.MONTH_FORMAT);
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
        Set<Long> onLeaveAll = aggregator.approvedLeaveUserIds(start, end);
        Set<Long> onTripAll = aggregator.approvedTripUserIds(start, end);
        Map<Long, Map<String, BigDecimal>> leaveDays = new HashMap<>();
        Map<Long, BigDecimal> tripDays = new HashMap<>();
        Map<Long, Map<String, BigDecimal>> overtimeHours = new HashMap<>();
        aggregator.aggregateLeaveDays(start, end, onLeaveAll, leaveDays);
        aggregator.aggregateTripDays(start, end, onTripAll, tripDays);
        aggregator.aggregateOvertime(start, end, overtimeHours);
        // 互斥兜底：请假/出差覆盖的日期不允许再计入旷工。
        // 即使某次审批回调漏了重算（考勤行仍是「缺勤」），月汇总也不会出现「旷工 + 请假」双重扣款。
        Map<Long, Set<LocalDate>> leaveTripDates = aggregator.collectLeaveTripDates(start, end);

        // 预取本月已有汇总（BE-OPT-03）：原实现每人一次 selectOne，500 人即 500 次
        Map<Long, AttendanceMonth> existingMap = new HashMap<>();
        for (AttendanceMonth m : monthMapper.selectList(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, text))) {
            existingMap.put(m.getUserId(), m);
        }

        int count = 0;
        for (Long userId : userIds) {
            AttendanceMonth existing = existingMap.get(userId);
            // 已封账的月份绝不覆盖：封账的意义就是「结果不能再变」
            if (existing != null && Objects.equals(existing.getStatus(), AttendanceConstants.MONTH_LOCKED)) {
                continue;
            }
            List<Attendance> mine = byUser.getOrDefault(userId, List.of());
            Set<LocalDate> covered = leaveTripDates.getOrDefault(userId, Set.of());
            BigDecimal actual = BigDecimal.ZERO;
            BigDecimal absent = BigDecimal.ZERO;
            int late = 0;
            int early = 0;
            int missing = 0;
            for (Attendance a : mine) {
                if (a.getCheckInTime() != null) {
                    actual = actual.add(BigDecimal.ONE);
                }
                if (Objects.equals(a.getAttendanceStatus(), AttendanceConstants.STATUS_ABSENT)
                        && !covered.contains(a.getAttendanceDate())) {
                    absent = absent.add(BigDecimal.ONE);
                }
                if (Objects.equals(a.getAttendanceStatus(), AttendanceConstants.STATUS_LATE)) {
                    late++;
                }
                if (Objects.equals(a.getAttendanceStatus(), AttendanceConstants.STATUS_EARLY)) {
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
            entity.setLeaveDaysJson(aggregator.toJson(leaveDays.get(userId)));
            entity.setOvertimeJson(aggregator.toJson(overtimeHours.get(userId)));
            entity.setTripDays(tripDays.getOrDefault(userId, BigDecimal.ZERO));
            entity.setStatus(AttendanceConstants.MONTH_OPEN);
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

    /**
     * 整月重算：逐日重结 + 月结汇总。
     *
     * <p><b>事务边界说明（勿按注解误判）</b>：本方法内的 {@code settleDate(...)} 与
     * {@code settleMonth(...)} 是同类的普通方法调用，它们各自的事务注解属于
     * <b>自调用</b>，Spring 代理不介入、注解<b>完全不生效</b>——
     * 实际只有一个事务，就是门面这一层。因此重算要么整体成功、要么整体回滚，
     * 不存在「某天单独提交」的中间态。</p>
     *
     * <p>后续若要拆成多事务（例如按天分段提交以缩短锁窗口），必须改为通过注入的代理
     * 或 {@code TransactionTemplate} 调用，否则改了注解也不会生效。</p>
     */
    public int rebuild(String month) {
        YearMonth ym = AttendanceDates.parseMonth(month);
        int days = 0;
        for (LocalDate date = ym.atDay(1); !date.isAfter(ym.atEndOfMonth()); date = date.plusDays(1)) {
            days += settleDate(date, true);
        }
        int users = settleMonth(month);
        log.info("考勤重算完成: month={} days={} users={}", month, days, users);
        return days + users;
    }

}
