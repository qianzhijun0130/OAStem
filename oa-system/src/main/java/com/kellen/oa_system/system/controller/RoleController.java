package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.system.dto.RoleMenuCloneRequest;
import com.kellen.oa_system.system.dto.RolePageQuery;
import com.kellen.oa_system.system.dto.RoleSaveRequest;
import com.kellen.oa_system.system.service.RoleService;
import com.kellen.oa_system.system.vo.RoleOptionVO;
import com.kellen.oa_system.system.vo.RoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限管理接口。
 */
@RestController
@RequestMapping("/system/role")
@Validated
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<PageResult<RoleVO>> page(RolePageQuery query) {
        return Result.success(roleService.pageRoles(query));
    }

    /** 角色下拉选项，供用户表单等场景复用 */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<RoleOptionVO>> options() {
        return Result.success(roleService.listOptions());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('system:role:add', 'system:role:edit')")
    @OperationLog(module = "角色管理", operation = "保存角色")
    public Result<Long> save(@Valid @RequestBody RoleSaveRequest request) {
        return Result.success(roleService.saveRole(request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperationLog(module = "角色管理", operation = "启用或禁用角色 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam
                                     @Min(value = 0, message = "状态只能是 0禁用 1启用")
                                     @Max(value = 1, message = "状态只能是 0禁用 1启用") Integer status) {
        roleService.updateRoleStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperationLog(module = "角色管理", operation = "删除角色 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    /** 查询角色已分配菜单 */
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<Long>> roleMenus(@PathVariable Long id) {
        return Result.success(roleService.getRoleMenuIds(id));
    }

    /** 分配角色菜单权限（全量覆盖） */
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:assign')")
    @OperationLog(module = "角色管理", operation = "角色分配权限 #{#id}")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(id, menuIds);
        return Result.success();
    }

    /**
     * 以源角色的菜单为基线，覆盖式克隆到目标角色。
     *
     * <p>系统没有角色继承机制，菜单必须逐角色独立勾选；新建角色时从成熟角色克隆，
     * 可避免漏勾基础菜单（工作台、我的公告等）导致「权限给了但侧边栏缺节点」。</p>
     */
    @PostMapping("/{id}/menus/clone")
    @PreAuthorize("hasAuthority('system:role:assign')")
    @OperationLog(module = "角色管理", operation = "克隆角色菜单 #{#id}")
    public Result<Void> cloneMenus(@PathVariable Long id, @Valid @RequestBody RoleMenuCloneRequest request) {
        roleService.cloneMenusFrom(id, request.getSourceRoleId());
        return Result.success();
    }
}
