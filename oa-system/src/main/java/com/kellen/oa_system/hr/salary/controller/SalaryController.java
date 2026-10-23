package com.kellen.oa_system.hr.salary.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.salary.dto.SalaryAdjustRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryItemSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryPageQuery;
import com.kellen.oa_system.hr.salary.dto.SalaryProfileSaveRequest;
import com.kellen.oa_system.hr.salary.dto.SocialPolicySaveRequest;
import com.kellen.oa_system.hr.salary.service.SalaryService;
import com.kellen.oa_system.hr.salary.vo.SalaryItemVO;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.hr.salary.vo.SalaryProfileVO;
import com.kellen.oa_system.hr.salary.vo.SalaryRecordVO;
import com.kellen.oa_system.hr.salary.vo.SocialPolicyVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * 薪酬管理接口。
 *
 * <p>权限划分刻意细：核算（calc）/ 复核（review）/ 封账（lock）/ 发放（pay）是四个独立权限码，
 * 因为现实中这四步通常由不同的人做——一个人能算也能发，等于没有复核。</p>
 *
 * <p>工资条接口只要求登录：本人看自己的，不需要任何 HR 权限码。</p>
 */
@RestController
@RequestMapping("/hr/salary")
@RequiredArgsConstructor
@Validated
public class SalaryController {

    private final SalaryService salaryService;

    // ================== 薪资项与社保政策 ==================

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    public Result<List<SalaryItemVO>> listItems() {
        return Result.success(salaryService.listItems());
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    @OperationLog(module = "薪酬管理", operation = "保存薪资项 {#request.itemName}")
    public Result<Long> saveItem(@Valid @RequestBody SalaryItemSaveRequest request) {
        return Result.success(salaryService.saveItem(request));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    @OperationLog(module = "薪酬管理", operation = "删除薪资项 {#id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        salaryService.deleteItem(id);
        return Result.success();
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    public Result<List<SocialPolicyVO>> listPolicies(@RequestParam(required = false) Integer year) {
        return Result.success(salaryService.listPolicies(year));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    @OperationLog(module = "薪酬管理", operation = "保存社保政策 {#request.city}")
    public Result<Long> savePolicy(@Valid @RequestBody SocialPolicySaveRequest request) {
        return Result.success(salaryService.savePolicy(request));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    @OperationLog(module = "薪酬管理", operation = "删除社保政策 {#id}")
    public Result<Void> deletePolicy(@PathVariable Long id) {
        salaryService.deletePolicy(id);
        return Result.success();
    }

    // ================== 薪资档案 ==================

    @GetMapping("/profiles")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    public Result<PageResult<SalaryProfileVO>> pageProfiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(salaryService.pageProfiles(keyword, deptId, pageNo, pageSize));
    }

    @PostMapping("/profiles")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    @OperationLog(module = "薪酬管理", operation = "维护薪资档案")
    public Result<Long> saveProfile(@Valid @RequestBody SalaryProfileSaveRequest request) {
        return Result.success(salaryService.saveProfile(request));
    }

    @GetMapping("/profiles/{userId}/history")
    @PreAuthorize("hasAuthority('hr:salary:profile')")
    public Result<List<SalaryProfileVO>> profileHistory(@PathVariable Long userId) {
        return Result.success(salaryService.profileHistory(userId));
    }

    // ================== 月度核算 ==================

    @PostMapping("/calc")
    @PreAuthorize("hasAuthority('hr:salary:calc')")
    @OperationLog(module = "薪酬管理", operation = "核算薪资 {#month}")
    public Result<Integer> calculate(@RequestParam @NotBlank(message = "月份不能为空") String month,
                                     @RequestParam(required = false) Long deptId) {
        return Result.success(salaryService.calculate(month, deptId));
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('hr:salary:calc')")
    public Result<PageResult<SalaryRecordVO>> pageRecords(@Valid SalaryPageQuery query) {
        return Result.success(salaryService.pageRecords(query));
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("hasAuthority('hr:salary:calc')")
    public Result<SalaryPayslipVO> detail(@PathVariable Long id) {
        return Result.success(salaryService.detail(id));
    }

    @PutMapping("/records/{id}/adjust")
    @PreAuthorize("hasAuthority('hr:salary:calc')")
    @OperationLog(module = "薪酬管理", operation = "调整薪资 {#id}")
    public Result<Void> adjust(@PathVariable Long id, @Valid @RequestBody SalaryAdjustRequest request) {
        salaryService.adjust(id, request);
        return Result.success();
    }

    @PostMapping("/review")
    @PreAuthorize("hasAuthority('hr:salary:review')")
    @OperationLog(module = "薪酬管理", operation = "复核薪资 {#month}")
    public Result<Integer> review(@RequestParam @NotBlank(message = "月份不能为空") String month,
                                  @RequestParam(required = false) Long deptId) {
        return Result.success(salaryService.review(month, deptId));
    }

    @PostMapping("/lock")
    @PreAuthorize("hasAuthority('hr:salary:lock')")
    @OperationLog(module = "薪酬管理", operation = "薪资封账 {#month}")
    public Result<Integer> lock(@RequestParam @NotBlank(message = "月份不能为空") String month,
                                @RequestParam(required = false) Long deptId) {
        return Result.success(salaryService.lock(month, deptId));
    }

    @PostMapping("/pay")
    @PreAuthorize("hasAuthority('hr:salary:pay')")
    @OperationLog(module = "薪酬管理", operation = "薪资发放 {#month}")
    public Result<Integer> pay(@RequestParam @NotBlank(message = "月份不能为空") String month,
                               @RequestParam(required = false) Long deptId) {
        return Result.success(salaryService.pay(month, deptId));
    }

    // ================== 工资条（本人） ==================

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SalaryPayslipVO>> myPayslips(@RequestParam(required = false) Integer year) {
        return Result.success(salaryService.myPayslips(year));
    }

    @GetMapping("/my/{month}")
    @PreAuthorize("isAuthenticated()")
    public Result<SalaryPayslipVO> myPayslip(@PathVariable String month) {
        return Result.success(salaryService.myPayslip(month));
    }
}
