package com.kellen.oa_system.hr.employee.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.employee.vo.CompletenessItemVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeCompletenessVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 建档完整度（BIZ-OPT-09）。
 *
 * <p>只读统计，不修改任何数据——所以它不需要事务写权限，也不该有「修复」动作。
 * 「补数据」必须走各自的正常入口（档案页、合同管理），否则会绕开校验与变更留痕。</p>
 */
public interface EmployeeCompletenessService {

    /** 六个维度的缺失人数与占比 */
    EmployeeCompletenessVO summary();

    /** 某个维度的人员清单（分页） */
    PageResult<CompletenessItemVO> items(String dimension, int pageNo, int pageSize);

    /** 某个维度的人员清单导出 */
    void export(String dimension, HttpServletResponse response);
}
