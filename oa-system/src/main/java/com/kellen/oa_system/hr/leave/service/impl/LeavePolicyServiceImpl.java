package com.kellen.oa_system.hr.leave.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.common.enums.LeaveTypeEnum;
import com.kellen.oa_system.hr.leave.dto.LeavePolicySaveRequest;
import com.kellen.oa_system.hr.leave.entity.LeavePolicy;
import com.kellen.oa_system.hr.leave.mapper.LeavePolicyMapper;
import com.kellen.oa_system.hr.leave.service.LeavePolicyService;
import com.kellen.oa_system.hr.leave.vo.LeavePolicyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 地方假别政策维护实现。
 *
 * <p>唯一性口径为「城市 + 假别 + 年度」。之所以不用库级唯一索引：
 * 该表走逻辑删除（`active_flag` 生成列方案），但历史建表脚本未建该索引；
 * 这里用应用层校验保证语义，避免额外 DDL 影响既有库。并发重复提交概率极低（HR 单人维护）。</p>
 */
@Service
@RequiredArgsConstructor
public class LeavePolicyServiceImpl implements LeavePolicyService {

    private final LeavePolicyMapper policyMapper;

    @Override
    public List<LeavePolicyVO> list(Integer year, String city) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        LambdaQueryWrapper<LeavePolicy> wrapper = new LambdaQueryWrapper<LeavePolicy>()
                .eq(LeavePolicy::getEffectiveYear, targetYear)
                .orderByAsc(LeavePolicy::getCity)
                .orderByAsc(LeavePolicy::getLeaveType);
        if (StringUtils.hasText(city)) {
            wrapper.eq(LeavePolicy::getCity, city.trim());
        }
        return policyMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public List<Map<String, Object>> policyTypes() {
        return LeaveTypeEnum.policyControlled().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", e.getCode());
                    item.put("label", e.getLabel());
                    return item;
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(LeavePolicySaveRequest request) {
        if (!LeaveTypeEnum.policyControlledCodes().contains(request.getLeaveType())) {
            throw new BizException(ErrorCode.HR_LEAVE_POLICY_TYPE_INVALID);
        }
        String city = request.getCity().trim();

        // 同「城市 + 假别 + 年度」只允许一条；修改时排除自身
        LeavePolicy existing = policyMapper.selectOne(new LambdaQueryWrapper<LeavePolicy>()
                .eq(LeavePolicy::getCity, city)
                .eq(LeavePolicy::getLeaveType, request.getLeaveType())
                .eq(LeavePolicy::getEffectiveYear, request.getEffectiveYear())
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), request.getId())) {
            throw new BizException(ErrorCode.HR_LEAVE_POLICY_DUPLICATE);
        }

        if (request.getId() == null) {
            LeavePolicy entity = new LeavePolicy();
            entity.setCity(city);
            entity.setLeaveType(request.getLeaveType());
            entity.setDays(request.getDays());
            entity.setEffectiveYear(request.getEffectiveYear());
            entity.setRemark(request.getRemark());
            policyMapper.insert(entity);
            return entity.getId();
        }

        LeavePolicy entity = new LeavePolicy();
        entity.setId(request.getId());
        entity.setCity(city);
        entity.setLeaveType(request.getLeaveType());
        entity.setDays(request.getDays());
        entity.setEffectiveYear(request.getEffectiveYear());
        entity.setRemark(request.getRemark());
        policyMapper.updateById(entity);
        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LeavePolicy exists = policyMapper.selectById(id);
        if (exists == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "政策不存在或已删除");
        }
        // 逻辑删除：deleteById 由 @TableLogic 接管
        policyMapper.deleteById(id);
    }

    private LeavePolicyVO toVO(LeavePolicy policy) {
        LeavePolicyVO vo = new LeavePolicyVO();
        vo.setId(policy.getId());
        vo.setCity(policy.getCity());
        vo.setLeaveType(policy.getLeaveType());
        vo.setLeaveTypeLabel(LeaveTypeEnum.labelOf(policy.getLeaveType()));
        vo.setDays(policy.getDays());
        vo.setEffectiveYear(policy.getEffectiveYear());
        vo.setRemark(policy.getRemark());
        vo.setUpdateTime(policy.getUpdateTime());
        return vo;
    }
}
