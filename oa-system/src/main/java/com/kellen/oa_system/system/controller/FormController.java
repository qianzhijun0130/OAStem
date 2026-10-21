package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.system.dto.FormPageQuery;
import com.kellen.oa_system.system.dto.FormSaveRequest;
import com.kellen.oa_system.system.service.FormService;
import com.kellen.oa_system.system.vo.FormVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 低代码表单管理接口。
 *
 * <p>权限码沿用项目三段式 {@code system:form:action}，其中
 * {@code design} 单独拆出是因为「打开设计器」与「保存表单」是两种粒度的能力：
 * 只读预览场景（如流程模板页查看表单长什么样）不需要设计权限。</p>
 */
@Slf4j
@RestController
@RequestMapping("/system/form")
@Validated
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:form:query')")
    public Result<PageResult<FormVO>> page(FormPageQuery query) {
        return Result.success(formService.pageForms(query));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('system:flow:form-bind')")
    public Result<List<FormVO>> options() {
        return Result.success(formService.listEnabledOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form:query')")
    public Result<FormVO> detail(@PathVariable Long id) {
        return Result.success(formService.getForm(id));
    }

    /**
     * 新增 / 更新表单（带 id 即更新）。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:form:add')")
    @OperationLog(module = "表单管理", operation = "保存表单")
    public Result<Long> save(@Valid @RequestBody FormSaveRequest req) {
        return Result.success(formService.saveForm(req));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:form:edit')")
    @OperationLog(module = "表单管理", operation = "启停表单")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Min(value = 0, message = "状态只能为 0 或 1")
                                     @Max(value = 1, message = "状态只能为 0 或 1") Integer status) {
        formService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:form:delete')")
    @OperationLog(module = "表单管理", operation = "删除表单")
    public Result<Void> delete(@PathVariable Long id) {
        formService.deleteForm(id);
        return Result.success();
    }
}
