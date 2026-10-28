package com.kellen.oa_system.hr.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordMapper;
import com.kellen.oa_system.hr.salary.support.SalaryConstants;
import com.kellen.oa_system.hr.salary.support.SalaryRecordAssembler;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 员工自助视角的工资条：只看自己、只看到「已复核及以后」。
 *
 * <p>草稿不给本人看——数字还可能变，提前露出去只会制造误会；这一条与列表页的可见性
 * 分属两个入口，所以单独成类而不是挂在管理视角的服务上。</p>
 */
@Service
@RequiredArgsConstructor
public class SalaryPayslipService {

    /** 本人可见的状态：已复核 / 已封账 / 已发放 */
    private static final List<Integer> VISIBLE_STATUS = List.of(
            SalaryConstants.STATUS_REVIEWED, SalaryConstants.STATUS_LOCKED, SalaryConstants.STATUS_PAID);

    private final SalaryRecordMapper recordMapper;
    private final SalaryRecordAssembler assembler;

    public List<SalaryPayslipVO> myPayslips(Integer year) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<SalaryRecord> wrapper = new LambdaQueryWrapper<SalaryRecord>()
                .eq(SalaryRecord::getUserId, userId)
                .in(SalaryRecord::getStatus, VISIBLE_STATUS)
                .orderByDesc(SalaryRecord::getMonth);
        if (year != null) {
            wrapper.likeRight(SalaryRecord::getMonth, String.valueOf(year));
        }
        return recordMapper.selectList(wrapper).stream().map(r -> assembler.toPayslip(r, true)).toList();
    }

    public SalaryPayslipVO myPayslip(String month) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        SalaryRecord record = recordMapper.selectOne(new LambdaQueryWrapper<SalaryRecord>()
                .eq(SalaryRecord::getUserId, userId)
                .eq(SalaryRecord::getMonth, month)
                .in(SalaryRecord::getStatus, VISIBLE_STATUS)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND, "该月没有可查看的工资条");
        }
        return assembler.toPayslip(record, true);
    }
}
