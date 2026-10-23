package com.kellen.oa_system.hr.position.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.position.dto.PositionPageQuery;
import com.kellen.oa_system.hr.position.dto.PositionSaveRequest;
import com.kellen.oa_system.hr.position.vo.PositionVO;

import java.util.List;

/**
 * 岗位业务接口。
 */
public interface PositionService {

    /** 岗位分页（含部门名称与在岗人数） */
    PageResult<PositionVO> page(PositionPageQuery query);

    /** 岗位下拉选项：只返回启用岗位，供入职 / 调岗选择 */
    List<PositionVO> listOptions(Long deptId);

    /** 新增或修改岗位，返回岗位ID */
    Long save(PositionSaveRequest request);

    /** 删除岗位（逻辑删）。存在在职员工时拒绝 */
    void delete(Long id);

    /** 启用 / 停用 */
    void updateStatus(Long id, Integer status);
}
