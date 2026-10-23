package com.kellen.oa_system.hr.performance.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.performance.dto.PerfIndicatorSaveRequest;
import com.kellen.oa_system.hr.performance.dto.PerfPlanSaveRequest;
import com.kellen.oa_system.hr.performance.vo.PerfIndicatorVO;
import com.kellen.oa_system.hr.performance.vo.PerfPlanVO;

import java.util.List;

/**
 * 绩效方案与指标库。
 *
 * <p>方案是绩效的「宪法」：指标、权重、等级区间、系数都在这里定死，
 * 之后的周期、考核表、评分都引用它——所以它必须能锁版本（改方案不影响已生成的考核表）。</p>
 */
public interface PerfPlanService {

    PageResult<PerfPlanVO> pagePlans(String keyword, Integer status, Integer pageNo, Integer pageSize);

    PerfPlanVO detail(Long id);

    /** 保存方案（含等级映射与指标权重；权重合计 ≠ 100% 直接拒绝） */
    Long savePlan(PerfPlanSaveRequest request);

    /** 删除（已被周期引用时拒绝，只能停用） */
    void deletePlan(Long id);

    List<PerfPlanVO> planOptions();

    PageResult<PerfIndicatorVO> pageIndicators(String keyword, Integer type, Integer pageNo, Integer pageSize);

    Long saveIndicator(PerfIndicatorSaveRequest request);

    void deleteIndicator(Long id);

    List<PerfIndicatorVO> indicatorOptions();
}
