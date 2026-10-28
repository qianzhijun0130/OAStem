package com.kellen.oa_system.hr.attendance.support;

import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 考勤判定规则：迟到/早退/缺卡/缺勤，以及假勤出差的状态覆盖。
 *
 * <p><b>这里是全模块唯一允许出现「判定」的地方。</b>日结、重算、打卡三条路径共用它——
 * 若各自实现，两边的缺勤/迟到判定稍有差异就会出现「重算结果与原结果不一致」的怪象
 * （同一个人同一天，日结说缺卡、重算说缺勤），而且几乎无法排查。</p>
 */
@Component
public class AttendanceRules {

    /**
     * 根据打卡情况计算状态、迟到/早退分钟与缺卡标记。
     *
     * <p><b>只缺一次卡不判缺勤</b>：标 {@code card_missing} 并落正常/迟到/早退，
     * 等补卡通过后重算。把「少打一次卡」直接算旷工会误扣工资，是最常见的实现错误。</p>
     */
    public void applyStatus(Attendance attendance, Shift shift, LocalDate date) {
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
            attendance.setAttendanceStatus(AttendanceConstants.STATUS_NORMAL);
            attendance.setLateMinutes(0);
            attendance.setEarlyMinutes(0);
            attendance.setCardMissing(attendance.getCheckInTime() == null && attendance.getCheckOutTime() == null
                    ? 3 : (attendance.getCheckInTime() == null ? 1
                    : (attendance.getCheckOutTime() == null ? 2 : 0)));
            return;
        }

        int status = AttendanceConstants.STATUS_NORMAL;
        int lateMinutes = 0;
        int earlyMinutes = 0;
        int missing = 0;

        if (attendance.getCheckInTime() == null) {
            missing = 1;
        } else {
            long diff = Duration.between(start, attendance.getCheckInTime()).toMinutes();
            if (diff > (shift.getLateGrace() == null ? 0 : shift.getLateGrace())) {
                lateMinutes = (int) diff;
                status = AttendanceConstants.STATUS_LATE;
            }
        }
        if (attendance.getCheckOutTime() == null) {
            missing = missing == 1 ? 3 : 2;
        } else {
            long diff = Duration.between(attendance.getCheckOutTime(), end).toMinutes();
            if (diff > (shift.getEarlyGrace() == null ? 0 : shift.getEarlyGrace())) {
                earlyMinutes = (int) diff;
                if (status == AttendanceConstants.STATUS_NORMAL) {
                    status = AttendanceConstants.STATUS_EARLY;
                }
            }
        }
        // 两次都没打卡且当天没有任何记录 → 缺勤
        if (attendance.getCheckInTime() == null && attendance.getCheckOutTime() == null) {
            status = AttendanceConstants.STATUS_ABSENT;
            missing = 3;
        }
        attendance.setAttendanceStatus(status);
        attendance.setLateMinutes(lateMinutes);
        attendance.setEarlyMinutes(earlyMinutes);
        attendance.setCardMissing(missing);
    }

    /**
     * 已通过的请假/出差覆盖当天考勤状态。
     *
     * <p><b>为什么必须覆盖</b>：请假当天通常没有打卡记录，若按常规逻辑会判成「缺勤 + 缺卡」，
     * 于是薪资既扣旷工又扣满勤，员工请一天假被扣两次。
     * 出差同理——出差期间本来就不该打卡。</p>
     *
     * <p>请假优先于出差：两者时间重叠时以请假为准（请假要扣额度，是更强的事实）。</p>
     */
    public void applyLeaveOrTrip(Attendance attendance, Set<Long> onLeave, Set<Long> onTrip) {
        Long userId = attendance.getUserId();
        boolean leave = onLeave.contains(userId);
        boolean trip = !leave && onTrip.contains(userId);
        if (!leave && !trip) {
            return;
        }
        attendance.setAttendanceStatus(leave
                ? AttendanceConstants.STATUS_LEAVE : AttendanceConstants.STATUS_TRIP);
        // 假勤/出差不产生迟到、早退、缺卡，也不计工时——全部由当天假勤单解释
        attendance.setLateMinutes(0);
        attendance.setEarlyMinutes(0);
        attendance.setCardMissing(0);
        attendance.setWorkDuration(BigDecimal.ZERO);
    }

    /**
     * 班的应出勤小时数（扣除休息时长）。
     *
     * <p>只用于前端展示，取任意一天作基准；跨天班次按次日折算。</p>
     */
    public static String standardHours(Shift shift) {
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

    public static String statusLabel(Integer status) {
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
