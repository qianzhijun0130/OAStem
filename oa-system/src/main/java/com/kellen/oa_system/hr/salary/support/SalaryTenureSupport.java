package com.kellen.oa_system.hr.salary.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.employee.entity.EmployeeChange;
import com.kellen.oa_system.hr.employee.mapper.EmployeeChangeMapper;
import com.kellen.oa_system.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 在职期间相关的两个口径：入离职当月折算系数、个税累计减除费用月数。
 *
 * <p>两者都依赖「员工在本单位的起止日」，且都以 {@code entry_date} + 「离职」异动为准。
 * 放在同一个支撑类，是为了让「本年度在本单位待了多久」只有一个答案——</p>
 *
 * <p>这两项都是 BIZ-OPT-04 引入的，各有开关：{@code hr.salary.proration.enabled}
 * 关闭即恢复「发整月」，{@code hr.salary.tax.month-source=calendar} 恢复旧的按自然月减除。</p>
 */
@Component
@RequiredArgsConstructor
public class SalaryTenureSupport {

    private final EmployeeChangeMapper changeMapper;
    private final ShiftScheduleMapper scheduleMapper;

    /** 入职/离职当月是否按在职天数折算标准工资（BIZ-OPT-04 第 1 项）。关闭即恢复「发整月」。 */
    @Value("${hr.salary.proration.enabled:true}")
    private boolean prorationEnabled;

    /**
     * 个税累计减除费用的月数口径（BIZ-OPT-04 第 2 项）。
     * {@code tenure}=本年度在本单位的任职月数（正确）；{@code calendar}=自然月序号（旧行为，仅供回滚）。
     */
    @Value("${hr.salary.tax.month-source:tenure}")
    private String taxMonthSource;

    /** 离职生效日：该员工最近一条「离职」异动的生效日期；无记录返回 null */
    public LocalDate resolveExitDate(Long userId, LocalDate at) {
        EmployeeChange change = changeMapper.selectOne(new LambdaQueryWrapper<EmployeeChange>()
                .eq(EmployeeChange::getUserId, userId)
                .eq(EmployeeChange::getChangeType, SalaryConstants.CHANGE_TYPE_RESIGN)
                .le(EmployeeChange::getEffectiveDate, at)
                .orderByDesc(EmployeeChange::getEffectiveDate)
                .orderByDesc(EmployeeChange::getId)
                .last("LIMIT 1"));
        return change == null ? null : change.getEffectiveDate();
    }

    /**
     * 在职天数折算系数（0~1）。
     *
     * <p>口径：入职当月按「入职日（含）至月末」、离职当月按「月初至离职日（含）」的实际应出勤天数
     * 除以当月应出勤天数。应出勤天数取排班表中该员工有班次的天数；没有排班时回退为当月工作日（周一至周五）。
     * 两者都不在当月则返回 1（不折算）。</p>
     */
    public BigDecimal prorationFactor(User user, LocalDate start, LocalDate end) {
        if (!prorationEnabled) {
            return BigDecimal.ONE;
        }
        LocalDate entry = user.getEntryDate();
        LocalDate exit = resolveExitDate(user.getId(), end);
        boolean entryIn = entry != null && !entry.isBefore(start) && !entry.isAfter(end);
        boolean exitIn = exit != null && !exit.isBefore(start) && !exit.isAfter(end);
        if (!entryIn && !exitIn) {
            return BigDecimal.ONE;
        }
        LocalDate from = entryIn ? entry : start;
        LocalDate to = exitIn ? exit : end;
        if (to.isBefore(from)) {
            return BigDecimal.ZERO;
        }
        int should = workDays(user.getId(), start, end);
        int actual = workDays(user.getId(), from, to);
        if (should <= 0) {
            should = weekdaysBetween(start, end);
            actual = weekdaysBetween(from, to);
        }
        if (should <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal factor = BigDecimal.valueOf(Math.min(actual, should))
                .divide(BigDecimal.valueOf(should), 4, RoundingMode.HALF_UP);
        return factor.min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    /**
     * 个税累计减除费用对应的月数。
     *
     * <p>{@code tenure}（默认）：本年度在本单位的任职月数，含入职当月；
     * {@code calendar}：自然月序号（旧行为，仅用于回滚对比）。</p>
     */
    public int taxMonthCount(User user, int year, int monthNo) {
        if (!"tenure".equalsIgnoreCase(taxMonthSource)) {
            return monthNo;
        }
        LocalDate entry = user.getEntryDate();
        if (entry == null) {
            return monthNo;
        }
        if (entry.getYear() < year) {
            return monthNo;
        }
        if (entry.getYear() > year) {
            return 0;
        }
        return Math.max(0, Math.min(monthNo - entry.getMonthValue() + 1, monthNo));
    }

    /** 备注：绩效口径 + 折算说明。折算必须可追溯，否则员工看不懂为什么应发少了 */
    public String buildRemark(String perfNote, BigDecimal factor, BigDecimal standard, BigDecimal prorated) {
        StringBuilder sb = new StringBuilder(perfNote == null ? "" : perfNote);
        if (factor != null && factor.compareTo(BigDecimal.ONE) != 0) {
            sb.append("；在职天数折算系数 ").append(factor.stripTrailingZeros().toPlainString())
                    .append("（标准工资 ").append(standard.toPlainString())
                    .append(" → ").append(prorated.toPlainString()).append("）");
        }
        return sb.toString();
    }

    /** 排班表里该员工区间内「有班次」的天数（休息日 shift_id 为空，不构成应出勤） */
    private int workDays(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        return scheduleMapper.selectList(new LambdaQueryWrapper<ShiftSchedule>()
                        .eq(ShiftSchedule::getUserId, userId)
                        .between(ShiftSchedule::getScheduleDate, from, to)
                        .isNotNull(ShiftSchedule::getShiftId)
                        .select(ShiftSchedule::getId))
                .size();
    }

    /** 无排班时的回退口径：周一至周五 */
    private int weekdaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }
}
