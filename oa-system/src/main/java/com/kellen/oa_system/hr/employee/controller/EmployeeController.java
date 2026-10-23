package com.kellen.oa_system.hr.employee.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.employee.dto.CertSaveRequest;
import com.kellen.oa_system.hr.employee.dto.ContractSaveRequest;
import com.kellen.oa_system.hr.employee.dto.EmployeePageQuery;
import com.kellen.oa_system.hr.employee.dto.EmployeeProfileRequest;
import com.kellen.oa_system.hr.employee.service.EmployeeService;
import com.kellen.oa_system.hr.employee.vo.ChangeVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeDetailVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工档案接口。
 *
 * <p>「新增员工」复用 {@code POST /system/user}（工号生成、默认角色、初始密码都在那里），
 * 本控制器只负责查看与维护档案，避免同一张 oa_user 出现两套写入逻辑。</p>
 */
@RestController
@RequestMapping("/hr/employee")
@Validated
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('hr:employee')")
    public Result<PageResult<EmployeeVO>> page(@Valid EmployeePageQuery query) {
        return Result.success(employeeService.page(query));
    }

    /**
     * 档案详情。
     *
     * <p>只要求登录态：本人、直属上级、HR 都可以看自己的/下属的/全员的档案，
     * 具体可见性由 Service 判定（越权返回 403）。若这里也要求 {@code hr:employee}，
     * 普通员工连自己的档案都打不开——那是权限模型错位，不是安全加固。</p>
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<EmployeeDetailVO> detail(@PathVariable Long id) {
        return Result.success(employeeService.detail(id));
    }

    @PostMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('hr:employee:update')")
    @OperationLog(module = "员工档案", operation = "更新员工档案 #{#id}")
    public Result<Void> updateProfile(@PathVariable Long id,
                                      @Valid @RequestBody EmployeeProfileRequest request) {
        employeeService.updateProfile(id, request);
        return Result.success();
    }

    @GetMapping("/{id}/changes")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ChangeVO>> changes(@PathVariable Long id) {
        return Result.success(employeeService.listChanges(id));
    }

    @PostMapping("/{id}/contract")
    @PreAuthorize("hasAuthority('hr:employee:update')")
    @OperationLog(module = "员工档案", operation = "维护合同 #{#id}")
    public Result<Long> saveContract(@PathVariable Long id,
                                     @Valid @RequestBody ContractSaveRequest request) {
        return Result.success(employeeService.saveContract(id, request));
    }

    @DeleteMapping("/{id}/contract/{contractId}")
    @PreAuthorize("hasAuthority('hr:employee:update')")
    @OperationLog(module = "员工档案", operation = "删除合同 #{#contractId}")
    public Result<Void> deleteContract(@PathVariable Long id, @PathVariable Long contractId) {
        employeeService.deleteContract(id, contractId);
        return Result.success();
    }

    @PostMapping("/{id}/cert")
    @PreAuthorize("hasAuthority('hr:employee:update')")
    @OperationLog(module = "员工档案", operation = "维护证照 #{#id}")
    public Result<Long> saveCert(@PathVariable Long id, @Valid @RequestBody CertSaveRequest request) {
        return Result.success(employeeService.saveCert(id, request));
    }

    @DeleteMapping("/{id}/cert/{certId}")
    @PreAuthorize("hasAuthority('hr:employee:update')")
    @OperationLog(module = "员工档案", operation = "删除证照 #{#certId}")
    public Result<Void> deleteCert(@PathVariable Long id, @PathVariable Long certId) {
        employeeService.deleteCert(id, certId);
        return Result.success();
    }

    /**
     * 花名册导出。直接写响应流，因此不返回 {@code Result}。
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('hr:employee:export')")
    @OperationLog(module = "员工档案", operation = "导出花名册")
    public void export(@Valid EmployeePageQuery query, HttpServletResponse response) {
        employeeService.export(response, query);
    }
}
