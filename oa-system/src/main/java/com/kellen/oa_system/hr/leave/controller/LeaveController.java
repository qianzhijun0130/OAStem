package com.kellen.oa_system.hr.leave.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.leave.dto.LeavePageQuery;
import com.kellen.oa_system.hr.leave.dto.LeaveSubmitRequest;
import com.kellen.oa_system.hr.leave.dto.QuotaAdjustRequest;
import com.kellen.oa_system.hr.leave.service.LeaveQuotaService;
import com.kellen.oa_system.hr.leave.service.LeaveService;
import com.kellen.oa_system.hr.leave.vo.LeaveQuotaVO;
import com.kellen.oa_system.hr.leave.vo.LeaveVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 假勤（请假与额度）接口。
 *
 * <p>发起/撤销/我的/详情只要求登录态：普通员工也能请假，页面入口由菜单权限控制。
 * HR 专属的「全量列表」与「额度调整」才校验权限码。</p>
 */
@RestController
@RequestMapping("/hr/leave")
@Validated
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final LeaveQuotaService quotaService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "假勤管理", operation = "提交请假")
    public Result<Long> submit(@Valid @RequestBody LeaveSubmitRequest request) {
        return Result.success(leaveService.submit(request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "假勤管理", operation = "撤销请假 #{#id}")
    public Result<Void> cancel(@PathVariable Long id) {
        leaveService.cancel(id);
        return Result.success();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('hr:leave')")
    public Result<PageResult<LeaveVO>> page(@Valid LeavePageQuery query) {
        return Result.success(leaveService.page(query));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LeaveVO>> mine(@RequestParam(required = false) Integer leaveStatus) {
        return Result.success(leaveService.mine(leaveStatus));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<LeaveVO> detail(@PathVariable Long id) {
        return Result.success(leaveService.detail(id));
    }

    @GetMapping("/quota")
    @PreAuthorize("hasAuthority('hr:leave')")
    public Result<List<LeaveQuotaVO>> quota(@RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) Long deptId) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        return Result.success(quotaService.listByYear(targetYear, deptId));
    }

    @GetMapping("/quota/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LeaveQuotaVO>> myQuota(@RequestParam(required = false) Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        return Result.success(quotaService.my(targetYear));
    }

    @PostMapping("/quota/adjust")
    @PreAuthorize("hasAuthority('hr:leave:quota')")
    @OperationLog(module = "假勤管理", operation = "调整假期额度")
    public Result<Void> adjust(@Valid @RequestBody QuotaAdjustRequest request) {
        quotaService.adjust(request);
        return Result.success();
    }
}
