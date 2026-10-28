package com.kellen.oa_system.hr.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.salary.dto.SalaryItemSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SocialPolicySaveRequest;
import com.kellen.oa_system.hr.salary.entity.SalaryItem;
import com.kellen.oa_system.hr.salary.entity.SocialPolicy;
import com.kellen.oa_system.hr.salary.mapper.SalaryItemMapper;
import com.kellen.oa_system.hr.salary.mapper.SocialPolicyMapper;
import com.kellen.oa_system.hr.salary.support.SalaryAmounts;
import com.kellen.oa_system.hr.salary.support.SalaryConstants;
import com.kellen.oa_system.hr.salary.support.SalaryVoAssembler;
import com.kellen.oa_system.hr.salary.vo.SalaryItemVO;
import com.kellen.oa_system.hr.salary.vo.SocialPolicyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 薪资项与社保政策：核算口径的两张配置表。
 *
 * <p>两者的共同点是「改了会直接影响下一次核算」，所以都有唯一性预检（编码 / 城市+年度），
 * 且内置薪资项禁止删除——历史工资条靠 item_code 回溯，删掉就只剩金额没有项目名。</p>
 */
@Service
@RequiredArgsConstructor
public class SalaryConfigService {

    private final SalaryItemMapper itemMapper;
    private final SocialPolicyMapper policyMapper;
    private final SalaryVoAssembler assembler;

    public List<SalaryItemVO> listItems() {
        return itemMapper.selectList(new LambdaQueryWrapper<SalaryItem>()
                        .orderByAsc(SalaryItem::getSort))
                .stream().map(assembler::toItemVO).toList();
    }

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

    public void deleteItem(Long id) {
        // 内置项被核算明细引用，删掉会让历史工资条出现「有金额没项目名」的空洞
        SalaryItem item = itemMapper.selectById(id);
        if (item == null) {
            return;
        }
        if (SalaryConstants.BUILTIN_ITEM_CODES.contains(item.getItemCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "内置薪资项不能删除，可停用");
        }
        itemMapper.deleteById(id);
    }

    public List<SocialPolicyVO> listPolicies(Integer year) {
        LambdaQueryWrapper<SocialPolicy> wrapper = new LambdaQueryWrapper<SocialPolicy>()
                .orderByDesc(SocialPolicy::getPolicyYear)
                .orderByAsc(SocialPolicy::getCity);
        if (year != null) {
            wrapper.eq(SocialPolicy::getPolicyYear, year);
        }
        return policyMapper.selectList(wrapper).stream().map(assembler::toPolicyVO).toList();
    }

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
        entity.setBaseMin(SalaryAmounts.nz(request.getBaseMin()));
        entity.setBaseMax(SalaryAmounts.nz(request.getBaseMax()));
        entity.setFundBaseMin(SalaryAmounts.nz(request.getFundBaseMin()));
        entity.setFundBaseMax(SalaryAmounts.nz(request.getFundBaseMax()));
        entity.setPensionPersonal(SalaryAmounts.nz(request.getPensionPersonal()));
        entity.setPensionCompany(SalaryAmounts.nz(request.getPensionCompany()));
        entity.setMedicalPersonal(SalaryAmounts.nz(request.getMedicalPersonal()));
        entity.setMedicalCompany(SalaryAmounts.nz(request.getMedicalCompany()));
        entity.setUnemploymentPersonal(SalaryAmounts.nz(request.getUnemploymentPersonal()));
        entity.setUnemploymentCompany(SalaryAmounts.nz(request.getUnemploymentCompany()));
        entity.setInjuryCompany(SalaryAmounts.nz(request.getInjuryCompany()));
        entity.setMaternityCompany(SalaryAmounts.nz(request.getMaternityCompany()));
        entity.setFundPersonal(SalaryAmounts.nz(request.getFundPersonal()));
        entity.setFundCompany(SalaryAmounts.nz(request.getFundCompany()));
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            policyMapper.insert(entity);
        } else {
            policyMapper.updateById(entity);
        }
        return entity.getId();
    }

    public void deletePolicy(Long id) {
        policyMapper.deleteById(id);
    }
}
