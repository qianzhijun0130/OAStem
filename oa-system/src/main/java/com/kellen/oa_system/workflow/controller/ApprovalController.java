package com.kellen.oa_system.workflow.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.service.ApprovalService;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批运行时接口：发起、待办、已办、我的发起、同意/驳回、撤销、转办、详情。
 */
@Slf4j
@RestController
@RequestMapping("/workflow/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('system:approval:start')")
    @OperationLog(module = "审批中心", operation = "发起审批申请")
    public Result<Long> start(@Valid @RequestBody ApprovalStartRequest req) {
        return Result.success(approvalService.start(req));
    }

    @GetMapping("/todo")
    @PreAuthorize("hasAuthority('system:approval:todo')")
    public Result<PageResult<ApprovalTodoVO>> todo(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(approvalService.todo(pageNo, pageSize));
    }

    @GetMapping("/done")
    @PreAuthorize("hasAuthority('system:approval:done')")
    public Result<PageResult<ApprovalRecordVO>> done(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(approvalService.done(pageNo, pageSize));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('system:approval:mine')")
    public Result<PageResult<ApprovalInstanceVO>> mine(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(approvalService.mine(status, pageNo, pageSize));
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('system:approval:complete')")
    @OperationLog(module = "审批中心", operation = "审批处理")
    public Result<Void> complete(@Valid @RequestBody ApprovalCompleteRequest req) {
        approvalService.complete(req);
        return Result.success();
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAuthority('system:approval:withdraw')")
    @OperationLog(module = "审批中心", operation = "撤销申请 #{#instanceId}")
    public Result<Void> withdraw(@RequestParam Long instanceId) {
        approvalService.withdraw(instanceId);
        return Result.success();
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('system:approval:transfer')")
    @OperationLog(module = "审批中心", operation = "审批转办")
    public Result<Void> transfer(@Valid @RequestBody ApprovalTransferRequest req) {
        approvalService.transfer(req);
        return Result.success();
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAuthority('system:approval:query')")
    public Result<ApprovalDetailVO> detail(@PathVariable Long id) {
        return Result.success(approvalService.detail(id));
    }
}
