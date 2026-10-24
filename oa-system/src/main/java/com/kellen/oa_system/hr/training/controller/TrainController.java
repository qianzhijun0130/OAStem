package com.kellen.oa_system.hr.training.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.hr.training.dto.TrainCourseSaveRequest;
import com.kellen.oa_system.hr.training.dto.TrainEnrollBatchRequest;
import com.kellen.oa_system.hr.training.dto.TrainScoreRequest;
import com.kellen.oa_system.hr.training.dto.TrainSessionSaveRequest;
import com.kellen.oa_system.hr.training.service.TrainService;
import com.kellen.oa_system.hr.training.vo.TrainCourseVO;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import com.kellen.oa_system.hr.training.vo.TrainStatisticVO;
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
 * 培训管理接口。
 *
 * <p>权限分三档：</p>
 * <ul>
 *   <li><b>登录即可</b>：报名、取消、签到、我的培训——都是「本人的事」；</li>
 *   <li><b>HR</b>：课程、期次、指派、成绩、统计；</li>
 *   <li><b>签到</b>：只能本人签到（服务层再校验 userId）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/hr/train")
@RequiredArgsConstructor
public class TrainController {

    private final TrainService trainService;

    // ================== 课程 ==================

    @GetMapping("/course/page")
    @PreAuthorize("hasAuthority('hr:training')")
    public Result<PageResult<TrainCourseVO>> pageCourses(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer type,
                                                          @RequestParam(required = false) Integer required,
                                                          @RequestParam(required = false) Integer pageNo,
                                                          @RequestParam(required = false) Integer pageSize) {
        return Result.success(trainService.pageCourses(keyword, type, required, pageNo, pageSize));
    }

    @GetMapping("/course/{id}")
    @PreAuthorize("hasAuthority('hr:training')")
    public Result<TrainCourseVO> courseDetail(@PathVariable Long id) {
        return Result.success(trainService.courseDetail(id));
    }

    @PostMapping("/course")
    @PreAuthorize("hasAuthority('hr:training:course')")
    @OperationLog(module = "培训管理", operation = "保存培训课程 {#request.courseName}")
    public Result<Long> saveCourse(@Valid @RequestBody TrainCourseSaveRequest request) {
        return Result.success(trainService.saveCourse(request));
    }

    @DeleteMapping("/course/{id}")
    @PreAuthorize("hasAuthority('hr:training:course')")
    @OperationLog(module = "培训管理", operation = "删除培训课程 {#id}")
    public Result<Void> deleteCourse(@PathVariable Long id) {
        trainService.deleteCourse(id);
        return Result.success();
    }

    // ================== 期次 ==================

    @GetMapping("/session/page")
    @PreAuthorize("hasAuthority('hr:training')")
    public Result<PageResult<TrainSessionVO>> pageSessions(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Long courseId,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(required = false) Integer pageNo,
                                                            @RequestParam(required = false) Integer pageSize) {
        return Result.success(trainService.pageSessions(keyword, courseId, status, pageNo, pageSize));
    }

    @GetMapping("/session/{id}")
    @PreAuthorize("hasAuthority('hr:training')")
    public Result<TrainSessionVO> sessionDetail(@PathVariable Long id) {
        return Result.success(trainService.sessionDetail(id));
    }

    @PostMapping("/session")
    @PreAuthorize("hasAuthority('hr:training:session')")
    @OperationLog(module = "培训管理", operation = "保存培训期次 {#request.sessionName}")
    public Result<Long> saveSession(@Valid @RequestBody TrainSessionSaveRequest request) {
        return Result.success(trainService.saveSession(request));
    }

    @PostMapping("/session/{id}/cancel")
    @PreAuthorize("hasAuthority('hr:training:session')")
    @OperationLog(module = "培训管理", operation = "取消培训期次 {#id}")
    public Result<Void> cancelSession(@PathVariable Long id) {
        trainService.cancelSession(id);
        return Result.success();
    }

    // ================== 报名 ==================

    @GetMapping("/session/{id}/enrolls")
    @PreAuthorize("hasAuthority('hr:training')")
    public Result<List<TrainEnrollVO>> listEnrolls(@PathVariable Long id) {
        return Result.success(trainService.listEnrolls(id));
    }

    @PostMapping("/session/{id}/enroll")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "培训管理", operation = "报名培训期次 {#id}")
    public Result<TrainEnrollVO> enroll(@PathVariable Long id) {
        return Result.success(trainService.enroll(id));
    }

    @PostMapping("/session/enroll/batch")
    @PreAuthorize("hasAuthority('hr:training:enroll')")
    @OperationLog(module = "培训管理", operation = "HR 指派培训报名 {#request.sessionId}")
    public Result<Integer> enrollBatch(@Valid @RequestBody TrainEnrollBatchRequest request) {
        return Result.success(trainService.enrollBatch(request));
    }

    @PostMapping("/enroll/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "培训管理", operation = "取消培训报名 {#id}")
    public Result<Void> cancel(@PathVariable Long id) {
        trainService.cancel(id);
        return Result.success();
    }

    @PostMapping("/enroll/{id}/checkin")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "培训管理", operation = "培训签到 {#id}")
    public Result<Void> checkin(@PathVariable Long id) {
        trainService.checkin(id);
        return Result.success();
    }

    @PostMapping("/enroll/score")
    @PreAuthorize("hasAuthority('hr:training:score')")
    @OperationLog(module = "培训管理", operation = "录入培训成绩 {#request.enrollId}")
    public Result<Void> score(@Valid @RequestBody TrainScoreRequest request) {
        trainService.score(request);
        return Result.success();
    }

    // ================== 我的培训 ==================

    @GetMapping("/my/page")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<TrainEnrollVO>> myEnrolls(@RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Integer pageNo,
                                                        @RequestParam(required = false) Integer pageSize) {
        return Result.success(trainService.myEnrolls(status, pageNo, pageSize));
    }

    @GetMapping("/my/available")
    @PreAuthorize("isAuthenticated()")
    public Result<List<TrainSessionVO>> myAvailable() {
        return Result.success(trainService.myAvailableSessions());
    }

    // ================== 统计 ==================

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('hr:training:stat')")
    public Result<TrainStatisticVO> statistics(@RequestParam(required = false) String year) {
        return Result.success(trainService.statistics(year));
    }
}
