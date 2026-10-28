package com.kellen.oa_system.hr.attendance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.BusinessTrip;
import com.kellen.oa_system.hr.attendance.entity.Overtime;
import com.kellen.oa_system.hr.attendance.mapper.BusinessTripMapper;
import com.kellen.oa_system.hr.attendance.mapper.OvertimeMapper;
import com.kellen.oa_system.hr.leave.entity.Leave;
import com.kellen.oa_system.hr.leave.mapper.LeaveMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 月汇总的假勤口径：谁在请假/出差、每人被覆盖了哪些日期、假别天数/出差天数/加班时数。
 *
 * <p>这些数直接进 {@code oa_attendance_month}，再被薪资当扣款依据。所以必须由考勤侧固化，
 * 不能让薪资模块自己去猜——两边算法一有差异就对不上账。</p>
 *
 * <p>每个方法都是「一次查询 + 内存归并」，调用方不要放进按人循环里。</p>
 */
@Component
@RequiredArgsConstructor
public class AttendanceAggregator {

    private final LeaveMapper leaveMapper;
    private final BusinessTripMapper tripMapper;
    private final OvertimeMapper overtimeMapper;
    private final ObjectMapper objectMapper;

    /** 区间内「存在已通过请假」的用户集合（一次查询，供日结/月结复用） */
    public Set<Long> approvedLeaveUserIds(LocalDate start, LocalDate end) {
        return approvedLeaves(start, end).stream().map(Leave::getUserId).collect(Collectors.toSet());
    }

    /** 区间内「存在已通过出差」的用户集合 */
    public Set<Long> approvedTripUserIds(LocalDate start, LocalDate end) {
        return approvedTrips(start, end).stream().map(BusinessTrip::getUserId).collect(Collectors.toSet());
    }

    /**
     * 收集区间内每人被「已通过请假/出差」覆盖的日期集合。
     *
     * <p>用途是月结的互斥兜底：考勤行可能因为审批晚于日结而残留「缺勤」，
     * 但只要当天在请假或出差区间内，就不允许再计入 {@code absent_days}，
     * 否则薪资会按「旷工 2 倍 + 假别」重复扣款。</p>
     */
    public Map<Long, Set<LocalDate>> collectLeaveTripDates(LocalDate start, LocalDate end) {
        Map<Long, Set<LocalDate>> dates = new HashMap<>();
        for (Leave leave : approvedLeaves(start, end)) {
            LocalDate from = leave.getStartTime().toLocalDate();
            LocalDate to = leave.getEndTime().toLocalDate();
            LocalDate overlapFrom = from.isBefore(start) ? start : from;
            LocalDate overlapTo = to.isAfter(end) ? end : to;
            for (LocalDate d = overlapFrom; !d.isAfter(overlapTo); d = d.plusDays(1)) {
                dates.computeIfAbsent(leave.getUserId(), k -> new HashSet<>()).add(d);
            }
        }
        for (BusinessTrip trip : approvedTrips(start, end)) {
            LocalDate overlapFrom = trip.getStartDate().isBefore(start) ? start : trip.getStartDate();
            LocalDate overlapTo = trip.getEndDate().isAfter(end) ? end : trip.getEndDate();
            for (LocalDate d = overlapFrom; !d.isAfter(overlapTo); d = d.plusDays(1)) {
                dates.computeIfAbsent(trip.getUserId(), k -> new HashSet<>()).add(d);
            }
        }
        return dates;
    }

    /**
     * 按假别汇总本月天数。
     *
     * <p>跨月的请假单按「重叠天数 / 总跨度天数 × 请假天数」折算，
     * 这样 9/29~10/3 的 5 天假在两个月的月汇总里各占自己的部分，合计仍等于 5 天。</p>
     */
    public void aggregateLeaveDays(LocalDate start, LocalDate end, Set<Long> userIds,
                                   Map<Long, Map<String, BigDecimal>> target) {
        if (userIds.isEmpty()) {
            return;
        }
        for (Leave leave : approvedLeaves(start, end)) {
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
            String label = AttendanceConstants.LEAVE_TYPE_LABEL.getOrDefault(leave.getLeaveType(), "其他");
            target.computeIfAbsent(leave.getUserId(), k -> new LinkedHashMap<>())
                    .merge(label, days, BigDecimal::add);
        }
    }

    /** 出差天数：按自然日重叠天数累加（出差不区分假别，只影响「是否算出勤」） */
    public void aggregateTripDays(LocalDate start, LocalDate end, Set<Long> userIds,
                                  Map<Long, BigDecimal> target) {
        if (userIds.isEmpty()) {
            return;
        }
        for (BusinessTrip trip : approvedTrips(start, end)) {
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
    public void aggregateOvertime(LocalDate start, LocalDate end,
                                  Map<Long, Map<String, BigDecimal>> target) {
        List<Overtime> list = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                .eq(Overtime::getOvertimeStatus, AttendanceConstants.OVERTIME_APPROVED)
                .between(Overtime::getOvertimeDate, start, end));
        for (Overtime item : list) {
            String label = switch (item.getOvertimeType() == null ? 1 : item.getOvertimeType()) {
                case 2 -> AttendanceConstants.OVERTIME_RESTDAY;
                case 3 -> AttendanceConstants.OVERTIME_HOLIDAY;
                default -> AttendanceConstants.OVERTIME_WORKDAY;
            };
            target.computeIfAbsent(item.getUserId(), k -> new LinkedHashMap<>())
                    .merge(label, item.getHours() == null ? BigDecimal.ZERO : item.getHours(), BigDecimal::add);
        }
    }

    /** 空值输出 null 而不是 "{}"：月汇总里没请假就是没数据，前端据此不显示该列 */
    public String toJson(Map<String, BigDecimal> values) {
        return values == null || values.isEmpty() ? null : objectMapper.writeValueAsString(values);
    }

    private List<Leave> approvedLeaves(LocalDate start, LocalDate end) {
        return leaveMapper.selectList(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getLeaveStatus, AttendanceConstants.LEAVE_APPROVED)
                .le(Leave::getStartTime, end.atTime(23, 59, 59))
                .ge(Leave::getEndTime, start.atStartOfDay()));
    }

    private List<BusinessTrip> approvedTrips(LocalDate start, LocalDate end) {
        return tripMapper.selectList(new LambdaQueryWrapper<BusinessTrip>()
                .eq(BusinessTrip::getTripStatus, AttendanceConstants.TRIP_APPROVED)
                .le(BusinessTrip::getStartDate, end)
                .ge(BusinessTrip::getEndDate, start));
    }
}
