package com.kellen.oa_system.workflow.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.workflow.dto.FlowDefinitionImportRequest;
import com.kellen.oa_system.workflow.dto.FlowDefinitionPageQuery;
import com.kellen.oa_system.workflow.dto.FlowDefinitionSaveRequest;
import com.kellen.oa_system.workflow.service.FlowDefinitionService;
import com.kellen.oa_system.workflow.vo.FlowDefinitionVO;
import com.kellen.oa_system.workflow.vo.FlowOptionVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程模板管理接口（方案B：流程由前端 bpmn-js modeler 设计后保存到此）。
 */
@Slf4j
@RestController
@RequestMapping("/workflow/flow-definition")
@Validated
@RequiredArgsConstructor
public class FlowDefinitionController {

    private final FlowDefinitionService flowDefinitionService;

    /**
     * 启用中的流程模板选项（只读、最小字段）。
     *
     * <p>供「发起申请」页拉取模板下拉。鉴权用 {@code system:approval:apply} 而非
     * {@code system:flow:query}：发起审批是<b>全体员工</b>的日常动作，而模板管理是管理职能；
     * 若沿用管理权限码，专员 / 员工角色一打开页面就会 403、无法发起。</p>
     *
     * <p>只返回 {@code id / flowCode / flowName / category / formFields}，
     * 不暴露 {@code deploymentId}、{@code nodeConfig} 等模板管理内部信息。</p>
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('system:approval:apply')")
    public Result<List<FlowOptionVO>> options() {
        return Result.success(flowDefinitionService.listEnabledOptions());
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:flow:query')")
    public Result<PageResult<FlowDefinitionVO>> page(FlowDefinitionPageQuery query) {
        return Result.success(flowDefinitionService.pageDefinitions(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:flow:query')")
    public Result<FlowDefinitionVO> detail(@PathVariable Long id) {
        return Result.success(flowDefinitionService.getDetail(id));
    }

    @GetMapping("/{id}/bpmn")
    @PreAuthorize("hasAuthority('system:flow:query')")
    public Result<String> bpmn(@PathVariable Long id) {
        return Result.success(flowDefinitionService.getBpmnXml(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:flow:add')")
    @OperationLog(module = "流程模板", operation = "保存流程模板")
    public Result<Long> save(@Valid @RequestBody FlowDefinitionSaveRequest req) {
        return Result.success(flowDefinitionService.saveDefinition(req));
    }

    /** 导入 BPMN XML：后端解析并部署，返回新建模板 id */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('system:flow:add')")
    @OperationLog(module = "流程模板", operation = "导入 BPMN 流程模板")
    public Result<Long> importBpmn(@Valid @RequestBody FlowDefinitionImportRequest req) {
        return Result.success(flowDefinitionService.importDefinition(req));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:flow:edit')")
    @OperationLog(module = "流程模板", operation = "修改流程模板状态 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam
                                     @Min(value = 0, message = "状态只能是 0禁用 1启用")
                                     @Max(value = 1, message = "状态只能是 0禁用 1启用") Integer status) {
        flowDefinitionService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:flow:delete')")
    @OperationLog(module = "流程模板", operation = "删除流程模板 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        flowDefinitionService.deleteDefinition(id);
        return Result.success();
    }
}
