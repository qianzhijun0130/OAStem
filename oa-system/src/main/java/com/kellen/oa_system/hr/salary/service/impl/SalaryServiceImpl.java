package com.kellen.oa_system.hr.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.util.LogMasker;
import org.springframework.beans.factory.annotation.Value;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.employee.entity.EmployeeChange;
import com.kellen.oa_system.hr.employee.mapper.EmployeeChangeMapper;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.performance.service.PerfScoreService;
import com.kellen.oa_system.hr.salary.dto.SalaryAdjustRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryItemSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryPageQuery;
import com.kellen.oa_system.hr.salary.dto.SalaryProfileSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SocialPolicySaveRequest;
import com.kellen.oa_system.hr.salary.entity.SalaryAdjustLog;
import com.kellen.oa_system.hr.salary.entity.SalaryItem;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.entity.SalaryRecordItem;
import com.kellen.oa_system.hr.salary.entity.SocialPolicy;
import com.kellen.oa_system.hr.salary.mapper.SalaryAdjustLogMapper;
import com.kellen.oa_system.hr.salary.mapper.SalaryItemMapper;
import com.kellen.oa_system.hr.salary.mapper.SalaryProfileMapper;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordItemMapper;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordMapper;
import com.kellen.oa_system.hr.salary.mapper.SocialPolicyMapper;
import com.kellen.oa_system.hr.salary.service.SalaryService;
import com.kellen.oa_system.hr.salary.support.SalaryCalculator;
import com.kellen.oa_system.hr.salary.vo.SalaryItemLineVO;
import com.kellen.oa_system.hr.salary.vo.SalaryItemVO;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.hr.salary.vo.SalaryProfileVO;
import com.kellen.oa_system.hr.salary.vo.SalaryRecordVO;
import com.kellen.oa_system.hr.salary.vo.SocialPolicyVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 薪酬业务实现。
 *
 * <p>核算的核心是「先确定口径，再逐人算，最后落明细」：口径全部来自
 * {@link SalaryCalculator}，本类只负责取数与组合，不自己发明公式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryServiceImpl implements SalaryService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_REVIEWED = 2;
    private static final int STATUS_LOCKED = 3;
    private static final int STATUS_PAID = 4;

    private static final int MONTH_LOCKED_STATUS = 2;

    /** 任职状态：已离职 */
    private static final int EMPLOYMENT_RESIGNED = 5;

    /** 员工异动类型：离职 */
    private static final int CHANGE_TYPE_RESIGN = 5;

    private static final Map<Integer, String> STATUS_LABEL = Map.of(
            STATUS_DRAFT, "草稿",
            STATUS_REVIEWED, "已复核",
            STATUS_LOCKED, "已封账",
            STATUS_PAID, "已发放");

    /** 参与考勤扣款的假别（婚假/产假等法定假别不扣钱，由政策决定是否支付，不做扣款） */
    private static final String LEAVE_PERSONAL = "事假";
    private static final String LEAVE_SICK = "病假";

    private final SalaryItemMapper itemMapper;
    private final SocialPolicyMapper policyMapper;
    private final SalaryProfileMapper profileMapper;
    private final SalaryRecordMapper recordMapper;
    private final SalaryRecordItemMapper recordItemMapper;
    private final SalaryAdjustLogMapper adjustLogMapper;
    private final AttendanceMonthMapper attendanceMonthMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final ObjectMapper objectMapper;
    /** 绩效系数来源：只读归档结果，取不到按 1.0 */
    private final PerfScoreService perfScoreService;
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

    // ==================================================================
    // 薪资项
    // ==================================================================

    @Override
    public List<SalaryItemVO> listItems() {
        return itemMapper.selectList(new LambdaQueryWrapper<SalaryItem>()
                        .orderByAsc(SalaryItem::getSort))
                .stream().map(this::toItemVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveItem(SalaryItemSaveRequest request) {
        SalaryItem exists = itemMapper.selectOne(new LambdaQueryWrapper<SalaryItem>()
                .eq(SalaryItem::getItemCode, request.getItemCode()).last("LIMIT 1"));
        if (exists != null && !Objects.equals(exists.getId(), request.getId())) {
            throw new BizException(ErrorCode.HR_SALARY_ITEM_DUPLICATE);
        }
        SalaryItem entity = request.getId() == null ? new SalaryItem() : itemMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND, "薪资项不存在");
        }
        entity.setItemCode(request.getItemCode());
        entity.setItemName(request.getItemName());
        entity.setItemType(request.getItemType() == null ? 1 : request.getItemType());
        entity.setIncludeSocial(request.getIncludeSocial() == null ? 1 : request.getIncludeSocial());
        entity.setIncludeTax(request.getIncludeTax() == null ? 1 : request.getIncludeTax());
        entity.setEditable(request.getEditable() == null ? 0 : request.getEditable());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            itemMapper.insert(entity);
        } else {
            itemMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void deleteItem(Long id) {
        // 内置项被核算明细引用，删掉会让历史工资条出现「有金额没项目名」的空洞
        SalaryItem item = itemMapper.selectById(id);
        if (item == null) {
            return;
        }
        if (BUILTIN_ITEM_CODES.contains(item.getItemCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "内置薪资项不能删除，可停用");
        }
        itemMapper.deleteById(id);
    }

    /** 核算引擎直接引用的编码，删掉会导致计算链路断裂 */
    private static final Set<String> BUILTIN_ITEM_CODES = Set.of(
            "BASE", "POST", "PERF", "ALLOW", "OT", "BONUS", "ATT", "SOCIAL", "FUND", "TAX", "OTHER");

    // ==================================================================
    // 社保政策
    // ==================================================================

    @Override
    public List<SocialPolicyVO> listPolicies(Integer year) {
        LambdaQueryWrapper<SocialPolicy> wrapper = new LambdaQueryWrapper<SocialPolicy>()
                .orderByDesc(SocialPolicy::getPolicyYear)
                .orderByAsc(SocialPolicy::getCity);
        if (year != null) {
            wrapper.eq(SocialPolicy::getPolicyYear, year);
        }
        return policyMapper.selectList(wrapper).stream().map(this::toPolicyVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePolicy(SocialPolicySaveRequest request) {
        SocialPolicy duplicate = policyMapper.selectOne(new LambdaQueryWrapper<SocialPolicy>()
                .eq(SocialPolicy::getCity, request.getCity())
                .eq(SocialPolicy::getPolicyYear, request.getPolicyYear())
                .last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
            throw new BizException(ErrorCode.HR_SALARY_POLICY_DUPLICATE);
        }
        SocialPolicy entity = request.getId() == null ? new SocialPolicy() : policyMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND, "社保政策不存在");
        }
        entity.setCity(request.getCity());
        entity.setPolicyYear(request.getPolicyYear());
        entity.setBaseMin(nz(request.getBaseMin()));
        entity.setBaseMax(nz(request.getBaseMax()));
        entity.setFundBaseMin(nz(request.getFundBaseMin()));
        entity.setFundBaseMax(nz(request.getFundBaseMax()));
        entity.setPensionPersonal(nz(request.getPensionPersonal()));
        entity.setPensionCompany(nz(request.getPensionCompany()));
        entity.setMedicalPersonal(nz(request.getMedicalPersonal()));
        entity.setMedicalCompany(nz(request.getMedicalCompany()));
        entity.setUnemploymentPersonal(nz(request.getUnemploymentPersonal()));
        entity.setUnemploymentCompany(nz(request.getUnemploymentCompany()));
        entity.setInjuryCompany(nz(request.getInjuryCompany()));
        entity.setMaternityCompany(nz(request.getMaternityCompany()));
        entity.setFundPersonal(nz(request.getFundPersonal()));
        entity.setFundCompany(nz(request.getFundCompany()));
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            policyMapper.insert(entity);
        } else {
            policyMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void deletePolicy(Long id) {
        policyMapper.deleteById(id);
    }

    // ==================================================================
    // 薪资档案
    // ==================================================================

    @Override
    public PageResult<SalaryProfileVO> pageProfiles(String keyword, Long deptId,
                                                    Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        // 档案是按人分段的，列表要展示「每人当前生效的一段」，所以先按用户分页再取档案
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4));
        if (StringUtils.hasText(keyword)) {
            userWrapper.and(w -> w.like(User::getRealName, keyword)
                    .or().like(User::getWorkNumber, keyword));
        }
        if (deptId != null) {
            List<Long> deptIds = new ArrayList<>();
            deptIds.add(deptId);
            deptIds.addAll(hrDataScopeSupport.subtreeIds(deptId));
            userWrapper.in(User::getDeptId, deptIds);
        }
        hrDataScopeSupport.applyToUser(userWrapper);

        Page<User> page = userMapper.selectPage(new Page<>(no, size), userWrapper);
        List<Long> userIds = page.getRecords().stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), (long) no, (long) size);
        }
        // 每人的最新一段：先按生效日期倒序取全量再按人取第一条，比逐个查库少 N 次往返
        List<SalaryProfile> all = profileMapper.selectList(new LambdaQueryWrapper<SalaryProfile>()
                .in(SalaryProfile::getUserId, userIds)
                .orderByDesc(SalaryProfile::getEffectiveDate)
                .orderByDesc(SalaryProfile::getId));
        Map<Long, SalaryProfile> latest = new LinkedHashMap<>();
        for (SalaryProfile p : all) {
            latest.putIfAbsent(p.getUserId(), p);
        }
        return PageResult.of(toProfileVOs(new ArrayList<>(latest.values())), page.getTotal(),
                (long) no, (long) size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProfile(SalaryProfileSaveRequest request) {
        requireUser(request.getUserId());
        if (request.getEffectiveDate() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "生效日期不能为空");
        }
        SalaryProfile exists = profileMapper.selectOne(new LambdaQueryWrapper<SalaryProfile>()
                .eq(SalaryProfile::getUserId, request.getUserId())
                .eq(SalaryProfile::getEffectiveDate, request.getEffectiveDate())
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "该员工在 " + request.getEffectiveDate() + " 已有档案，调薪请选择更晚的生效日期");
        }
        SalaryProfile entity = new SalaryProfile();
        entity.setUserId(request.getUserId());
        entity.setEffectiveDate(request.getEffectiveDate());
        entity.setBaseSalary(nz(request.getBaseSalary()));
        entity.setPostSalary(nz(request.getPostSalary()));
        entity.setPerfSalary(nz(request.getPerfSalary()));
        entity.setAllowance(nz(request.getAllowance()));
        entity.setSocialBase(request.getSocialBase());
        entity.setFundBase(request.getFundBase());
        entity.setCity(request.getCity());
        entity.setSpecialDeduct(nz(request.getSpecialDeduct()));
        entity.setChangeReason(request.getChangeReason());
        profileMapper.insert(entity);
        // 金额不进日志：日志会落盘并被采集系统收走，且没有访问审计（BE-OPT-11）
        log.info("薪资档案新增: userId={} effective={} standard={}", entity.getUserId(),
                entity.getEffectiveDate(), LogMasker.amount(standardOf(entity)));
        return entity.getId();
    }

    @Override
    public List<SalaryProfileVO> profileHistory(Long userId) {
        return toProfileVOs(profileMapper.selectList(new LambdaQueryWrapper<SalaryProfile>()
                .eq(SalaryProfile::getUserId, userId)
                .orderByDesc(SalaryProfile::getEffectiveDate)));
    }

    // ==================================================================
    // 月度核算
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int calculate(String month, Long deptId) {
        YearMonth ym = parseMonth(month);
        String text = ym.format(MONTH_FORMAT);
        LocalDate end = ym.atEndOfMonth();
        int year = ym.getYear();
        int monthNo = ym.getMonthValue();

        List<User> users = payableUsers(deptId, ym.atDay(1), end);
        if (users.isEmpty()) {
            throw new BizException(ErrorCode.HR_SALARY_NO_DATA);
        }
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());

        // 1) 每人的生效档案（effective_date <= 月末 的最新一条）
        Map<Long, SalaryProfile> profiles = latestProfiles(userIds, end);
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
                .filter(u -> !Objects.equals(attendances.get(u.getId()).getStatus(), MONTH_LOCKED_STATUS))
                .map(User::getRealName).toList();
        if (!unlocked.isEmpty()) {
            throw new BizException(ErrorCode.HR_SALARY_ATTENDANCE_UNLOCKED,
                    "以下员工 " + text + " 的考勤尚未封账：" + String.join("、", unlocked));
        }

        // 3) 本年已确定的累计数（已复核及以后），用于累计预扣个税
        Map<Long, BigDecimal[]> cumulative = cumulativeBefore(text, year);

        int count = 0;
        for (User user : users) {
            SalaryProfile profile = profiles.get(user.getId());
            SalaryRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<SalaryRecord>()
                    .eq(SalaryRecord::getUserId, user.getId())
                    .eq(SalaryRecord::getMonth, text)
                    .last("LIMIT 1"));
            // 已复核之后的结果不允许被重算覆盖：复核过的数字是要发给员工的
            if (existing != null && existing.getStatus() != null && existing.getStatus() >= STATUS_REVIEWED) {
                continue;
            }
            SalaryRecord record = existing == null ? new SalaryRecord() : existing;
            fillRecord(record, user, profile, attendances.get(user.getId()), text, year, monthNo,
                    ym.atDay(1), end, cumulative.getOrDefault(user.getId(), zeroCumulative()));
            if (record.getId() == null) {
                recordMapper.insert(record);
            } else {
                recordMapper.updateById(record);
            }
            rebuildItems(record);
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
                            LocalDate monthStart, LocalDate monthEnd,
                            BigDecimal[] cumulative) {
        SocialPolicy policy = requirePolicy(profile.getCity(), year);

        BigDecimal base = SalaryCalculator.scale(profile.getBaseSalary());
        BigDecimal post = SalaryCalculator.scale(profile.getPostSalary());
        // 绩效工资 = 档案里的绩效基数 × 当期绩效系数；没有归档结果时按 1.0 并在备注标注，
        // 绝不因为「绩效还没归档」就把整月工资卡住不发
        BigDecimal perfCoefficient = perfScoreService.coefficientOf(user.getId(), month);
        String perfNote = perfCoefficient == null
                ? "绩效系数按 1.0（本期无归档绩效）"
                : "绩效系数 " + perfCoefficient.stripTrailingZeros().toPlainString();
        BigDecimal perf = SalaryCalculator.scale(nz(profile.getPerfSalary())
                .multiply(Objects.requireNonNullElse(perfCoefficient, BigDecimal.ONE)));
        BigDecimal allow = SalaryCalculator.scale(profile.getAllowance());
        BigDecimal standard = base.add(post).add(perf).add(allow);

        // 入职/离职当月按实际在职天数折算标准工资（BIZ-OPT-04 第 1 项）。
        // 只折算「标准工资」这一项：社保按整月缴纳、考勤扣款仍按未折算的日工资口径，
        // 否则入离职当天的缺勤扣款会跟着变小，反而制造新的口径错误。
        BigDecimal prorationFactor = prorationFactor(user, monthStart, monthEnd);
        BigDecimal proratedStandard = SalaryCalculator.scale(standard.multiply(prorationFactor));

        Map<String, BigDecimal> leaveDays = readJson(attendance == null ? null : attendance.getLeaveDaysJson());
        Map<String, BigDecimal> overtime = readJson(attendance == null ? null : attendance.getOvertimeJson());
        BigDecimal personalDays = leaveDays.getOrDefault(LEAVE_PERSONAL, BigDecimal.ZERO);
        BigDecimal sickDays = leaveDays.getOrDefault(LEAVE_SICK, BigDecimal.ZERO);
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

        BigDecimal personalRate = nz(policy.getPensionPersonal())
                .add(nz(policy.getMedicalPersonal()))
                .add(nz(policy.getUnemploymentPersonal()));
        BigDecimal companyRate = nz(policy.getPensionCompany())
                .add(nz(policy.getMedicalCompany()))
                .add(nz(policy.getUnemploymentCompany()))
                .add(nz(policy.getInjuryCompany()))
                .add(nz(policy.getMaternityCompany()));

        BigDecimal socialPersonal = SalaryCalculator.scale(socialBase.multiply(personalRate));
        BigDecimal socialCompany = SalaryCalculator.scale(socialBase.multiply(companyRate));
        BigDecimal fundPersonal = SalaryCalculator.scale(fundBase.multiply(nz(policy.getFundPersonal())));
        BigDecimal fundCompany = SalaryCalculator.scale(fundBase.multiply(nz(policy.getFundCompany())));

        BigDecimal specialDeduct = SalaryCalculator.scale(profile.getSpecialDeduct());

        // 累计预扣：累计收入 - 累计减除费用(5000×月数) - 累计专项扣除 - 累计专项附加
        BigDecimal cumIncome = cumulative[CUM_INCOME].add(gross);
        BigDecimal cumSpecial = cumulative[CUM_SPECIAL].add(socialPersonal).add(fundPersonal);
        BigDecimal cumExtra = cumulative[CUM_EXTRA].add(specialDeduct);
        BigDecimal cumTaxPaid = cumulative[CUM_TAX];
        // 累计减除费用按「本年度在本单位的任职月数」而不是自然月序号：
        // 7 月入职者在 10 月只能减 4 个月，按自然月会多减 6 个月、少扣个税（BIZ-OPT-04 第 2 项）
        int taxMonths = taxMonthCount(user, year, monthNo);
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
        record.setStatus(STATUS_DRAFT);
        record.setRemark(buildRemark(perfNote, prorationFactor, standard, proratedStandard));
        record.setCalcTime(LocalDateTime.now());
    }

    /** 逐项明细：有金额才写行，工资条上不出现一堆 0.00 */
    private void rebuildItems(SalaryRecord record) {
        recordItemMapper.delete(new LambdaQueryWrapper<SalaryRecordItem>()
                .eq(SalaryRecordItem::getRecordId, record.getId()));
        List<SalaryRecordItem> items = new ArrayList<>();
        addItem(items, record.getId(), "BASE", "基本工资", 1, record.getBaseSalary(), 1);
        addItem(items, record.getId(), "POST", "岗位工资", 1, record.getPostSalary(), 2);
        addItem(items, record.getId(), "PERF", "绩效工资", 1, record.getPerfSalary(), 3);
        addItem(items, record.getId(), "ALLOW", "固定津贴", 1, record.getAllowance(), 4);
        addItem(items, record.getId(), "OT", "加班费", 1, record.getOvertimePay(), 5);
        addItem(items, record.getId(), "BONUS", "其他收入", 1, record.getOtherIncome(), 6);
        addItem(items, record.getId(), "ATT", "考勤扣款", 2, record.getAttendanceDeduct(), 7);
        addItem(items, record.getId(), "SOCIAL", "社保个人", 2, record.getSocialPersonal(), 8);
        addItem(items, record.getId(), "FUND", "公积金个人", 2, record.getFundPersonal(), 9);
        addItem(items, record.getId(), "TAX", "个人所得税", 2, record.getTax(), 10);
        addItem(items, record.getId(), "OTHER", "其他扣款", 2, record.getOtherDeduct(), 11);
        for (SalaryRecordItem item : items) {
            recordItemMapper.insert(item);
        }
    }

    private void addItem(List<SalaryRecordItem> target, Long recordId, String code, String name,
                         int type, BigDecimal amount, int sort) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        SalaryRecordItem item = new SalaryRecordItem();
        item.setRecordId(recordId);
        item.setItemCode(code);
        item.setItemName(name);
        item.setItemType(type);
        item.setAmount(SalaryCalculator.scale(amount));
        item.setSort(sort);
        target.add(item);
    }

    @Override
    @DataScope
    public PageResult<SalaryRecordVO> pageRecords(SalaryPageQuery query) {
        int no = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int size = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<SalaryRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getMonth())) {
            wrapper.eq(SalaryRecord::getMonth, query.getMonth());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SalaryRecord::getStatus, query.getStatus());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(SalaryRecord::getDeptId, query.getDeptId());
        }
        Long viewer = SecurityContextUtil.getCurrentUserId();
        if (!hrDataScopeSupport.isUnrestricted()) {
            // 数据范围在用户层判定：薪资表只存了核算时的部门快照，直接按快照过滤会漏掉后来调部门的。
            // 注意：本方法必须带 @DataScope，否则 isUnrestricted() 恒为 true、整块被跳过 —— 这正是
            // BE-OPT-02 修复的越权缺口（任何持 hr:salary:calc 的账号都能看到全公司工资条）。
            LambdaQueryWrapper<User> scoped = new LambdaQueryWrapper<User>().select(User::getId);
            hrDataScopeSupport.applyToUser(scoped);
            List<Long> allowed = userMapper.selectList(scoped).stream().map(User::getId).toList();
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(SalaryRecord::getUserId, allowed);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            List<Long> matched = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .and(w -> w.like(User::getRealName, query.getKeyword())
                                    .or().like(User::getWorkNumber, query.getKeyword()))
                            .select(User::getId))
                    .stream().map(User::getId).toList();
            if (matched.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(SalaryRecord::getUserId, matched);
        }
        wrapper.orderByDesc(SalaryRecord::getMonth).orderByAsc(SalaryRecord::getUserId);

        Page<SalaryRecord> page = recordMapper.selectPage(new Page<>(no, size), wrapper);
        List<SalaryRecordVO> vos = toRecordVOs(page.getRecords());
        log.debug("薪资列表查询: viewer={} total={}", viewer, page.getTotal());
        return PageResult.of(vos, page.getTotal(), (long) no, (long) size);
    }

    @Override
    public SalaryPayslipVO detail(Long recordId) {
        SalaryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND);
        }
        return toPayslip(record, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long recordId, SalaryAdjustRequest request) {
        SalaryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND);
        }
        if (record.getStatus() != null && record.getStatus() >= STATUS_LOCKED) {
            throw new BizException(ErrorCode.HR_SALARY_MONTH_LOCKED);
        }
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BizException(ErrorCode.HR_SALARY_ADJUST_REASON_REQUIRED);
        }
        BigDecimal beforeIncome = nz(record.getOtherIncome());
        BigDecimal beforeDeduct = nz(record.getOtherDeduct());
        BigDecimal afterIncome = request.getOtherIncome() == null ? beforeIncome : nz(request.getOtherIncome());
        BigDecimal afterDeduct = request.getOtherDeduct() == null ? beforeDeduct : nz(request.getOtherDeduct());

        // 调整后必须重算应发/个税/实发，否则「改了收入但实发没变」
        SalaryProfile profile = latestProfileAt(record.getUserId(), parseMonth(record.getMonth()).atEndOfMonth());
        if (profile == null) {
            throw new BizException(ErrorCode.HR_SALARY_PROFILE_MISSING);
        }
        record.setOtherIncome(afterIncome);
        record.setOtherDeduct(afterDeduct);
        BigDecimal deltaIncome = afterIncome.subtract(beforeIncome);
        BigDecimal deltaDeduct = afterDeduct.subtract(beforeDeduct);
        record.setGrossPay(SalaryCalculator.scale(nz(record.getGrossPay()).add(deltaIncome)));
        BigDecimal taxDelta = adjustTax(record, deltaIncome);
        record.setNetPay(SalaryCalculator.scale(nz(record.getNetPay())
                .add(deltaIncome).subtract(deltaDeduct).subtract(taxDelta)));
        recordMapper.updateById(record);

        writeAdjustLog(recordId, "otherIncome", "其他收入", beforeIncome, afterIncome, request.getRemark());
        writeAdjustLog(recordId, "otherDeduct", "其他扣款", beforeDeduct, afterDeduct, request.getRemark());
        rebuildItems(record);
        log.info("薪资手工调整: recordId={} income {} deduct {} 原因={}", recordId,
                LogMasker.amountChange(beforeIncome, afterIncome),
                LogMasker.amountChange(beforeDeduct, afterDeduct), request.getRemark());
    }

    /**
     * 手工调整只影响「本月」的个税差额。
     *
     * <p>严格做法要重算后续所有月份的累计，但那会让已发工资的历史记录被动改动。
     * 这里是「本月差额按边际税率补扣」——误差留到年度汇算，符合多数企业的操作习惯。</p>
     */
    private BigDecimal adjustTax(SalaryRecord record, BigDecimal deltaIncome) {
        if (deltaIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxable = nz(record.getCumulativeTaxable()).add(deltaIncome);
        BigDecimal recalculated = SalaryCalculator.cumulativeTax(taxable);
        BigDecimal before = SalaryCalculator.cumulativeTax(nz(record.getCumulativeTaxable()));
        BigDecimal delta = SalaryCalculator.scale(recalculated.subtract(before));
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            delta = BigDecimal.ZERO;
        }
        record.setTax(SalaryCalculator.scale(nz(record.getTax()).add(delta)));
        record.setCumulativeTaxable(SalaryCalculator.scale(taxable));
        return delta;
    }

    private void writeAdjustLog(Long recordId, String field, String label,
                                BigDecimal before, BigDecimal after, String reason) {
        if (before.compareTo(after) == 0) {
            return;
        }
        SalaryAdjustLog entry = new SalaryAdjustLog();
        entry.setRecordId(recordId);
        entry.setFieldName(field);
        entry.setFieldLabel(label);
        entry.setBeforeValue(before);
        entry.setAfterValue(after);
        entry.setReason(reason);
        entry.setOperatorId(SecurityContextUtil.getCurrentUserId());
        entry.setCreateTime(LocalDateTime.now());
        adjustLogMapper.insert(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int review(String month, Long deptId) {
        return transit(month, deptId, STATUS_DRAFT, STATUS_REVIEWED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int lock(String month, Long deptId) {
        return transit(month, deptId, STATUS_REVIEWED, STATUS_LOCKED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pay(String month, Long deptId) {
        return transit(month, deptId, STATUS_LOCKED, STATUS_PAID);
    }

    /** 状态推进：只处理「当前状态 = from」的记录，返回推进条数 */
    private int transit(String month, Long deptId, int from, int to) {
        if (!StringUtils.hasText(month)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份不能为空");
        }
        LambdaUpdateWrapper<SalaryRecord> wrapper = new LambdaUpdateWrapper<SalaryRecord>()
                .eq(SalaryRecord::getMonth, month)
                .eq(SalaryRecord::getStatus, from)
                .set(SalaryRecord::getStatus, to)
                .set(SalaryRecord::getUpdateTime, LocalDateTime.now());
        if (deptId != null) {
            wrapper.eq(SalaryRecord::getDeptId, deptId);
        }
        if (to == STATUS_REVIEWED) {
            wrapper.set(SalaryRecord::getReviewTime, LocalDateTime.now())
                    .set(SalaryRecord::getReviewBy, SecurityContextUtil.getCurrentUserId());
        }
        if (to == STATUS_PAID) {
            wrapper.set(SalaryRecord::getPayTime, LocalDateTime.now());
        }
        int count = recordMapper.update(null, wrapper);
        if (count == 0) {
            throw new BizException(ErrorCode.HR_SALARY_STATUS_INVALID,
                    "没有状态为「" + STATUS_LABEL.get(from) + "」的记录可推进，请先执行上一步");
        }
        log.info("薪资状态推进: month={} {}→{} 条数={}", month, STATUS_LABEL.get(from), STATUS_LABEL.get(to), count);
        return count;
    }

    // ==================================================================
    // 工资条（本人）
    // ==================================================================

    @Override
    public List<SalaryPayslipVO> myPayslips(Integer year) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<SalaryRecord> wrapper = new LambdaQueryWrapper<SalaryRecord>()
                .eq(SalaryRecord::getUserId, userId)
                // 草稿不给本人看：数字还可能变，提前露出去只会制造误会
                .in(SalaryRecord::getStatus, List.of(STATUS_REVIEWED, STATUS_LOCKED, STATUS_PAID))
                .orderByDesc(SalaryRecord::getMonth);
        if (year != null) {
            wrapper.likeRight(SalaryRecord::getMonth, String.valueOf(year));
        }
        return recordMapper.selectList(wrapper).stream().map(r -> toPayslip(r, true)).toList();
    }

    @Override
    public SalaryPayslipVO myPayslip(String month) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        SalaryRecord record = recordMapper.selectOne(new LambdaQueryWrapper<SalaryRecord>()
                .eq(SalaryRecord::getUserId, userId)
                .eq(SalaryRecord::getMonth, month)
                .in(SalaryRecord::getStatus, List.of(STATUS_REVIEWED, STATUS_LOCKED, STATUS_PAID))
                .last("LIMIT 1"));
        if (record == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND, "该月没有可查看的工资条");
        }
        return toPayslip(record, true);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private static final int CUM_INCOME = 0;
    private static final int CUM_SPECIAL = 1;
    private static final int CUM_EXTRA = 2;
    private static final int CUM_TAX = 3;

    private static BigDecimal[] zeroCumulative() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    /** 本年 1 月到上一个月「已复核及以后」的累计数，按月聚合到用户维度 */
    private Map<Long, BigDecimal[]> cumulativeBefore(String month, int year) {
        List<SalaryRecord> list = recordMapper.selectList(new LambdaQueryWrapper<SalaryRecord>()
                .likeRight(SalaryRecord::getMonth, String.valueOf(year))
                .lt(SalaryRecord::getMonth, month)
                .ge(SalaryRecord::getStatus, STATUS_REVIEWED));
        Map<Long, BigDecimal[]> result = new HashMap<>();
        for (SalaryRecord r : list) {
            BigDecimal[] acc = result.computeIfAbsent(r.getUserId(), k -> zeroCumulative());
            acc[CUM_INCOME] = acc[CUM_INCOME].add(nz(r.getGrossPay()));
            acc[CUM_SPECIAL] = acc[CUM_SPECIAL].add(nz(r.getSocialPersonal())).add(nz(r.getFundPersonal()));
            acc[CUM_EXTRA] = acc[CUM_EXTRA].add(nz(r.getSpecialDeduct()));
            acc[CUM_TAX] = acc[CUM_TAX].add(nz(r.getTax()));
        }
        return result;
    }

    /**
     * 参与核算的人：在职（2/3/4）+ 本核算月内离职（5），按数据范围收窄。
     *
     * <p>为什么必须带上「本月离职」：只取在职会让离职当月的工资直接不发，
     * 而员工实际干到了离职日，属少发（BIZ-OPT-04 第 6 项）。
     * 离职日期取「离职」异动的生效日；没有异动记录时不纳入，避免给早已离职的人重复发薪。</p>
     */
    private List<User> payableUsers(Long deptId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4, EMPLOYMENT_RESIGNED));
        if (deptId != null) {
            List<Long> deptIds = new ArrayList<>();
            deptIds.add(deptId);
            deptIds.addAll(hrDataScopeSupport.subtreeIds(deptId));
            wrapper.in(User::getDeptId, deptIds);
        } else {
            hrDataScopeSupport.applyToUser(wrapper);
        }
        return userMapper.selectList(wrapper).stream()
                .filter(u -> {
                    if (!Objects.equals(u.getEmploymentStatus(), EMPLOYMENT_RESIGNED)) {
                        return true;
                    }
                    LocalDate exit = resolveExitDate(u.getId(), end);
                    return exit != null && !exit.isBefore(start) && !exit.isAfter(end);
                })
                .toList();
    }

    /** 离职生效日：该员工最近一条「离职」异动的生效日期；无记录返回 null */
    private LocalDate resolveExitDate(Long userId, LocalDate at) {
        EmployeeChange change = changeMapper.selectOne(new LambdaQueryWrapper<EmployeeChange>()
                .eq(EmployeeChange::getUserId, userId)
                .eq(EmployeeChange::getChangeType, CHANGE_TYPE_RESIGN)
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
    private BigDecimal prorationFactor(User user, LocalDate start, LocalDate end) {
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

    /**
     * 个税累计减除费用对应的月数。
     *
     * <p>{@code tenure}（默认）：本年度在本单位的任职月数，含入职当月；
     * {@code calendar}：自然月序号（旧行为，仅用于回滚对比）。</p>
     */
    private int taxMonthCount(User user, int year, int monthNo) {
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
    private String buildRemark(String perfNote, BigDecimal factor, BigDecimal standard, BigDecimal prorated) {
        StringBuilder sb = new StringBuilder(perfNote == null ? "" : perfNote);
        if (factor != null && factor.compareTo(BigDecimal.ONE) != 0) {
            sb.append("；在职天数折算系数 ").append(factor.stripTrailingZeros().toPlainString())
                    .append("（标准工资 ").append(standard.toPlainString())
                    .append(" → ").append(prorated.toPlainString()).append("）");
        }
        return sb.toString();
    }

    private Map<Long, SalaryProfile> latestProfiles(Set<Long> userIds, LocalDate at) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SalaryProfile> list = profileMapper.selectList(new LambdaQueryWrapper<SalaryProfile>()
                .in(SalaryProfile::getUserId, userIds)
                .le(SalaryProfile::getEffectiveDate, at)
                .orderByDesc(SalaryProfile::getEffectiveDate)
                .orderByDesc(SalaryProfile::getId));
        Map<Long, SalaryProfile> result = new HashMap<>();
        for (SalaryProfile p : list) {
            result.putIfAbsent(p.getUserId(), p);
        }
        return result;
    }

    private SalaryProfile latestProfileAt(Long userId, LocalDate at) {
        return latestProfiles(Set.of(userId), at).get(userId);
    }

    private SocialPolicy requirePolicy(String city, int year) {
        LambdaQueryWrapper<SocialPolicy> wrapper = new LambdaQueryWrapper<SocialPolicy>()
                .eq(SocialPolicy::getPolicyYear, year);
        if (StringUtils.hasText(city)) {
            wrapper.eq(SocialPolicy::getCity, city);
        }
        SocialPolicy policy = policyMapper.selectOne(wrapper.orderByAsc(SocialPolicy::getId).last("LIMIT 1"));
        if (policy == null && StringUtils.hasText(city)) {
            // 员工档案指定了城市但没配政策时，回落到「默认」城市，而不是让核算整批失败
            policy = policyMapper.selectOne(new LambdaQueryWrapper<SocialPolicy>()
                    .eq(SocialPolicy::getPolicyYear, year)
                    .eq(SocialPolicy::getCity, "默认")
                    .orderByAsc(SocialPolicy::getId)
                    .last("LIMIT 1"));
        }
        if (policy == null) {
            throw new BizException(ErrorCode.HR_SALARY_POLICY_MISSING,
                    "缺少 " + year + " 年的社保政策（城市：" + (StringUtils.hasText(city) ? city : "未指定") + "）");
        }
        return policy;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private static YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month, MONTH_FORMAT);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份格式应为 yyyy-MM");
        }
    }

    private Map<String, BigDecimal> readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Map<String, BigDecimal> result = new HashMap<>();
            raw.forEach((k, v) -> result.put(k, v == null ? BigDecimal.ZERO
                    : new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP)));
            return result;
        } catch (RuntimeException e) {
            log.warn("考勤月结 JSON 解析失败，按无假勤处理: {}", json);
            return Collections.emptyMap();
        }
    }

    private static BigDecimal standardOf(SalaryProfile p) {
        return nz(p.getBaseSalary()).add(nz(p.getPostSalary()))
                .add(nz(p.getPerfSalary())).add(nz(p.getAllowance()));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ---------------- VO 组装 ----------------

    private SalaryItemVO toItemVO(SalaryItem entity) {
        SalaryItemVO vo = new SalaryItemVO();
        vo.setId(entity.getId());
        vo.setItemCode(entity.getItemCode());
        vo.setItemName(entity.getItemName());
        vo.setItemType(entity.getItemType());
        vo.setIncludeSocial(entity.getIncludeSocial());
        vo.setIncludeTax(entity.getIncludeTax());
        vo.setEditable(entity.getEditable());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    private SocialPolicyVO toPolicyVO(SocialPolicy entity) {
        SocialPolicyVO vo = new SocialPolicyVO();
        vo.setId(entity.getId());
        vo.setCity(entity.getCity());
        vo.setPolicyYear(entity.getPolicyYear());
        vo.setBaseMin(entity.getBaseMin());
        vo.setBaseMax(entity.getBaseMax());
        vo.setFundBaseMin(entity.getFundBaseMin());
        vo.setFundBaseMax(entity.getFundBaseMax());
        vo.setPensionPersonal(entity.getPensionPersonal());
        vo.setPensionCompany(entity.getPensionCompany());
        vo.setMedicalPersonal(entity.getMedicalPersonal());
        vo.setMedicalCompany(entity.getMedicalCompany());
        vo.setUnemploymentPersonal(entity.getUnemploymentPersonal());
        vo.setUnemploymentCompany(entity.getUnemploymentCompany());
        vo.setInjuryCompany(entity.getInjuryCompany());
        vo.setMaternityCompany(entity.getMaternityCompany());
        vo.setFundPersonal(entity.getFundPersonal());
        vo.setFundCompany(entity.getFundCompany());
        vo.setPersonalRateTotal(nz(entity.getPensionPersonal()).add(nz(entity.getMedicalPersonal()))
                .add(nz(entity.getUnemploymentPersonal())).add(nz(entity.getFundPersonal())));
        vo.setRemark(entity.getRemark());
        return vo;
    }

    private List<SalaryProfileVO> toProfileVOs(List<SalaryProfile> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = list.stream().map(SalaryProfile::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        List<SalaryProfileVO> vos = new ArrayList<>(list.size());
        for (SalaryProfile p : list) {
            SalaryProfileVO vo = new SalaryProfileVO();
            vo.setId(p.getId());
            vo.setUserId(p.getUserId());
            User u = users.get(p.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptId(u.getDeptId());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
            vo.setEffectiveDate(p.getEffectiveDate());
            vo.setBaseSalary(p.getBaseSalary());
            vo.setPostSalary(p.getPostSalary());
            vo.setPerfSalary(p.getPerfSalary());
            vo.setAllowance(p.getAllowance());
            vo.setStandardSalary(SalaryCalculator.scale(standardOf(p)));
            vo.setSocialBase(p.getSocialBase());
            vo.setFundBase(p.getFundBase());
            vo.setCity(p.getCity());
            vo.setSpecialDeduct(p.getSpecialDeduct());
            vo.setChangeReason(p.getChangeReason());
            vo.setCreateTime(p.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 是否本年度有前单位收入：参加工作时间早于入职日，且落在核算年度内。
     *
     * <p>判定只用系统内已有字段（`work_start_date` / `entry_date`），不去读任何外部数据——
     * 读不到前单位收入，只能提示用户去年度汇算合并。</p>
     */
    private boolean hasPriorEmployerIncome(User user, String month) {
        if (user == null || user.getWorkStartDate() == null || user.getEntryDate() == null
                || month == null || month.length() < 4) {
            return false;
        }
        if (!user.getWorkStartDate().isBefore(user.getEntryDate())) {
            return false;   // 首次工作即本单位入职，不存在前单位
        }
        return String.valueOf(user.getWorkStartDate().getYear()).equals(month.substring(0, 4));
    }

    private List<SalaryRecordVO> toRecordVOs(List<SalaryRecord> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = list.stream().map(SalaryRecord::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        List<SalaryRecordVO> vos = new ArrayList<>(list.size());
        for (SalaryRecord r : list) {
            SalaryRecordVO vo = new SalaryRecordVO();
            vo.setId(r.getId());
            vo.setUserId(r.getUserId());
            User u = users.get(r.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
            }
            vo.setDeptId(r.getDeptId());
            vo.setDeptName(deptNames.get(r.getDeptId()));
            vo.setMonth(r.getMonth());
            vo.setGrossPay(r.getGrossPay());
            vo.setProrationFactor(r.getProrationFactor());
            vo.setProratedStandard(r.getProratedStandard());
            vo.setAttendanceDeduct(r.getAttendanceDeduct());
            vo.setSocialPersonal(r.getSocialPersonal());
            vo.setFundPersonal(r.getFundPersonal());
            vo.setTax(r.getTax());
            vo.setOtherDeduct(r.getOtherDeduct());
            vo.setNetPay(r.getNetPay());
            vo.setCompanyCost(SalaryCalculator.scale(nz(r.getSocialCompany()).add(nz(r.getFundCompany()))));
            vo.setStatus(r.getStatus());
            vo.setStatusLabel(STATUS_LABEL.getOrDefault(r.getStatus(), "未知"));
            // 换单位员工提示：个税累计口径只含本单位收入，前单位部分需并入年度汇算
            if (hasPriorEmployerIncome(u, r.getMonth())) {
                vo.setPriorEmployerIncome(true);
                vo.setPriorEmployerTip("本年度在前单位已有收入，个税只按本单位收入预扣，需在年度汇算合并计算");
            } else {
                vo.setPriorEmployerIncome(false);
            }
            vo.setCalcTime(r.getCalcTime());
            vo.setPayTime(r.getPayTime());
            vos.add(vo);
        }
        return vos;
    }

    private SalaryPayslipVO toPayslip(SalaryRecord record, boolean withItems) {
        SalaryPayslipVO vo = new SalaryPayslipVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setMonth(record.getMonth());
        User user = userMapper.selectById(record.getUserId());
        if (user != null) {
            vo.setRealName(user.getRealName());
            vo.setWorkNumber(user.getWorkNumber());
            Department dept = user.getDeptId() == null ? null : departmentMapper.selectById(user.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getDeptName());
        }
        vo.setBaseSalary(record.getBaseSalary());
        vo.setPostSalary(record.getPostSalary());
        vo.setPerfSalary(record.getPerfSalary());
        vo.setAllowance(record.getAllowance());
        vo.setOvertimePay(record.getOvertimePay());
        vo.setOtherIncome(record.getOtherIncome());
        vo.setGrossPay(record.getGrossPay());
        vo.setAttendanceDeduct(record.getAttendanceDeduct());
        vo.setSocialPersonal(record.getSocialPersonal());
        vo.setFundPersonal(record.getFundPersonal());
        vo.setTax(record.getTax());
        vo.setOtherDeduct(record.getOtherDeduct());
        vo.setNetPay(record.getNetPay());
        vo.setSocialCompany(record.getSocialCompany());
        vo.setFundCompany(record.getFundCompany());
        vo.setSocialBase(record.getSocialBase());
        vo.setCumulativeTaxable(record.getCumulativeTaxable());
        vo.setSpecialDeduct(record.getSpecialDeduct());
        vo.setStatus(record.getStatus());
        vo.setStatusLabel(STATUS_LABEL.getOrDefault(record.getStatus(), "未知"));
        vo.setRemark(record.getRemark());
        vo.setCalcTime(record.getCalcTime());
        vo.setPayTime(record.getPayTime());
        if (withItems) {
            List<SalaryItemLineVO> items = recordItemMapper.selectList(new LambdaQueryWrapper<SalaryRecordItem>()
                            .eq(SalaryRecordItem::getRecordId, record.getId())
                            .orderByAsc(SalaryRecordItem::getSort))
                    .stream().map(i -> {
                        SalaryItemLineVO line = new SalaryItemLineVO();
                        line.setItemCode(i.getItemCode());
                        line.setItemName(i.getItemName());
                        line.setItemType(i.getItemType());
                        line.setAmount(i.getAmount());
                        return line;
                    }).toList();
            vo.setItems(items);
        }
        return vo;
    }
}
