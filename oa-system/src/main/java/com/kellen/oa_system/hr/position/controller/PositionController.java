package com.kellen.oa_system.hr.position.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.position.dto.PositionPageQuery;
import com.kellen.oa_system.hr.position.dto.PositionSaveRequest;
import com.kellen.oa_system.hr.position.service.PositionService;
import com.kellen.oa_system.hr.position.vo.PositionVO;
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
 * 岗位管理接口。
 */
@RestController
@RequestMapping("/hr/position")
@Validated
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('hr:position')")
    public Result<PageResult<PositionVO>> page(@Valid PositionPageQuery query) {
        return Result.success(positionService.page(query));
    }

    /**
     * 岗位下拉选项。
     *
     * <p>员工档案 / 调岗表单都要用，因此对 {@code hr:employee} 一并放行——
     * 只放开「启用岗位的只读列表」，不涉及任何写操作。</p>
     */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('hr:position', 'hr:employee')")
    public Result<List<PositionVO>> options(@RequestParam(required = false) Long deptId) {
        return Result.success(positionService.listOptions(deptId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('hr:position:create', 'hr:position:update')")
    @OperationLog(module = "岗位管理", operation = "保存岗位")
    public Result<Long> save(@Valid @RequestBody PositionSaveRequest request) {
        return Result.success(positionService.save(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('hr:position:delete')")
    @OperationLog(module = "岗位管理", operation = "删除岗位 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('hr:position:update')")
    @OperationLog(module = "岗位管理", operation = "启停岗位 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam
                                     @Min(value = 0, message = "状态只能是 0停用 1启用")
                                     @Max(value = 1, message = "状态只能是 0停用 1启用") Integer status) {
        positionService.updateStatus(id, status);
        return Result.success();
    }
}
