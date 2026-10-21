package com.kellen.oa_system.workflow.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.workflow.dto.ApprovalCcRequest;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.service.ApprovalService;
import com.kellen.oa_system.workflow.vo.ApprovalCcVO;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.CcUserVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    /** 保存草稿（不启动流程，可在「我的流程」里继续提交） */
    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('system:approval:start')")
    @OperationLog(module = "任务中心", operation = "保存申请草稿")
    public Result<Long> saveDraft(@Valid @RequestBody ApprovalStartRequest req) {
        return Result.success(approvalService.saveDraft(req));
    }

    /** 更新草稿内容 */
    @PutMapping("/draft/{id}")
    @PreAuthorize("hasAuthority('system:approval:start')")
    public Result<Void> updateDraft(@PathVariable Long id, @Valid @RequestBody ApprovalStartRequest req) {
        approvalService.updateDraft(id, req);
        return Result.success();
    }

    /** 提交草稿 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('system:approval:start')")
    @OperationLog(module = "任务中心", operation = "提交申请草稿 #{#id}")
    public Result<Void> submitDraft(@PathVariable Long id) {
        approvalService.submitDraft(id);
        return Result.success();
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

    /** 抄送审批单给若干人（仅告知，不产生待办） */
    @PostMapping("/cc")
    @PreAuthorize("hasAuthority('system:approval:cc')")
    @OperationLog(module = "任务中心", operation = "抄送审批单 #{#req.instanceId}")
    public Result<Void> cc(@Valid @RequestBody ApprovalCcRequest req) {
        approvalService.cc(req);
        return Result.success();
    }

    /** 我的抄送任务分页（readFlag 为空表示全部） */
    @GetMapping("/cc/page")
    @PreAuthorize("hasAuthority('system:approval:cc')")
    public Result<PageResult<ApprovalCcVO>> ccPage(
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(approvalService.ccPage(readFlag, pageNo, pageSize));
    }

    /** 标记单条抄送已读 */
    @PostMapping("/cc/read")
    @PreAuthorize("hasAuthority('system:approval:cc')")
    public Result<Void> readCc(@RequestParam Long ccId) {
        approvalService.readCc(ccId);
        return Result.success();
    }

    /** 抄送人候选（仅启用用户） */
    @GetMapping("/cc/users")
    @PreAuthorize("hasAuthority('system:approval:cc')")
    public Result<java.util.List<CcUserVO>> ccUsers() {
        return Result.success(approvalService.ccUsers());
    }

    /** 我的抄送全部标记已读 */
    @PostMapping("/cc/read-all")
    @PreAuthorize("hasAuthority('system:approval:cc')")
    public Result<Void> readAllCc() {
        approvalService.readAllCc();
        return Result.success();
    }

    /**
     * 流程实例（全量，管理员视角）：分页 + 多条件检索。
     *
     * <p>权限独立于「我的发起」，面向需要总览/管理所有审批单的角色（超管、各业务经理）。</p>
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:approval:instance')")
    public Result<PageResult<ApprovalInstanceVO>> page(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String applicantName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(approvalService.page(title, applicantName, status, flowCode,
                beginTime, endTime, pageNo, pageSize));
    }

    /** 强制终止审批中的流程实例 */
    @PostMapping("/terminate")
    @PreAuthorize("hasAuthority('system:approval:terminate')")
    @OperationLog(module = "审批中心", operation = "终止流程实例 #{#instanceId}")
    public Result<Void> terminate(@RequestParam Long instanceId) {
        approvalService.terminate(instanceId);
        return Result.success();
    }

    /** 挂起审批中的流程实例（任务暂时退出待办，可恢复） */
    @PostMapping("/suspend")
    @PreAuthorize("hasAuthority('system:approval:suspend')")
    @OperationLog(module = "审批中心", operation = "挂起流程实例 #{#instanceId}")
    public Result<Void> suspend(@RequestParam Long instanceId) {
        approvalService.suspend(instanceId);
        return Result.success();
    }

    /** 恢复已挂起的流程实例（任务重新回到待办） */
    @PostMapping("/resume")
    @PreAuthorize("hasAuthority('system:approval:resume')")
    @OperationLog(module = "审批中心", operation = "恢复流程实例 #{#instanceId}")
    public Result<Void> resume(@RequestParam Long instanceId) {
        approvalService.resume(instanceId);
        return Result.success();
    }

    /** 删除流程实例（逻辑删除） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:approval:delete')")
    @OperationLog(module = "审批中心", operation = "删除流程实例 #{#id}")
    public Result<Void> deleteInstance(@PathVariable Long id) {
        approvalService.deleteInstance(id);
        return Result.success();
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

    /**
     * 审批单详情。
     *
     * <p>鉴权放在 <b>Service 层</b>按「申请人 / 当前候选人 / 曾处理人 / 实例总览」判定，
     * 因此这里只做粗粒度拦截：持有任一审批相关权限码即可进入，随后由 Service 精确判定。
     * 若沿用单一 {@code system:approval:query}，普通员工与审批专员打开待办详情会直接 403。</p>
     *
     * <p>⚠️ 必须包含 {@code system:approval:instance}：该码是「流程实例」总览页（菜单 218）的页面权限，
     * 持有者（含超管、各业务经理）从该页点「详情」时并不持有 todo/mine 等按钮码，漏掉此码会在粗粒度层
     * 直接 403，连 Service 都进不去。</p>
     */
    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAnyAuthority('system:approval:query', 'system:approval:todo', "
            + "'system:approval:done', 'system:approval:mine', 'system:approval:start', "
            + "'system:approval:complete', 'system:approval:instance')")
    public Result<ApprovalDetailVO> detail(@PathVariable Long id) {
        return Result.success(approvalService.detail(id));
    }
}
