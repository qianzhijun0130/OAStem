package com.kellen.oa_system.system.service;

import com.kellen.oa_system.system.dto.DeptSaveRequest;
import com.kellen.oa_system.system.vo.DeptVO;

import java.util.List;

/**
 * 部门（组织架构）业务接口。
 */
public interface DeptService {

    /**
     * 查询部门树。
     *
     * <p>按名称或状态过滤时，会保留命中节点的完整祖先链，避免出现无父节点的孤儿分支。</p>
     *
     * @param deptName 部门名称模糊匹配，可为空
     * @param status   状态过滤，可为空
     * @return 部门树，根节点为顶级部门
     */
    List<DeptVO> getDeptTree(String deptName, Integer status);

    /**
     * 新增或修改部门。
     *
     * @return 部门ID
     */
    Long saveDept(DeptSaveRequest request);

    /**
     * 删除部门（逻辑删除）。存在下级部门或部门下有用户时拒绝删除。
     */
    void deleteDept(Long id);

    /**
     * 修改部门状态（启用 / 禁用）。
     */
    void updateStatus(Long id, Integer status);
}
