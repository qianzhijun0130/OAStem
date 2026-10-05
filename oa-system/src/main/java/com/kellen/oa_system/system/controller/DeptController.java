package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.system.dto.DeptSaveRequest;
import com.kellen.oa_system.system.service.DeptService;
import com.kellen.oa_system.system.vo.DeptVO;
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
 * 部门管理接口。
 *
 * <p>方法级权限码与 oa_menu 中 menu_type=3 的按钮权限保持一致，
 * 由 {@code @EnableMethodSecurity} + {@code @PreAuthorize} 生效。</p>
 */
@RestController
@RequestMapping("/system/dept")
@Validated
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:dept:query')")
    public Result<List<DeptVO>> tree(@RequestParam(required = false) String deptName,
                                     @RequestParam(required = false) Integer status) {
        return Result.success(deptService.getDeptTree(deptName, status));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('system:dept:add', 'system:dept:edit')")
    @OperationLog(module = "部门管理", operation = "保存部门")
    public Result<Long> save(@Valid @RequestBody DeptSaveRequest request) {
        return Result.success(deptService.saveDept(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @OperationLog(module = "部门管理", operation = "删除部门 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.deleteDept(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @OperationLog(module = "部门管理", operation = "启用或停用部门 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam
                                     @Min(value = 0, message = "状态只能是 0禁用 1启用")
                                     @Max(value = 1, message = "状态只能是 0禁用 1启用") Integer status) {
        deptService.updateStatus(id, status);
        return Result.success();
    }
}
