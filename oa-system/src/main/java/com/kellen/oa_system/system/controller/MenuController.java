package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.system.dto.MenuSaveRequest;
import com.kellen.oa_system.system.service.MenuService;
import com.kellen.oa_system.system.vo.MenuVO;
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
 * 菜单管理接口。
 *
 * <p>方法级权限码与 oa_menu 中 menu_type=3 的按钮权限保持一致。</p>
 */
@RestController
@RequestMapping("/system/menu")
@Validated
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 全量菜单树（含目录/菜单/按钮），用于菜单管理与角色权限分配 */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:query')")
    public Result<List<MenuVO>> tree() {
        return Result.success(menuService.getFullMenuTree());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('system:menu:add', 'system:menu:edit')")
    @OperationLog(module = "菜单管理", operation = "保存菜单")
    public Result<Long> save(@Valid @RequestBody MenuSaveRequest request) {
        return Result.success(menuService.saveMenu(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @OperationLog(module = "菜单管理", operation = "删除菜单 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @OperationLog(module = "菜单管理", operation = "启用或停用菜单 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam
                                     @Min(value = 0, message = "状态只能是 0禁用 1启用")
                                     @Max(value = 1, message = "状态只能是 0禁用 1启用") Integer status) {
        menuService.updateMenuStatus(id, status);
        return Result.success();
    }
}
