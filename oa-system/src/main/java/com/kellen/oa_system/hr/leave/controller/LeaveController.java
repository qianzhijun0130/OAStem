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
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /**
     * 销假（提前返岗登记）。
     *
     * <p>权限只要求登录：本人可以给自己的单子销假，HR 可以代为登记（服务层判定）。
     * 用 PUT 而不是 POST——销假是「修正已有的请假事实」，不是新建资源。</p>
     */
    @PutMapping("/{id}/return")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "假勤管理", operation = "销假 {#id}")
    public Result<Void> returnFromLeave(@PathVariable Long id,
                                        @RequestParam @NotNull(message = "返岗日期不能为空")
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
                                        @RequestParam(required = false) String remark) {
        leaveService.returnFromLeave(id, returnDate, remark);
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

    /**
     * 导出请假记录。直接写响应流，因此不返回 {@code Result}。
     *
     * <p>与列表共用筛选条件（月份/假别/状态/部门/关键字/员工）与同一套数据范围，
     * 避免「列表看得到、导出却没有」这类权限穿透。</p>
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('hr:leave:export')")
    @OperationLog(module = "假勤管理", operation = "导出请假记录")
    public void export(@Valid LeavePageQuery query, HttpServletResponse response) {
        leaveService.export(query, response);
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
