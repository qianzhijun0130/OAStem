package com.kellen.oa_system.hr.salary.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.salary.entity.SalaryItem;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import com.kellen.oa_system.hr.salary.entity.SocialPolicy;
import com.kellen.oa_system.hr.salary.vo.SalaryItemVO;
import com.kellen.oa_system.hr.salary.vo.SalaryProfileVO;
import com.kellen.oa_system.hr.salary.vo.SocialPolicyVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置类 VO 组装（薪资项 / 社保政策 / 薪资档案）。
 *
 * <p>档案 VO 带姓名与部门名，用户与部门都<b>批量取一次</b>——逐个查会让 20 人的档案列表
 * 多出 40 次往返。</p>
 */
@Component
@RequiredArgsConstructor
public class SalaryVoAssembler {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    public SalaryItemVO toItemVO(SalaryItem entity) {
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

    public SocialPolicyVO toPolicyVO(SocialPolicy entity) {
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
        vo.setPersonalRateTotal(SalaryAmounts.nz(entity.getPensionPersonal())
                .add(SalaryAmounts.nz(entity.getMedicalPersonal()))
                .add(SalaryAmounts.nz(entity.getUnemploymentPersonal()))
                .add(SalaryAmounts.nz(entity.getFundPersonal())));
        vo.setRemark(entity.getRemark());
        return vo;
    }

    public List<SalaryProfileVO> toProfileVOs(List<SalaryProfile> list) {
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
            vo.setStandardSalary(SalaryCalculator.scale(SalaryAmounts.standardOf(p)));
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
}
