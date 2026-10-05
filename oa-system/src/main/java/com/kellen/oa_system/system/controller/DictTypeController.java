package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.system.dto.DictTypePageQuery;
import com.kellen.oa_system.system.dto.DictTypeSaveRequest;
import com.kellen.oa_system.system.service.DictTypeService;
import com.kellen.oa_system.system.vo.DictTypeVO;
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
 * 数据字典类型管理接口。
 *
 * <p>权限码与 oa_menu 中 menu_type=3 的按钮权限保持一致。</p>
 */
@RestController
@RequestMapping("/system/dict/type")
@Validated
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<PageResult<DictTypeVO>> page(DictTypePageQuery query) {
        return Result.success(dictTypeService.pageTypes(query));
    }

    /**
     * 启用中的字典类型列表，供「新增字典项」「按分类筛选」等下拉使用。
     *
     * <p>不加按钮权限码：这是所有登录用户都会用到的只读基础数据，
     * 加码会导致每个新角色都要额外授权一次。登录态由 SecurityConfig 统一保证。</p>
     */
    @GetMapping("/enabled")
    public Result<List<DictTypeVO>> enabled() {
        return Result.success(dictTypeService.listEnabledTypes());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperationLog(module = "系统字典", operation = "保存字典类型")
    public Result<Long> save(@Valid @RequestBody DictTypeSaveRequest request) {
        return Result.success(dictTypeService.saveType(request));
    }

    /**
     * 修改类型状态。
     *
     * <p>{@code status} 只有 0/1 两个合法值。这里用方法级约束把住入口——
     * 若漏校验，非法值会直接落库，而按状态的过滤（{@code /enabled}、{@code /data/type/{code}}）
     * 会把该行静默排除，表现为「数据无故消失」。</p>
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(module = "系统字典", operation = "修改字典类型状态 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam @Min(value = 0, message = "状态只能是 0禁用 1启用")
                                     @Max(value = 1, message = "状态只能是 0禁用 1启用") Integer status) {
        dictTypeService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperationLog(module = "系统字典", operation = "删除字典类型 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictTypeService.deleteType(id);
        return Result.success();
    }
}
