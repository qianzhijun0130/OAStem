package com.kellen.oa_system.hr.training.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.training.dto.TrainCourseSaveRequest;
import com.kellen.oa_system.hr.training.dto.TrainEnrollBatchRequest;
import com.kellen.oa_system.hr.training.dto.TrainScoreRequest;
import com.kellen.oa_system.hr.training.dto.TrainSessionSaveRequest;
import com.kellen.oa_system.hr.training.vo.TrainCourseVO;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import com.kellen.oa_system.hr.training.vo.TrainStatisticVO;

import java.util.List;

/**
 * 培训管理。
 *
 * <p>链路：课程 → 开课 → 报名 → 签到 → 考核 → 结业 → 入档。</p>
 *
 * <p><b>学时按签到算，不按报名算</b>——报名只是占个位子，
 * 没来听的课不该计入学时，否则统计出来的培训时长全是虚的。</p>
 */
public interface TrainService {

    // ---------- 课程 ----------

    PageResult<TrainCourseVO> pageCourses(String keyword, Integer type, Integer required,
                                           Integer pageNo, Integer pageSize);

    TrainCourseVO courseDetail(Long id);

    Long saveCourse(TrainCourseSaveRequest request);

    void deleteCourse(Long id);

    // ---------- 期次 ----------

    PageResult<TrainSessionVO> pageSessions(String keyword, Long courseId, Integer status,
                                             Integer pageNo, Integer pageSize);

    TrainSessionVO sessionDetail(Long id);

    Long saveSession(TrainSessionSaveRequest request);

    void cancelSession(Long id);

    // ---------- 报名 ----------

    /** 员工自助报名（名额满则进候补） */
    TrainEnrollVO enroll(Long sessionId);

    /** HR 批量指派（可超额，须填原因） */
    int enrollBatch(TrainEnrollBatchRequest request);

    /** 取消报名：开课前 24 小时内取消计爽约 */
    void cancel(Long enrollId);

    /** 签到（按签到时间算实际学时） */
    void checkin(Long enrollId);

    /** 录入考核成绩与结业 */
    void score(TrainScoreRequest request);

    /** 某期次的报名名单 */
    List<TrainEnrollVO> listEnrolls(Long sessionId);

    /** 我的培训记录 */
    PageResult<TrainEnrollVO> myEnrolls(Integer status, Integer pageNo, Integer pageSize);

    /** 我的可报名期次 */
    List<TrainSessionVO> myAvailableSessions();

    // ---------- 统计 ----------

    TrainStatisticVO statistics(String year);
}
