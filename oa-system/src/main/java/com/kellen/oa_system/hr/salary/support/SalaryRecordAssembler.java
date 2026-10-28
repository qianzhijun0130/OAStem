package com.kellen.oa_system.hr.salary.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.entity.SalaryRecordItem;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordItemMapper;
import com.kellen.oa_system.hr.salary.vo.SalaryItemLineVO;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.hr.salary.vo.SalaryRecordVO;
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
 * 核算记录的 VO 组装（列表行 / 工资条）。
 *
 * <p>两处都要展示「姓名 + 部门名」，所以用户与部门一律按批取一次；
 * 工资条才需要明细行，列表不要明细（N+1 的另一半来源）。</p>
 */
@Component
@RequiredArgsConstructor
public class SalaryRecordAssembler {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final SalaryRecordItemMapper recordItemMapper;

    public List<SalaryRecordVO> toRecordVOs(List<SalaryRecord> list) {
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
            vo.setCompanyCost(SalaryCalculator.scale(
                    SalaryAmounts.nz(r.getSocialCompany()).add(SalaryAmounts.nz(r.getFundCompany()))));
            vo.setStatus(r.getStatus());
            vo.setStatusLabel(SalaryConstants.STATUS_LABEL.getOrDefault(r.getStatus(), "未知"));
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

    public SalaryPayslipVO toPayslip(SalaryRecord record, boolean withItems) {
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
        vo.setStatusLabel(SalaryConstants.STATUS_LABEL.getOrDefault(record.getStatus(), "未知"));
        vo.setRemark(record.getRemark());
        vo.setCalcTime(record.getCalcTime());
        vo.setPayTime(record.getPayTime());
        if (withItems) {
            List<SalaryItemLineVO> items = recordItemMapper.selectList(
                            new LambdaQueryWrapper<SalaryRecordItem>()
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

    /**
     * 是否本年度有前单位收入：参加工作时间早于入职日，且落在核算年度内。
     *
     * <p>判定只用系统内已有字段（{@code work_start_date} / {@code entry_date}），不去读任何外部数据——
     * 读不到前单位收入，只能提示用户去年度汇算合并。</p>
     */
    boolean hasPriorEmployerIncome(User user, String month) {
        if (user == null || user.getWorkStartDate() == null || user.getEntryDate() == null
                || month == null || month.length() < 4) {
            return false;
        }
        if (!user.getWorkStartDate().isBefore(user.getEntryDate())) {
            return false;   // 首次工作即本单位入职，不存在前单位
        }
        return String.valueOf(user.getWorkStartDate().getYear()).equals(month.substring(0, 4));
    }
}
