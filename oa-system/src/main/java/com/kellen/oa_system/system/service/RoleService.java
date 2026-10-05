package com.kellen.oa_system.system.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.RolePageQuery;
import com.kellen.oa_system.system.dto.RoleSaveRequest;
import com.kellen.oa_system.system.vo.RoleOptionVO;
import com.kellen.oa_system.system.vo.RoleVO;

import java.util.List;

/**
 * 角色领域服务。
 */
public interface RoleService {

    /** 分页查询角色列表（含用户数） */
    PageResult<RoleVO> pageRoles(RolePageQuery query);

    /** 全部角色下拉选项 */
    List<RoleOptionVO> listOptions();

    /** 新增或修改角色，返回角色ID */
    Long saveRole(RoleSaveRequest request);

    /** 删除角色（关联用户或超级管理员角色拒绝删除） */
    void deleteRole(Long id);

    /** 修改角色状态（启用 / 禁用） */
    void updateRoleStatus(Long id, Integer status);

    /** 查询角色已分配的菜单ID集合 */
    List<Long> getRoleMenuIds(Long roleId);

    /** 重新分配角色菜单权限（全量覆盖） */
    void assignMenus(Long roleId, List<Long> menuIds);

    /**
     * 以源角色的菜单集合为基线，覆盖式克隆到目标角色。
     *
     * <p>用于「以成熟角色为模板新建角色」，避免逐项重勾导致漏掉基础菜单。
     * 语义等同 {@link #assignMenus} 的全量覆盖：目标角色原有菜单会被清空。</p>
     */
    void cloneMenusFrom(Long roleId, Long sourceRoleId);
}
