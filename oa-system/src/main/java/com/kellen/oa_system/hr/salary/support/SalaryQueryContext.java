package com.kellen.oa_system.hr.salary.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.entity.SocialPolicy;
import com.kellen.oa_system.hr.salary.mapper.SalaryProfileMapper;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordMapper;
import com.kellen.oa_system.hr.salary.mapper.SocialPolicyMapper;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 核算前的「取数层」：把人、档案、政策、累计数、月结 JSON 取成内存结构。
 *
 * <p>核算的循环体只做算术，所有数据库往返都发生在这里，且<b>一律批量</b>——
 * 逐人查档案/政策是 BE-OPT-04 之前的写法，500 人要 1500 次往返。新增取数时请沿用批量形状。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalaryQueryContext {

    private final UserMapper userMapper;
    private final SalaryProfileMapper profileMapper;
    private final SocialPolicyMapper policyMapper;
    private final SalaryRecordMapper recordMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final SalaryTenureSupport tenureSupport;
    private final ObjectMapper objectMapper;

    /**
     * 参与核算的人：在职（2/3/4）+ 本核算月内离职（5），按数据范围收窄。
     *
     * <p>为什么必须带上「本月离职」：只取在职会让离职当月的工资直接不发，
     * 而员工实际干到了离职日，属少发（BIZ-OPT-04 第 6 项）。
     * 离职日期取「离职」异动的生效日；没有异动记录时不纳入，避免给早已离职的人重复发薪。</p>
     */
    public List<User> payableUsers(Long deptId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus,
                        List.of(2, 3, 4, SalaryConstants.EMPLOYMENT_RESIGNED));
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
                    if (!Objects.equals(u.getEmploymentStatus(), SalaryConstants.EMPLOYMENT_RESIGNED)) {
                        return true;
                    }
                    LocalDate exit = tenureSupport.resolveExitDate(u.getId(), end);
                    return exit != null && !exit.isBefore(start) && !exit.isAfter(end);
                })
                .toList();
    }

    /** 每人的生效档案（effective_date ≤ at 的最新一条），一次批量取回后在内存里取首条 */
    public Map<Long, SalaryProfile> latestProfiles(Set<Long> userIds, LocalDate at) {
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

    public SalaryProfile latestProfileAt(Long userId, LocalDate at) {
        return latestProfiles(Set.of(userId), at).get(userId);
    }

    /**
     * 批量预取某年全部社保政策，按城市建索引（BE-OPT-04）。
     *
     * <p>原先每个员工查一次，而 cities 的取值域通常 ≤10 个、year 恒定——500 人就是 500 次重复查询。
     * 政策表量级只有数百行，一次全取更划算。</p>
     */
    public Map<String, SocialPolicy> loadPolicies(int year) {
        Map<String, SocialPolicy> index = new HashMap<>();
        List<SocialPolicy> list = policyMapper.selectList(new LambdaQueryWrapper<SocialPolicy>()
                .eq(SocialPolicy::getPolicyYear, year)
                .orderByAsc(SocialPolicy::getId));
        for (SocialPolicy policy : list) {
            // 同城市多条时取 id 最小的那条，与原实现 orderByAsc(id).LIMIT 1 一致
            index.putIfAbsent(policy.getCity(), policy);
        }
        return index;
    }

    /** 从预取索引取政策；指定城市缺失时回落「默认」，两者都没有才抛错 */
    public SocialPolicy requirePolicy(Map<String, SocialPolicy> index, String city, int year) {
        SocialPolicy policy = StringUtils.hasText(city) ? index.get(city) : null;
        if (policy == null) {
            policy = index.get("默认");
        }
        if (policy == null) {
            throw new BizException(ErrorCode.HR_SALARY_POLICY_MISSING,
                    "缺少 " + year + " 年的社保政策（城市：" + (StringUtils.hasText(city) ? city : "未指定") + "）");
        }
        return policy;
    }

    /** 本年 1 月到上一个月「已复核及以后」的累计数，按月聚合到用户维度 */
    public Map<Long, BigDecimal[]> cumulativeBefore(String month, int year) {
        List<SalaryRecord> list = recordMapper.selectList(new LambdaQueryWrapper<SalaryRecord>()
                .likeRight(SalaryRecord::getMonth, String.valueOf(year))
                .lt(SalaryRecord::getMonth, month)
                .ge(SalaryRecord::getStatus, SalaryConstants.STATUS_REVIEWED));
        Map<Long, BigDecimal[]> result = new HashMap<>();
        for (SalaryRecord r : list) {
            BigDecimal[] acc = result.computeIfAbsent(r.getUserId(), k -> SalaryAmounts.zeroCumulative());
            acc[SalaryConstants.CUM_INCOME] = acc[SalaryConstants.CUM_INCOME].add(SalaryAmounts.nz(r.getGrossPay()));
            acc[SalaryConstants.CUM_SPECIAL] = acc[SalaryConstants.CUM_SPECIAL]
                    .add(SalaryAmounts.nz(r.getSocialPersonal())).add(SalaryAmounts.nz(r.getFundPersonal()));
            acc[SalaryConstants.CUM_EXTRA] = acc[SalaryConstants.CUM_EXTRA].add(SalaryAmounts.nz(r.getSpecialDeduct()));
            acc[SalaryConstants.CUM_TAX] = acc[SalaryConstants.CUM_TAX].add(SalaryAmounts.nz(r.getTax()));
        }
        return result;
    }

    /**
     * 考勤月结里的假勤明细 JSON（假别 → 天数 / 加班类型 → 小时）。
     *
     * <p>解析失败按「无假勤」处理并告警：宁可少扣一次，也不能因为一条脏 JSON 让整个月核算崩掉。</p>
     */
    public Map<String, BigDecimal> readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
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

    public User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
