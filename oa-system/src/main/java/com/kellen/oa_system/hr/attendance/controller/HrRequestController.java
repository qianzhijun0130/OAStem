package com.kellen.oa_system.hr.attendance.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.attendance.dto.HrRequestSaveRequest;
import com.kellen.oa_system.hr.attendance.service.HrRequestService;
import com.kellen.oa_system.hr.attendance.vo.HrRequestVO;
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

import java.util.List;

/**
 * 加班 / 出差 / 补卡接口。
 *
 * <p>三类申请共用一套路径，用 {@code bizType} 区分（1加班 2出差 3补卡）：
 * 它们的数据形状与流程几乎一致，拆成三套 Controller 只会重复三遍相同代码。</p>
 */
@RestController
@RequestMapping("/hr/request")
@Validated
@RequiredArgsConstructor
public class HrRequestController {

    private final HrRequestService hrRequestService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "假勤管理", operation = "提交加班/出差/补卡申请")
    public Result<Long> submit(@Valid @RequestBody HrRequestSaveRequest request) {
        return Result.success(hrRequestService.submit(request));
    }

    @PostMapping("/{bizType}/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "假勤管理", operation = "撤销假勤申请 #{#bizType}-#{#id}")
    public Result<Void> cancel(@PathVariable Integer bizType, @PathVariable Long id) {
        hrRequestService.cancel(bizType, id);
        return Result.success();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyAuthority('hr:overtime', 'hr:trip', 'hr:card')")
    public Result<PageResult<HrRequestVO>> page(@RequestParam Integer bizType,
                                                @RequestParam(required = false) String month,
                                                @RequestParam(required = false) Long deptId,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer pageNo,
                                                @RequestParam(required = false) Integer pageSize) {
        return Result.success(hrRequestService.page(bizType, month, deptId, status, keyword, pageNo, pageSize));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<HrRequestVO>> mine(@RequestParam Integer bizType) {
        return Result.success(hrRequestService.mine(bizType));
    }
}
