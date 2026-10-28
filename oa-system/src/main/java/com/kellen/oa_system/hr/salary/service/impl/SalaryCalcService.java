package com.kellen.oa_system.hr.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.performance.service.PerfScoreService;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.entity.SocialPolicy;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordMapper;
import com.kellen.oa_system.hr.salary.support.SalaryAmounts;
import com.kellen.oa_system.hr.salary.support.SalaryCalculator;
import com.kellen.oa_system.hr.salary.support.SalaryItemWriter;
import com.kellen.oa_system.hr.salary.support.SalaryConstants;
import com.kellen.oa_system.hr.salary.support.SalaryQueryContext;
import com.kellen.oa_system.hr.salary.support.SalaryTenureSupport;
import com.kellen.oa_system.system.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 月度核算：整月一次、全员一遍。
 *
 * <p>三段式，顺序不可换：<b>先拦口径</b>（档案缺失、考勤未封账都是硬阻断）→
 * <b>批量预取</b>（档案/政策/绩效系数/累计数/已有记录各取一次）→ <b>逐人算并落明细</b>。
 * 中间任何一步提前或按人查库，都会把「一次核算」变成几千次往返。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryCalcService {

    private final SalaryRecordMapper recordMapper;
    private final AttendanceMonthMapper attendanceMonthMapper;
    private final SalaryItemWriter itemWriter;
    private final PerfScoreService perfScoreService;
    private final SalaryQueryContext queryContext;
    private final SalaryTenureSupport tenureSupport;

    public int calculate(String month, Long deptId) {
        YearMonth ym = SalaryAmounts.parseMonth(month);
        String text = ym.format(SalaryConstants.MONTH_FORMAT);
        LocalDate end = ym.atEndOfMonth();
        int year = ym.getYear();
        int monthNo = ym.getMonthValue();

        List<User> users = queryContext.payableUsers(deptId, ym.atDay(1), end);
        if (users.isEmpty()) {
            throw new BizException(ErrorCode.HR_SALARY_NO_DATA);
        }
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());

        // 1) 每人的生效档案（effective_date <= 月末 的最新一条）
        Map<Long, SalaryProfile> profiles = queryContext.latestProfiles(userIds, end);
        List<String> missing = users.stream()
                .filter(u -> !profiles.containsKey(u.getId()))
                .map(User::getRealName).toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.HR_SALARY_PROFILE_MISSING,
                    "以下员工没有生效薪资档案：" + String.join("、", missing));
        }

        // 2) 考勤月结必须存在且已封账：否则考勤一改，工资就对不上
        Map<Long, AttendanceMonth> attendances = attendanceMonthMapper.selectList(
                        new LambdaQueryWrapper<AttendanceMonth>().eq(AttendanceMonth::getMonth, text))
                .stream().collect(Collectors.toMap(AttendanceMonth::getUserId, a -> a, (a, b) -> a));
        // 没有月结记录 = 当月从未跑过日结。此前这种员工被静默按全勤发放，
        // 一次漏跑月结就等于全公司多发一个月（BIZ-OPT-04 第 3 项）。
        List<String> noAttendance = users.stream()
                .filter(u -> !attendances.containsKey(u.getId()))
                .map(User::getRealName).toList();
        if (!noAttendance.isEmpty()) {
            throw new BizException(ErrorCode.HR_SALARY_ATTENDANCE_UNLOCKED,
                    "以下员工缺少 " + text + " 考勤汇总，请先执行日结与月结后再核算："
                            + String.join("、", noAttendance));
        }

        List<String> unlocked = users.stream()
                .filter(u -> attendances.containsKey(u.getId()))
                .filter(u -> !Objects.equals(attendances.get(u.getId()).getStatus(),
                        SalaryConstants.MONTH_LOCKED_STATUS))
                .map(User::getRealName).toList();
        if (!unlocked.isEmpty()) {
            throw new BizException(ErrorCode.HR_SALARY_ATTENDANCE_UNLOCKED,
                    "以下员工 " + text + " 的考勤尚未封账：" + String.join("、", unlocked));
        }

        // 3) 本年已确定的累计数（已复核及以后），用于累计预扣个税
        Map<Long, BigDecimal[]> cumulative = queryContext.cumulativeBefore(text, year);

        // ---- 循环前批量预取（BE-OPT-04）：把「每用户固定开销」从 ~17 条 SQL 压到 2 条 ----
        Map<String, SocialPolicy> policies = queryContext.loadPolicies(year);
        Map<Long, BigDecimal> coeffMap = perfScoreService.coefficientMap(userIds, text);
        Map<Long, SalaryRecord> existingMap = recordMapper.selectList(new LambdaQueryWrapper<SalaryRecord>()
                        .eq(SalaryRecord::getMonth, text)
                        .in(SalaryRecord::getUserId, userIds))
                .stream().collect(Collectors.toMap(SalaryRecord::getUserId, r -> r, (a, b) -> a));

        int count = 0;
        for (User user : users) {
            SalaryProfile profile = profiles.get(user.getId());
            SalaryRecord existing = existingMap.get(user.getId());
            // 已复核之后的结果不允许被重算覆盖：复核过的数字是要发给员工的
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() >= SalaryConstants.STATUS_REVIEWED) {
                continue;
            }
            SalaryRecord record = existing == null ? new SalaryRecord() : existing;
            fillRecord(record, user, profile, attendances.get(user.getId()), text, year, monthNo,
                    policies, ym.atDay(1), end, coeffMap.get(user.getId()),
                    cumulative.getOrDefault(user.getId(), SalaryAmounts.zeroCumulative()));
            if (record.getId() == null) {
                recordMapper.insert(record);
            } else {
                recordMapper.updateById(record);
            }
            itemWriter.rebuild(record);
            count++;
        }
        log.info("薪资核算完成: month={} users={} scopeDept={}", text, count, deptId);
        return count;
    }

    /**
     * 组装一条核算记录。
     *
     * <p>顺序不能变：先算「应发」→ 再定「社保基数」（基数依赖应发里计入社保的项）
     * → 再算社保与个税 → 最后算实发。任何一步提前都会用到错的口径。</p>
     */
    private void fillRecord(SalaryRecord record, User user, SalaryProfile profile,
                            AttendanceMonth attendance, String month, int year, int monthNo,
                            Map<String, SocialPolicy> policies,
                            LocalDate monthStart, LocalDate monthEnd,
                            BigDecimal perfCoefficient,
                            BigDecimal[] cumulative) {
        SocialPolicy policy = queryContext.requirePolicy(policies, profile.getCity(), year);

        BigDecimal base = SalaryCalculator.scale(profile.getBaseSalary());
        BigDecimal post = SalaryCalculator.scale(profile.getPostSalary());
        // 绩效工资 = 档案里的绩效基数 × 当期绩效系数；没有归档结果时按 1.0 并在备注标注，
        // 绝不因为「绩效还没归档」就把整月工资卡住不发
        // 系数由调用方批量预取后传入（原先每人查两次：周期列表 + 考核表）
        String perfNote = perfCoefficient == null
                ? "绩效系数按 1.0（本期无归档绩效）"
                : "绩效系数 " + perfCoefficient.stripTrailingZeros().toPlainString();
        BigDecimal perf = SalaryCalculator.scale(SalaryAmounts.nz(profile.getPerfSalary())
                .multiply(Objects.requireNonNullElse(perfCoefficient, BigDecimal.ONE)));
        BigDecimal allow = SalaryCalculator.scale(profile.getAllowance());
        BigDecimal standard = base.add(post).add(perf).add(allow);

        // 入职/离职当月按实际在职天数折算标准工资（BIZ-OPT-04 第 1 项）。
        // 只折算「标准工资」这一项：社保按整月缴纳、考勤扣款仍按未折算的日工资口径，
        // 否则入离职当天的缺勤扣款会跟着变小，反而制造新的口径错误。
        BigDecimal prorationFactor = tenureSupport.prorationFactor(user, monthStart, monthEnd);
        BigDecimal proratedStandard = SalaryCalculator.scale(standard.multiply(prorationFactor));

        Map<String, BigDecimal> leaveDays = queryContext.readJson(
                attendance == null ? null : attendance.getLeaveDaysJson());
        Map<String, BigDecimal> overtime = queryContext.readJson(
                attendance == null ? null : attendance.getOvertimeJson());
        BigDecimal personalDays = leaveDays.getOrDefault(SalaryConstants.LEAVE_PERSONAL, BigDecimal.ZERO);
        BigDecimal sickDays = leaveDays.getOrDefault(SalaryConstants.LEAVE_SICK, BigDecimal.ZERO);
        BigDecimal absentDays = attendance == null ? BigDecimal.ZERO
                : Objects.requireNonNullElse(attendance.getAbsentDays(), BigDecimal.ZERO);

        BigDecimal attendanceDeduct = SalaryCalculator.attendanceDeduct(standard, personalDays, sickDays, absentDays);
        BigDecimal overtimePay = SalaryCalculator.overtimePay(standard,
                overtime.getOrDefault("工作日", BigDecimal.ZERO),
                overtime.getOrDefault("休息日", BigDecimal.ZERO),
                overtime.getOrDefault("节假日", BigDecimal.ZERO));

        // 手工录入的收入/扣款在重算时要保留，否则每次重算都把人工调整抹掉
        BigDecimal otherIncome = record.getId() == null ? BigDecimal.ZERO
                : Objects.requireNonNullElse(record.getOtherIncome(), BigDecimal.ZERO);
        BigDecimal otherDeduct = record.getId() == null ? BigDecimal.ZERO
                : Objects.requireNonNullElse(record.getOtherDeduct(), BigDecimal.ZERO);

        BigDecimal gross = SalaryCalculator.scale(proratedStandard.add(overtimePay).add(otherIncome));

        // 社保基数：优先档案指定，否则用「计入社保的应发项」（加班费不计入）
        BigDecimal socialBaseRaw = profile.getSocialBase() != null ? profile.getSocialBase() : standard;
        BigDecimal socialBase = SalaryCalculator.clampBase(socialBaseRaw, policy.getBaseMin(), policy.getBaseMax());
        BigDecimal fundBaseRaw = profile.getFundBase() != null ? profile.getFundBase()
                : (profile.getSocialBase() != null ? profile.getSocialBase() : standard);
        BigDecimal fundBase = SalaryCalculator.clampBase(fundBaseRaw, policy.getFundBaseMin(), policy.getFundBaseMax());

        BigDecimal personalRate = SalaryAmounts.nz(policy.getPensionPersonal())
                .add(SalaryAmounts.nz(policy.getMedicalPersonal()))
                .add(SalaryAmounts.nz(policy.getUnemploymentPersonal()));
        BigDecimal companyRate = SalaryAmounts.nz(policy.getPensionCompany())
                .add(SalaryAmounts.nz(policy.getMedicalCompany()))
                .add(SalaryAmounts.nz(policy.getUnemploymentCompany()))
                .add(SalaryAmounts.nz(policy.getInjuryCompany()))
                .add(SalaryAmounts.nz(policy.getMaternityCompany()));

        BigDecimal socialPersonal = SalaryCalculator.scale(socialBase.multiply(personalRate));
        BigDecimal socialCompany = SalaryCalculator.scale(socialBase.multiply(companyRate));
        BigDecimal fundPersonal = SalaryCalculator.scale(
                fundBase.multiply(SalaryAmounts.nz(policy.getFundPersonal())));
        BigDecimal fundCompany = SalaryCalculator.scale(
                fundBase.multiply(SalaryAmounts.nz(policy.getFundCompany())));

        BigDecimal specialDeduct = SalaryCalculator.scale(profile.getSpecialDeduct());

        // 累计预扣：累计收入 - 累计减除费用(5000×月数) - 累计专项扣除 - 累计专项附加
        BigDecimal cumIncome = cumulative[SalaryConstants.CUM_INCOME].add(gross);
        BigDecimal cumSpecial = cumulative[SalaryConstants.CUM_SPECIAL].add(socialPersonal).add(fundPersonal);
        BigDecimal cumExtra = cumulative[SalaryConstants.CUM_EXTRA].add(specialDeduct);
        BigDecimal cumTaxPaid = cumulative[SalaryConstants.CUM_TAX];
        // 累计减除费用按「本年度在本单位的任职月数」而不是自然月序号：
        // 7 月入职者在 10 月只能减 4 个月，按自然月会多减 6 个月、少扣个税（BIZ-OPT-04 第 2 项）
        int taxMonths = tenureSupport.taxMonthCount(user, year, monthNo);
        BigDecimal taxable = SalaryCalculator.scale(cumIncome
                .subtract(SalaryCalculator.MONTHLY_TAX_FREE.multiply(BigDecimal.valueOf(taxMonths)))
                .subtract(cumSpecial)
                .subtract(cumExtra));
        BigDecimal tax = SalaryCalculator.scale(SalaryCalculator.cumulativeTax(taxable).subtract(cumTaxPaid));
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            // 累计应纳税额小于已预扣时说明多扣了，本月不退（负数会让工资条出现「负个税」），
            // 差额留到年度汇算或后续月份自然抵减
            tax = BigDecimal.ZERO;
        }

        BigDecimal net = SalaryCalculator.scale(gross.subtract(attendanceDeduct)
                .subtract(socialPersonal).subtract(fundPersonal).subtract(tax).subtract(otherDeduct));

        record.setUserId(user.getId());
        record.setDeptId(user.getDeptId());
        record.setMonth(month);
        record.setProrationFactor(prorationFactor);
        record.setProratedStandard(proratedStandard);
        record.setBaseSalary(base);
        record.setPostSalary(post);
        record.setPerfSalary(perf);
        record.setAllowance(allow);
        record.setOvertimePay(overtimePay);
        record.setOtherIncome(SalaryCalculator.scale(otherIncome));
        record.setGrossPay(gross);
        record.setAttendanceDeduct(attendanceDeduct);
        record.setSocialPersonal(socialPersonal);
        record.setFundPersonal(fundPersonal);
        record.setSpecialDeduct(specialDeduct);
        record.setTax(tax);
        record.setOtherDeduct(SalaryCalculator.scale(otherDeduct));
        record.setNetPay(net);
        record.setSocialCompany(socialCompany);
        record.setFundCompany(fundCompany);
        record.setSocialBase(socialBase);
        record.setCumulativeTaxable(taxable);
        record.setStatus(SalaryConstants.STATUS_DRAFT);
        record.setRemark(tenureSupport.buildRemark(perfNote, prorationFactor, standard, proratedStandard));
        record.setCalcTime(LocalDateTime.now());
    }

}
