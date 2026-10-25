package com.kellen.oa_system.hr.leave.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.leave.dto.LeavePolicySaveRequest;
import com.kellen.oa_system.hr.leave.service.LeavePolicyService;
import com.kellen.oa_system.hr.leave.vo.LeavePolicyVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 地方假别政策维护接口。
 *
 * <p>政策只影响「婚假/产假/陪产假/丧假」的天数上限，属于 HR 配置权限（{@code hr:leave:policy}），
 * 与请假申请本身的登录即可用不同。</p>
 */
@RestController
@RequestMapping("/hr/leave/policy")
@Validated
@RequiredArgsConstructor
public class LeavePolicyController {

    private final LeavePolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAuthority('hr:leave')")
    public Result<List<LeavePolicyVO>> list(@RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) String city) {
        return Result.success(policyService.list(year, city));
    }

    /** 可配置政策的假别下拉数据 */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('hr:leave')")
    public Result<List<Map<String, Object>>> types() {
        return Result.success(policyService.policyTypes());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('hr:leave:policy')")
    @OperationLog(module = "假勤管理", operation = "保存地方假别政策")
    public Result<Long> save(@Valid @RequestBody LeavePolicySaveRequest request) {
        return Result.success(policyService.save(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('hr:leave:policy')")
    @OperationLog(module = "假勤管理", operation = "删除地方假别政策 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        policyService.delete(id);
        return Result.success();
    }
}
