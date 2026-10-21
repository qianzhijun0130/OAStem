package com.kellen.oa_system.workflow.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.workflow.dto.NodeFieldPermissionRequest;
import com.kellen.oa_system.workflow.service.NodeFieldPermissionService;
import com.kellen.oa_system.workflow.vo.NodeFieldPermissionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 节点表单字段权限接口。
 *
 * <p>权限码沿用 {@code system:flow:form-bind}：字段权限与节点绑表单同属「流程模板管理」职能，
 * 拆出独立权限码会让授权矩阵复杂度显著上升，收益有限。</p>
 */
@RestController
@RequestMapping("/workflow/node-field")
@RequiredArgsConstructor
public class NodeFieldPermissionController {

    private final NodeFieldPermissionService permissionService;

    /**
     * 读取某节点的字段权限。
     *
     * <p>返回表单的<b>全部字段</b>，未配置的以「只读 + 未配置」返回，
     * 前端可直接渲染整张表，无需在前端补默认值。</p>
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:flow:form-bind')")
    public Result<NodeFieldPermissionVO> list(@RequestParam Long flowDefId,
                                              @RequestParam String nodeKey,
                                              @RequestParam Long formId) {
        return Result.success(permissionService.getPermissions(flowDefId, nodeKey, formId));
    }

    /** 保存某节点的字段权限（整节点全量覆盖） */
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('system:flow:form-bind')")
    public Result<Void> save(@Valid @RequestBody NodeFieldPermissionRequest req) {
        permissionService.savePermissions(req);
        return Result.success();
    }
}
