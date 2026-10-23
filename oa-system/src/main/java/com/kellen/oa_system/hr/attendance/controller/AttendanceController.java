package com.kellen.oa_system.hr.attendance.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.attendance.dto.AttendancePageQuery;
import com.kellen.oa_system.hr.attendance.dto.ClockRequest;
import com.kellen.oa_system.hr.attendance.dto.ScheduleBatchRequest;
import com.kellen.oa_system.hr.attendance.dto.ShiftSaveRequest;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.hr.attendance.vo.AttendanceVO;
import com.kellen.oa_system.hr.attendance.vo.ScheduleVO;
import com.kellen.oa_system.hr.attendance.vo.ShiftVO;
import com.kellen.oa_system.hr.attendance.vo.TodayAttendanceVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤接口（班次 / 排班 / 打卡 / 明细 / 结算）。
 *
 * <p>员工自助接口（今日卡片、打卡、我的考勤）只要求登录态，
 * 但「我的考勤」菜单仍受 {@code hr:my-attendance} 权限控制，两者不冲突：
 * 接口可调不等于是功能可用，页面入口由菜单权限决定。</p>
 */
@RestController
@RequestMapping("/hr/attendance")
@Validated
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ---------------- 班次 ----------------

    @GetMapping("/shifts")
    @PreAuthorize("hasAuthority('hr:attendance')")
    public Result<List<ShiftVO>> shifts() {
        return Result.success(attendanceService.listShifts());
    }

    @PostMapping("/shift")
    @PreAuthorize("hasAuthority('hr:attendance:shift')")
    @OperationLog(module = "考勤管理", operation = "保存班次")
    public Result<Long> saveShift(@Valid @RequestBody ShiftSaveRequest request) {
        return Result.success(attendanceService.saveShift(request));
    }

    @DeleteMapping("/shift/{id}")
    @PreAuthorize("hasAuthority('hr:attendance:shift')")
    @OperationLog(module = "考勤管理", operation = "删除班次 #{#id}")
    public Result<Void> deleteShift(@PathVariable Long id) {
        attendanceService.deleteShift(id);
        return Result.success();
    }

    @PutMapping("/shift/{id}/status")
    @PreAuthorize("hasAuthority('hr:attendance:shift')")
    @OperationLog(module = "考勤管理", operation = "启停班次 #{#id}")
    public Result<Void> updateShiftStatus(@PathVariable Long id,
                                          @RequestParam
                                          @Min(value = 0, message = "状态只能是 0停用 1启用")
                                          @Max(value = 1, message = "状态只能是 0停用 1启用") Integer status) {
        attendanceService.updateShiftStatus(id, status);
        return Result.success();
    }

    // ---------------- 排班 ----------------

    @PostMapping("/schedule/batch")
    @PreAuthorize("hasAuthority('hr:attendance:schedule')")
    @OperationLog(module = "考勤管理", operation = "批量排班")
    public Result<Integer> batchSchedule(@Valid @RequestBody ScheduleBatchRequest request) {
        return Result.success(attendanceService.batchSchedule(request));
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAuthority('hr:attendance')")
    public Result<List<ScheduleVO>> schedule(@RequestParam(required = false) Long deptId,
                                             @RequestParam(required = false) String month) {
        return Result.success(attendanceService.listSchedule(deptId, month));
    }

    // ---------------- 打卡与查询 ----------------

    @GetMapping("/today")
    @PreAuthorize("isAuthenticated()")
    public Result<TodayAttendanceVO> today() {
        return Result.success(attendanceService.today());
    }

    @PostMapping("/clock")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "考勤管理", operation = "打卡")
    public Result<Void> clock(@Valid @RequestBody ClockRequest request) {
        attendanceService.clock(request);
        return Result.success();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('hr:attendance')")
    public Result<PageResult<AttendanceVO>> page(@Valid AttendancePageQuery query) {
        return Result.success(attendanceService.page(query));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AttendanceVO>> myMonth(@RequestParam(required = false) String month) {
        return Result.success(attendanceService.myMonth(month));
    }

    @GetMapping("/month")
    @PreAuthorize("hasAuthority('hr:attendance')")
    public Result<PageResult<AttendanceMonthVO>> pageMonth(@RequestParam(required = false) String month,
                                                           @RequestParam(required = false) Long deptId,
                                                           @RequestParam(required = false) Integer pageNo,
                                                           @RequestParam(required = false) Integer pageSize) {
        return Result.success(attendanceService.pageMonth(month, deptId, pageNo, pageSize));
    }

    // ---------------- 结算 ----------------

    @PostMapping("/settle")
    @PreAuthorize("hasAuthority('hr:attendance:rebuild')")
    @OperationLog(module = "考勤管理", operation = "日结 {#date}")
    public Result<Integer> settle(@RequestParam LocalDate date,
                                  @RequestParam(defaultValue = "true") boolean force) {
        return Result.success(attendanceService.settleDate(date, force));
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('hr:attendance:rebuild')")
    @OperationLog(module = "考勤管理", operation = "重算考勤 {#month}")
    public Result<Integer> rebuild(@RequestParam @NotBlank(message = "月份不能为空") String month) {
        return Result.success(attendanceService.rebuild(month));
    }

    @PostMapping("/lock")
    @PreAuthorize("hasAuthority('hr:attendance:lock')")
    @OperationLog(module = "考勤管理", operation = "考勤封账 {#month}")
    public Result<Void> lock(@RequestParam @NotBlank(message = "月份不能为空") String month) {
        attendanceService.lockMonth(month);
        return Result.success();
    }

    /**
     * 解封：仅超级管理员。封账的意义是「结果不能再变」，所以解封必须是兜底动作而非日常操作。
     */
    @PostMapping("/unlock")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(module = "考勤管理", operation = "考勤解封 {#month}")
    public Result<Void> unlock(@RequestParam @NotBlank(message = "月份不能为空") String month,
                               @RequestParam @NotBlank(message = "解封原因不能为空") String reason) {
        attendanceService.unlockMonth(month, reason);
        return Result.success();
    }
}
