package com.kellen.oa_system.workflow.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.workflow.dto.NodeFormBindRequest;
import com.kellen.oa_system.workflow.service.NodeFormService;
import com.kellen.oa_system.workflow.vo.NodeFormVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程节点表单绑定接口。
 *
 * <p>与「表单管理」分开：绑定的业务主体是流程模板（谁在哪个环节填什么），
 * 权限码走 {@code system:flow:form-bind}，而不是 {@code system:form:*}。
 * 这样模板管理员可以改绑定而无需获得表单定义的管理权。</p>
 */
@Slf4j
@RestController
@RequestMapping("/workflow/flow-definition")
@Validated
@RequiredArgsConstructor
public class NodeFormController {

    private final NodeFormService nodeFormService;

    /**
     * 发起页取「发起环节主表单」规则。
     *
     * <p>鉴权用 {@code system:approval:apply}（面向全体员工），
     * 与本控制器的管理端点（{@code system:flow:form-bind}）区分开——
     * 否则专员/员工没有流程模板管理权限，打开发起页就拿不到表单。</p>
     */
    @GetMapping("/primary-form")
    @PreAuthorize("hasAuthority('system:approval:apply')")
    public Result<NodeFormVO> primaryForm(@RequestParam String flowCode) {
        return Result.success(nodeFormService.getPrimaryFormByFlowCode(flowCode));
    }

    /** 列出某流程模板的全部节点表单绑定 */
    @GetMapping("/{id}/node-forms")
    @PreAuthorize("hasAuthority('system:flow:form-bind')")
    public Result<List<NodeFormVO>> listByFlow(@PathVariable Long id) {
        return Result.success(nodeFormService.listByFlowDef(id));
    }

    /** 绑定 / 重绑某节点的表单（全量覆盖语义） */
    @PostMapping("/node-forms")
    @PreAuthorize("hasAuthority('system:flow:form-bind')")
    public Result<Void> bind(@Valid @RequestBody NodeFormBindRequest req) {
        nodeFormService.bindForm(req);
        return Result.success();
    }

    @DeleteMapping("/node-forms/{bindId}")
    @PreAuthorize("hasAuthority('system:flow:form-bind')")
    public Result<Void> unbind(@PathVariable Long bindId) {
        nodeFormService.unbindForm(bindId);
        return Result.success();
    }
}
