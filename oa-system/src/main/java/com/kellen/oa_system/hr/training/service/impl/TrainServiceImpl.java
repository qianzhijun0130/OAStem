package com.kellen.oa_system.hr.training.service.impl;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.training.dto.TrainCourseSaveRequest;
import com.kellen.oa_system.hr.training.dto.TrainEnrollBatchRequest;
import com.kellen.oa_system.hr.training.dto.TrainScoreRequest;
import com.kellen.oa_system.hr.training.dto.TrainSessionSaveRequest;
import com.kellen.oa_system.hr.training.service.TrainService;
import com.kellen.oa_system.hr.training.vo.TrainCourseVO;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import com.kellen.oa_system.hr.training.vo.TrainStatisticVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 培训管理门面（BE-OPT-08 拆分后）。
 *
 * <p>本类只做两件事：<b>持有事务边界</b>、<b>按职责把调用转给四个协作类</b>。
 * 对外签名、返回值、异常码、事务注解位置全部保持拆分前不变（Controller 与调用方零改动）。</p>
 *
 * <p>为什么事务留在门面而不是内层：内层方法之间也会互相调用（如报名域调期次支撑），
 * 事务注解一旦下沉，自调用就失效，边界变得需要逐处推理；留在门面则一个动作一个事务，肉眼可查。</p>
 *
 * <p><b>四个协作类的职责边界</b>：</p>
 * <ul>
 *   <li>{@link TrainCourseService}：课程（培训的根）</li>
 *   <li>{@link TrainSessionService}：期次（课程 + 时间 + 名额）</li>
 *   <li>{@link TrainEnrollService}：报名 / 指派 / 签到 / 成绩（三条业务规则都在这里）</li>
 *   <li>{@link TrainReportService}：统计</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TrainServiceImpl implements TrainService {

    private final TrainCourseService courseService;
    private final TrainSessionService sessionService;
    private final TrainEnrollService enrollService;
    private final TrainEnrollQueryService enrollQueryService;
    private final TrainReportService reportService;

    // ==================================================================
    // 课程
    // ==================================================================

    @Override
    public PageResult<TrainCourseVO> pageCourses(String keyword, Integer type, Integer required,
                                                 Integer pageNo, Integer pageSize) {
        return courseService.pageCourses(keyword, type, required, pageNo, pageSize);
    }

    @Override
    public TrainCourseVO courseDetail(Long id) {
        return courseService.courseDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCourse(TrainCourseSaveRequest request) {
        return courseService.saveCourse(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long id) {
        courseService.deleteCourse(id);
    }

    // ==================================================================
    // 期次
    // ==================================================================

    @Override
    public PageResult<TrainSessionVO> pageSessions(String keyword, Long courseId, Integer status,
                                                   Integer pageNo, Integer pageSize) {
        return sessionService.pageSessions(keyword, courseId, status, pageNo, pageSize);
    }

    @Override
    public TrainSessionVO sessionDetail(Long id) {
        return sessionService.sessionDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSession(TrainSessionSaveRequest request) {
        return sessionService.saveSession(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelSession(Long id) {
        sessionService.cancelSession(id);
    }

    // ==================================================================
    // 报名 / 签到 / 成绩
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainEnrollVO enroll(Long sessionId) {
        return enrollService.enroll(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enrollBatch(TrainEnrollBatchRequest request) {
        return enrollService.enrollBatch(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long enrollId) {
        enrollService.cancel(enrollId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long enrollId) {
        enrollService.checkin(enrollId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void score(TrainScoreRequest request) {
        enrollService.score(request);
    }

    @Override
    public List<TrainEnrollVO> listEnrolls(Long sessionId) {
        return enrollQueryService.listEnrolls(sessionId);
    }

    @Override
    public PageResult<TrainEnrollVO> myEnrolls(Integer status, Integer pageNo, Integer pageSize) {
        return enrollQueryService.myEnrolls(status, pageNo, pageSize);
    }

    @Override
    public List<TrainSessionVO> myAvailableSessions() {
        return enrollQueryService.myAvailableSessions();
    }

    // ==================================================================
    // 统计
    // ==================================================================

    @Override
    public TrainStatisticVO statistics(String year) {
        return reportService.statistics(year);
    }
}
