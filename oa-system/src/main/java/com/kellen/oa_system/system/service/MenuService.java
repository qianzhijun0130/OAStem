package com.kellen.oa_system.system.service;

import com.kellen.oa_system.system.dto.MenuSaveRequest;
import com.kellen.oa_system.system.vo.MenuVO;

import java.util.List;

/**
 * 菜单领域服务。
 */
public interface MenuService {

    /**
     * 按用户权限查询菜单树（仅目录与菜单，不含按钮），用于前端渲染侧边栏。
     */
    List<MenuVO> getMenuTreeByUserId(Long userId);

    /**
     * 全量菜单树（含目录、菜单、按钮），用于菜单管理与角色权限分配。
     */
    List<MenuVO> getFullMenuTree();

    /**
     * 新增或修改菜单，返回菜单ID。
     */
    Long saveMenu(MenuSaveRequest request);

    /**
     * 删除菜单（存在子节点时拒绝）。
     */
    void deleteMenu(Long id);

    /**
     * 修改菜单状态（启用 / 禁用）。
     */
    void updateMenuStatus(Long id, Integer status);
}
