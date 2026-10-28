package com.kellen.oa_system.hr.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.util.LogMasker;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.salary.dto.SalaryProfileSaveRequest;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import com.kellen.oa_system.hr.salary.mapper.SalaryProfileMapper;
import com.kellen.oa_system.hr.salary.support.SalaryAmounts;
import com.kellen.oa_system.hr.salary.support.SalaryQueryContext;
import com.kellen.oa_system.hr.salary.support.SalaryVoAssembler;
import com.kellen.oa_system.hr.salary.vo.SalaryProfileVO;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 薪资档案：按「人 + 生效日」分段，核算时取月末前最新一段。
 *
 * <p>档案是只增不改的：同一员工同一生效日只允许一条，调薪必须选更晚的日期。
 * 这样任何一个月的历史核算结果都能被完整解释，不需要「猜当时的档案长什么样」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryProfileService {

    private final SalaryProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final SalaryQueryContext queryContext;
    private final SalaryVoAssembler assembler;

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
        return PageResult.of(assembler.toProfileVOs(new ArrayList<>(latest.values())), page.getTotal(),
                (long) no, (long) size);
    }

    public Long saveProfile(SalaryProfileSaveRequest request) {
        queryContext.requireUser(request.getUserId());
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
        entity.setBaseSalary(SalaryAmounts.nz(request.getBaseSalary()));
        entity.setPostSalary(SalaryAmounts.nz(request.getPostSalary()));
        entity.setPerfSalary(SalaryAmounts.nz(request.getPerfSalary()));
        entity.setAllowance(SalaryAmounts.nz(request.getAllowance()));
        entity.setSocialBase(request.getSocialBase());
        entity.setFundBase(request.getFundBase());
        entity.setCity(request.getCity());
        entity.setSpecialDeduct(SalaryAmounts.nz(request.getSpecialDeduct()));
        entity.setChangeReason(request.getChangeReason());
        profileMapper.insert(entity);
        // 金额不进日志：日志会落盘并被采集系统收走，且没有访问审计（BE-OPT-11）
        log.info("薪资档案新增: userId={} effective={} standard={}", entity.getUserId(),
                entity.getEffectiveDate(), LogMasker.amount(SalaryAmounts.standardOf(entity)));
        return entity.getId();
    }

    public List<SalaryProfileVO> profileHistory(Long userId) {
        return assembler.toProfileVOs(profileMapper.selectList(new LambdaQueryWrapper<SalaryProfile>()
                .eq(SalaryProfile::getUserId, userId)
                .orderByDesc(SalaryProfile::getEffectiveDate)));
    }
}
