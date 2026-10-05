package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.system.dto.DictDataPageQuery;
import com.kellen.oa_system.system.dto.DictDataSaveRequest;
import com.kellen.oa_system.system.service.DictDataService;
import com.kellen.oa_system.system.vo.DictDataVO;
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
 * 数据字典项管理接口。
 */
@RestController
@RequestMapping("/system/dict/data")
@Validated
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<PageResult<DictDataVO>> page(DictDataPageQuery query) {
        return Result.success(dictDataService.pageData(query));
    }

    /**
     * 按字典类型编码取启用中的数据项，供业务表单下拉直接渲染。
     *
     * <p>同 {@code /type/enabled}：属于只读基础数据，不挂按钮权限码。</p>
     */
    @GetMapping("/type/{dictType}")
    public Result<List<DictDataVO>> listByType(@PathVariable String dictType) {
        return Result.success(dictDataService.listByType(dictType));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperationLog(module = "系统字典", operation = "保存字典数据")
    public Result<Long> save(@Valid @RequestBody DictDataSaveRequest request) {
        return Result.success(dictDataService.saveData(request));
    }

    /** 修改数据项状态；{@code status} 仅允许 0/1，非法值不得落库（否则会被按状态的过滤静默排除） */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(module = "系统字典", operation = "修改字典数据状态 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam @Min(value = 0, message = "状态只能是 0禁用 1启用")
                                     @Max(value = 1, message = "状态只能是 0禁用 1启用") Integer status) {
        dictDataService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperationLog(module = "系统字典", operation = "删除字典数据 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictDataService.deleteData(id);
        return Result.success();
    }
}
