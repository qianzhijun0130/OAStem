package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceDates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 假勤审批终态后的重算（BE-OPT-01）。
 *
 * <p>四件事：把「已结算」标记作废、按区间重结、审批提交后触发、销假后清除假勤行。</p>
 *
 * <p><b>为什么单独成类</b>：这四个入口由审批回调 SPI 与销假流程驱动，
 * 与「人主动去点日结」是两条完全不同的触发路径；混在一起时，
 * 排查「审批通过了但考勤没变」要在几千行里找哪条路径漏了调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecalcService {

    private final ShiftScheduleMapper scheduleMapper;
    private final AttendanceMapper attendanceMapper;
    private final AttendanceMonthMapper monthMapper;
    private final AttendanceSettleService settleService;

    public int invalidateSettlement(Long userId, LocalDate from, LocalDate to) {
        if (userId == null || from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        // 两侧都要置 0：settleDate(force=false) 以排班 settled 为判据，
        // 而考勤行的 settled 表示「结果是否已结算」，只改一侧会出现「重算了但没覆盖」的中间态
        int affected = scheduleMapper.update(null, new LambdaUpdateWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getUserId, userId)
                .between(ShiftSchedule::getScheduleDate, from, to)
                .set(ShiftSchedule::getSettled, 0));
        attendanceMapper.update(null, new LambdaUpdateWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .between(Attendance::getAttendanceDate, from, to)
                .set(Attendance::getSettled, 0));
        return affected;
    }

    public int resettle(Long userId, LocalDate from, LocalDate to) {
        if (userId == null || from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        assertMonthNotLocked(from, to);
        invalidateSettlement(userId, from, to);
        return settleService.settleRange(loadSchedules(userId, from, to), from, to);
    }

    /**
     * 事务提交后重算，避免把重算的锁窗口留在审批事务里。
     *
     * <p>注意：本方法本身不带事务，{@code afterCommit} 里执行时也不会有事务——
     * 此前 {@code resettle} 的注解因同对象自调用而从未生效，行为一致。
     * 若将来要让它整体回滚，需改为经代理调用或 {@code TransactionTemplate}。</p>
     */
    public void resettleAfterCommit(Long userId, LocalDate from, LocalDate to) {
        Runnable task = () -> {
            try {
                resettle(userId, from, to);
            } catch (Exception e) {
                // 提交后失败不回滚审批，但必须留下可追溯线索。
                // 月结侧有「请假/出差当天不计旷工」的互斥兜底，单次失败不会再造成重复扣款。
                log.error("审批后重算考勤失败，请手工执行日结重算: userId={} {}~{}", userId, from, to, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 销假后作废区间内的假勤行并重算。
     *
     * <p>与 {@link #resettle} 的区别：这里要先把「因请假而产生的考勤行」删掉再重结，
     * 否则重结只会把同一行改回「假勤」，销假永远不生效。</p>
     */
    public int resettleLeaveRange(Long userId, LocalDate from, LocalDate to) {
        if (userId == null || from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        List<Attendance> stale = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, userId)
                .between(Attendance::getAttendanceDate, from, to)
                .eq(Attendance::getAttendanceStatus, AttendanceConstants.STATUS_LEAVE)
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
            months.add(AttendanceDates.monthOf(d));
        }
        for (String month : months) {
            monthMapper.delete(new LambdaQueryWrapper<AttendanceMonth>()
                    .eq(AttendanceMonth::getUserId, userId)
                    .eq(AttendanceMonth::getMonth, month)
                    .ne(AttendanceMonth::getStatus, AttendanceConstants.MONTH_LOCKED));
        }
        log.info("销假后作废假勤记录: userId={} {}~{} 天数={}", userId, from, to, stale.size());
        return stale.size();
    }

    private List<ShiftSchedule> loadSchedules(Long userId, LocalDate from, LocalDate to) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getUserId, userId)
                .between(ShiftSchedule::getScheduleDate, from, to));
    }

    /**
     * 区间内任一月已封账则直接失败。
     *
     * <p>封账的意义是「结果不能再变」；静默跳过会让「审批已通过、考勤没变」一路带到工资单，
     * 所以这里显式抛错，让审批人知道要走 {@code unlockMonth} 的解锁流程。</p>
     */
    private void assertMonthNotLocked(LocalDate from, LocalDate to) {
        Set<String> months = new LinkedHashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            months.add(AttendanceDates.monthOf(d));
        }
        for (String month : months) {
            Long locked = monthMapper.selectCount(new LambdaQueryWrapper<AttendanceMonth>()
                    .eq(AttendanceMonth::getMonth, month)
                    .eq(AttendanceMonth::getStatus, AttendanceConstants.MONTH_LOCKED));
            if (locked != null && locked > 0) {
                throw new BizException(ErrorCode.HR_ATTENDANCE_LOCKED,
                        month + " 考勤已封账，假勤变更需先由超级管理员解封后再重算");
            }
        }
    }
}
