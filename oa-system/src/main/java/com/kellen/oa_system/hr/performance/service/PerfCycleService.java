package com.kellen.oa_system.hr.performance.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.performance.dto.PerfCycleSaveRequest;
import com.kellen.oa_system.hr.performance.vo.PerfCycleVO;

/**
 * 绩效周期：建周期 → 自动生成考核表 → 阶段推进 → 归档。
 */
public interface PerfCycleService {

    PageResult<PerfCycleVO> pageCycles(Integer status, Integer pageNo, Integer pageSize);

    PerfCycleVO detail(Long id);

    /** 新建/编辑周期；新建时自动为符合范围的在职员工生成考核表 */
    Long saveCycle(PerfCycleSaveRequest request);

    /** 手工为周期补齐考核表（幂等，用于新增员工后补录） */
    int generateSheets(Long cycleId);

    /** 推进到下一阶段，返回受影响的考核表条数 */
    int advance(Long cycleId);

    /** 归档：定级、写系数、状态置已归档，结果转只读 */
    int archive(Long cycleId);

    void deleteCycle(Long id);
}
