package com.kellen.oa_system.hr.performance.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.performance.dto.PerfAppealHandleRequest;
import com.kellen.oa_system.hr.performance.dto.PerfAppealRequest;
import com.kellen.oa_system.hr.performance.dto.PerfCalibrateRequest;
import com.kellen.oa_system.hr.performance.dto.PerfCycleSaveRequest;
import com.kellen.oa_system.hr.performance.dto.PerfIndicatorSaveRequest;
import com.kellen.oa_system.hr.performance.dto.PerfPlanSaveRequest;
import com.kellen.oa_system.hr.performance.dto.PerfScoreRequest;
import com.kellen.oa_system.hr.performance.dto.PerfSheetPageQuery;
import com.kellen.oa_system.hr.performance.dto.PerfTargetSaveRequest;
import com.kellen.oa_system.hr.performance.service.PerfCycleService;
import com.kellen.oa_system.hr.performance.service.PerfPlanService;
import com.kellen.oa_system.hr.performance.service.PerfScoreService;
import com.kellen.oa_system.hr.performance.vo.PerfAppealVO;
import com.kellen.oa_system.hr.performance.vo.PerfCycleVO;
import com.kellen.oa_system.hr.performance.vo.PerfDistributionVO;
import com.kellen.oa_system.hr.performance.vo.PerfIndicatorVO;
import com.kellen.oa_system.hr.performance.vo.PerfPlanVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetDetailVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 绩效管理接口。
 *
 * <p>权限分三档：</p>
 * <ul>
 *   <li><b>登录即可</b>：我的考核表、目标提交、自评、确认、申诉——都是「本人的事」；</li>
 *   <li><b>上级</b>：目标审核、上级评分——服务层再校验「是否直属上级」；</li>
 *   <li><b>人事</b>：方案、指标、周期、校准、申诉处理、结果与分布、导出。</li>
 * </ul>
 */
@RestController
@RequestMapping("/hr/perf")
@RequiredArgsConstructor
public class PerfController {

    private final PerfPlanService planService;
    private final PerfCycleService cycleService;
    private final PerfScoreService scoreService;

    // ================== 方案 ==================

    @GetMapping("/plan/page")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<PageResult<PerfPlanVO>> pagePlans(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Integer pageNo,
                                                    @RequestParam(required = false) Integer pageSize) {
        return Result.success(planService.pagePlans(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/plan/{id}")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<PerfPlanVO> planDetail(@PathVariable Long id) {
        return Result.success(planService.detail(id));
    }

    @PostMapping("/plan")
    @PreAuthorize("hasAuthority('hr:performance:plan')")
    @OperationLog(module = "绩效管理", operation = "保存绩效方案 {#request.planName}")
    public Result<Long> savePlan(@Valid @RequestBody PerfPlanSaveRequest request) {
        return Result.success(planService.savePlan(request));
    }

    @DeleteMapping("/plan/{id}")
    @PreAuthorize("hasAuthority('hr:performance:plan')")
    @OperationLog(module = "绩效管理", operation = "删除绩效方案 {#id}")
    public Result<Void> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return Result.success();
    }

    @GetMapping("/plan/options")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<List<PerfPlanVO>> planOptions() {
        return Result.success(planService.planOptions());
    }

    // ================== 指标库 ==================

    @GetMapping("/indicator/page")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<PageResult<PerfIndicatorVO>> pageIndicators(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer type,
                                                              @RequestParam(required = false) Integer pageNo,
                                                              @RequestParam(required = false) Integer pageSize) {
        return Result.success(planService.pageIndicators(keyword, type, pageNo, pageSize));
    }

    @PostMapping("/indicator")
    @PreAuthorize("hasAuthority('hr:performance:indicator')")
    @OperationLog(module = "绩效管理", operation = "保存绩效指标 {#request.indicatorName}")
    public Result<Long> saveIndicator(@Valid @RequestBody PerfIndicatorSaveRequest request) {
        return Result.success(planService.saveIndicator(request));
    }

    @DeleteMapping("/indicator/{id}")
    @PreAuthorize("hasAuthority('hr:performance:indicator')")
    @OperationLog(module = "绩效管理", operation = "删除绩效指标 {#id}")
    public Result<Void> deleteIndicator(@PathVariable Long id) {
        planService.deleteIndicator(id);
        return Result.success();
    }

    @GetMapping("/indicator/options")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<List<PerfIndicatorVO>> indicatorOptions() {
        return Result.success(planService.indicatorOptions());
    }

    // ================== 周期 ==================

    @GetMapping("/cycle/page")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<PageResult<PerfCycleVO>> pageCycles(@RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) Integer pageNo,
                                                      @RequestParam(required = false) Integer pageSize) {
        return Result.success(cycleService.pageCycles(status, pageNo, pageSize));
    }

    @GetMapping("/cycle/{id}")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<PerfCycleVO> cycleDetail(@PathVariable Long id) {
        return Result.success(cycleService.detail(id));
    }

    @PostMapping("/cycle")
    @PreAuthorize("hasAuthority('hr:performance:plan')")
    @OperationLog(module = "绩效管理", operation = "保存绩效周期 {#request.cycleName}")
    public Result<Long> saveCycle(@Valid @RequestBody PerfCycleSaveRequest request) {
        return Result.success(cycleService.saveCycle(request));
    }

    @PostMapping("/cycle/{id}/generate")
    @PreAuthorize("hasAuthority('hr:performance:plan')")
    @OperationLog(module = "绩效管理", operation = "补齐考核表 {#id}")
    public Result<Integer> generate(@PathVariable Long id) {
        return Result.success(cycleService.generateSheets(id));
    }

    @PostMapping("/cycle/{id}/advance")
    @PreAuthorize("hasAuthority('hr:performance:plan')")
    @OperationLog(module = "绩效管理", operation = "推进绩效阶段 {#id}")
    public Result<Integer> advance(@PathVariable Long id) {
        return Result.success(cycleService.advance(id));
    }

    @PostMapping("/cycle/{id}/archive")
    @PreAuthorize("hasAuthority('hr:performance:calibrate')")
    @OperationLog(module = "绩效管理", operation = "归档绩效周期 {#id}")
    public Result<Integer> archive(@PathVariable Long id) {
        return Result.success(cycleService.archive(id));
    }

    @DeleteMapping("/cycle/{id}")
    @PreAuthorize("hasAuthority('hr:performance:plan')")
    @OperationLog(module = "绩效管理", operation = "删除绩效周期 {#id}")
    public Result<Void> deleteCycle(@PathVariable Long id) {
        cycleService.deleteCycle(id);
        return Result.success();
    }

    // ================== 考核表（本人 / 上级 / HR） ==================

    @GetMapping("/sheet/my")
    @PreAuthorize("isAuthenticated()")
    public Result<PerfSheetDetailVO> mySheet(@RequestParam Long cycleId) {
        return Result.success(scoreService.mySheet(cycleId));
    }

    @GetMapping("/my/page")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<PerfSheetVO>> myHistory(@RequestParam(required = false) Integer pageNo,
                                                     @RequestParam(required = false) Integer pageSize) {
        return Result.success(scoreService.myHistory(pageNo, pageSize));
    }

    @PostMapping("/target/my")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "绩效管理", operation = "提交绩效目标 {#request.sheetId}")
    public Result<Void> saveTarget(@Valid @RequestBody PerfTargetSaveRequest request) {
        scoreService.saveTarget(request);
        return Result.success();
    }

    @PostMapping("/target/{sheetId}/approve")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "绩效管理", operation = "审核绩效目标 {#sheetId}")
    public Result<Void> approveTarget(@PathVariable Long sheetId,
                                      @RequestParam Boolean pass,
                                      @RequestParam(required = false) String opinion) {
        scoreService.approveTarget(sheetId, pass, opinion);
        return Result.success();
    }

    @PostMapping("/score/self")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "绩效管理", operation = "提交绩效自评 {#request.sheetId}")
    public Result<Void> submitSelf(@Valid @RequestBody PerfScoreRequest request) {
        scoreService.submitSelf(request);
        return Result.success();
    }

    @PostMapping("/score/manager")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "绩效管理", operation = "上级绩效评分 {#request.sheetId}")
    public Result<Void> submitManager(@Valid @RequestBody PerfScoreRequest request) {
        scoreService.submitManager(request);
        return Result.success();
    }

    @PostMapping("/score/calibrate")
    @PreAuthorize("hasAuthority('hr:performance:calibrate')")
    @OperationLog(module = "绩效管理", operation = "绩效校准 {#request.sheetId}")
    public Result<Void> calibrate(@Valid @RequestBody PerfCalibrateRequest request) {
        scoreService.calibrate(request);
        return Result.success();
    }

    @PostMapping("/score/{sheetId}/confirm")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "绩效管理", operation = "确认绩效结果 {#sheetId}")
    public Result<Void> confirm(@PathVariable Long sheetId) {
        scoreService.confirm(sheetId);
        return Result.success();
    }

    @PostMapping("/appeal")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "绩效管理", operation = "提交绩效申诉 {#request.sheetId}")
    public Result<Void> appeal(@Valid @RequestBody PerfAppealRequest request) {
        scoreService.appeal(request);
        return Result.success();
    }

    @PostMapping("/appeal/handle")
    @PreAuthorize("hasAuthority('hr:performance:calibrate')")
    @OperationLog(module = "绩效管理", operation = "处理绩效申诉 {#request.appealId}")
    public Result<Void> handleAppeal(@Valid @RequestBody PerfAppealHandleRequest request) {
        scoreService.handleAppeal(request);
        return Result.success();
    }

    @GetMapping("/appeal/pending")
    @PreAuthorize("hasAuthority('hr:performance:calibrate')")
    public Result<List<PerfAppealVO>> pendingAppeals(@RequestParam(required = false) Long cycleId) {
        return Result.success(scoreService.pendingAppeals(cycleId));
    }

    @GetMapping("/sheet/page")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<PageResult<PerfSheetVO>> pageSheets(@Valid PerfSheetPageQuery query) {
        return Result.success(scoreService.pageSheets(query));
    }

    @GetMapping("/team/page")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<PerfSheetVO>> pageTeam(@Valid PerfSheetPageQuery query) {
        return Result.success(scoreService.pageTeam(query));
    }

    @GetMapping("/sheet/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<PerfSheetDetailVO> sheetDetail(@PathVariable Long id) {
        return Result.success(scoreService.detail(id));
    }

    @GetMapping("/distribution")
    @PreAuthorize("hasAuthority('hr:performance')")
    public Result<List<PerfDistributionVO>> distribution(@RequestParam Long cycleId) {
        return Result.success(scoreService.distribution(cycleId));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('hr:performance:export')")
    @OperationLog(module = "绩效管理", operation = "导出绩效结果 {#cycleId}")
    public void export(@RequestParam Long cycleId, HttpServletResponse response) {
        scoreService.export(cycleId, response);
    }
}
