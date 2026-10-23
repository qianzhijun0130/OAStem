package com.kellen.oa_system.hr.salary.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.salary.dto.SalaryAdjustRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryItemSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryPageQuery;
import com.kellen.oa_system.hr.salary.dto.SalaryProfileSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SocialPolicySaveRequest;
import com.kellen.oa_system.hr.salary.vo.SalaryItemVO;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.hr.salary.vo.SalaryProfileVO;
import com.kellen.oa_system.hr.salary.vo.SalaryRecordVO;
import com.kellen.oa_system.hr.salary.vo.SocialPolicyVO;

import java.util.List;

/**
 * 薪酬业务接口。
 *
 * <p>核算链路是「考勤月结（已封账） → 薪资档案 → 社保政策 → 个税累计」，
 * 四者缺一不可：任一环节用默认值兜底，都会在发薪日后变成对账问题。</p>
 */
public interface SalaryService {

    // ================== 薪资项 ==================

    List<SalaryItemVO> listItems();

    Long saveItem(SalaryItemSaveRequest request);

    void deleteItem(Long id);

    // ================== 社保政策 ==================

    List<SocialPolicyVO> listPolicies(Integer year);

    Long savePolicy(SocialPolicySaveRequest request);

    void deletePolicy(Long id);

    // ================== 薪资档案 ==================

    PageResult<SalaryProfileVO> pageProfiles(String keyword, Long deptId, Integer pageNo, Integer pageSize);

    /** 新增一段档案（生效日期更晚即视为调薪，历史段不动） */
    Long saveProfile(SalaryProfileSaveRequest request);

    /** 某人的档案历史（含金额），用于看调薪轨迹 */
    List<SalaryProfileVO> profileHistory(Long userId);

    // ================== 月度核算 ==================

    /** 生成/重算草稿；已复核及以后的状态不会被覆盖 */
    int calculate(String month, Long deptId);

    PageResult<SalaryRecordVO> pageRecords(SalaryPageQuery query);

    SalaryPayslipVO detail(Long recordId);

    /** 手工调整收入/扣款，写留痕 */
    void adjust(Long recordId, SalaryAdjustRequest request);

    /** 复核：草稿 → 已复核 */
    int review(String month, Long deptId);

    /** 封账：已复核 → 已封账 */
    int lock(String month, Long deptId);

    /** 发放：已封账 → 已发放 */
    int pay(String month, Long deptId);

    // ================== 工资条（本人） ==================

    List<SalaryPayslipVO> myPayslips(Integer year);

    SalaryPayslipVO myPayslip(String month);
}
