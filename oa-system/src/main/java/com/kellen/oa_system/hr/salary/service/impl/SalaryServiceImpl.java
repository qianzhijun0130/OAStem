package com.kellen.oa_system.hr.salary.service.impl;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.salary.dto.SalaryAdjustRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryItemSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryPageQuery;
import com.kellen.oa_system.hr.salary.dto.SalaryProfileSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SocialPolicySaveRequest;
import com.kellen.oa_system.hr.salary.service.SalaryService;
import com.kellen.oa_system.hr.salary.support.SalaryCalculator;
import com.kellen.oa_system.hr.salary.vo.SalaryItemVO;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.hr.salary.vo.SalaryProfileVO;
import com.kellen.oa_system.hr.salary.vo.SalaryRecordVO;
import com.kellen.oa_system.hr.salary.vo.SocialPolicyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 薪酬业务门面。
 *
 * <p>本类只做三件事，别往里加业务逻辑：</p>
 * <ol>
 *   <li><b>事务边界</b>——所有 {@code @Transactional} 都留在这一层。下沉到协作类会踩「自调用不生效」
 *       和「内层提前提交」两个坑，尤其是核算：它必须整月一个事务，中途提交会留下半套工资。</li>
 *   <li><b>数据范围</b>——{@code @DataScope} 必须标在这里。切面把可见范围写入 ThreadLocal，
 *       协作类只读它；注解一旦下沉，入口方法就没有快照，协作类会退化成「不过滤」。</li>
 *   <li><b>委派</b>——按业务域转给下面五个服务，保证每个文件都小而专。</li>
 * </ol>
 *
 * <p>核算的核心是「先确定口径，再逐人算，最后落明细」：口径全部来自
 * {@link SalaryCalculator}，本类只负责组合，不自己发明公式。</p>
 */
@Service
@RequiredArgsConstructor
public class SalaryServiceImpl implements SalaryService {

    private final SalaryConfigService configService;
    private final SalaryProfileService profileService;
    private final SalaryCalcService calcService;
    private final SalaryRecordService recordService;
    private final SalaryPayslipService payslipService;

    // ==================================================================
    // 薪资项 / 社保政策
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public List<SalaryItemVO> listItems() {
        return configService.listItems();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveItem(SalaryItemSaveRequest request) {
        return configService.saveItem(request);
    }

    @Override
    public void deleteItem(Long id) {
        configService.deleteItem(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SocialPolicyVO> listPolicies(Integer year) {
        return configService.listPolicies(year);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePolicy(SocialPolicySaveRequest request) {
        return configService.savePolicy(request);
    }

    @Override
    public void deletePolicy(Long id) {
        configService.deletePolicy(id);
    }

    // ==================================================================
    // 薪资档案
    // ==================================================================

    @Override
    public PageResult<SalaryProfileVO> pageProfiles(String keyword, Long deptId,
                                                    Integer pageNo, Integer pageSize) {
        return profileService.pageProfiles(keyword, deptId, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProfile(SalaryProfileSaveRequest request) {
        return profileService.saveProfile(request);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SalaryProfileVO> profileHistory(Long userId) {
        return profileService.profileHistory(userId);
    }

    // ==================================================================
    // 月度核算
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int calculate(String month, Long deptId) {
        return calcService.calculate(month, deptId);
    }

    @Transactional(readOnly = true)
    @DataScope
    @Override
    public PageResult<SalaryRecordVO> pageRecords(SalaryPageQuery query) {
        return recordService.pageRecords(query);
    }

    @Transactional(readOnly = true)
    @Override
    public SalaryPayslipVO detail(Long recordId) {
        return recordService.detail(recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long recordId, SalaryAdjustRequest request) {
        recordService.adjust(recordId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int review(String month, Long deptId) {
        return recordService.review(month, deptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int lock(String month, Long deptId) {
        return recordService.lock(month, deptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pay(String month, Long deptId) {
        return recordService.pay(month, deptId);
    }

    // ==================================================================
    // 工资条（本人）
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public List<SalaryPayslipVO> myPayslips(Integer year) {
        return payslipService.myPayslips(year);
    }

    @Transactional(readOnly = true)
    @Override
    public SalaryPayslipVO myPayslip(String month) {
        return payslipService.myPayslip(month);
    }
}
