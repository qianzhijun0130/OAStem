package com.kellen.oa_system.hr.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.training.dto.TrainCourseSaveRequest;
import com.kellen.oa_system.hr.training.dto.TrainEnrollBatchRequest;
import com.kellen.oa_system.hr.training.dto.TrainScoreRequest;
import com.kellen.oa_system.hr.training.dto.TrainSessionSaveRequest;
import com.kellen.oa_system.hr.training.entity.TrainAbsence;
import com.kellen.oa_system.hr.training.entity.TrainCourse;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainAbsenceMapper;
import com.kellen.oa_system.hr.training.mapper.TrainCourseMapper;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.service.TrainService;
import com.kellen.oa_system.hr.training.vo.TrainCourseVO;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import com.kellen.oa_system.hr.training.vo.TrainStatisticVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 培训管理实现。
 *
 * <p><b>三个最容易做错的地方</b>：</p>
 * <ol>
 *   <li><b>学时按签到算</b>：报名不算学时，只报名不签到 → 学时 0。</li>
 *   <li><b>名额满 → 候补</b>：普通员工报名进候补；HR 指派可超额但必须填原因。</li>
 *   <li><b>取消时限</b>：开课前 24 小时内取消计爽约；累计 3 次限制报名 1 个月。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainServiceImpl implements TrainService {

    private static final Map<Integer, String> TRAIN_TYPE_LABEL = Map.of(
            1, "新员工入职", 2, "岗位技能", 3, "管理能力", 4, "合规安全", 5, "通用素养");

    private static final Map<Integer, String> SESSION_STATUS_LABEL = Map.of(
            1, "报名中", 2, "进行中", 3, "已结束", 4, "已取消");

    private static final Map<Integer, String> ENROLL_STATUS_LABEL = Map.of(
            1, "已报名", 2, "候补", 3, "已取消", 4, "已签到", 5, "缺勤", 6, "已完成", 7, "爽约");

    private static final Map<Integer, String> ENROLL_WAY_LABEL = Map.of(
            1, "自助报名", 2, "HR指派");

    /** 报名状态 */
    private static final int ENROLL_ENROLLED = 1;
    private static final int ENROLL_WAITING = 2;
    private static final int ENROLL_CANCELLED = 3;
    private static final int ENROLL_CHECKED = 4;
    private static final int ENROLL_ABSENT = 5;
    private static final int ENROLL_COMPLETED = 6;
    private static final int ENROLL_NOSHOW = 7;

    /** 期次状态 */
    private static final int SESSION_OPENING = 1;
    private static final int SESSION_CANCELED = 4;

    /** 取消报名时限：开课前 24 小时 */
    private static final int CANCEL_LIMIT_HOURS = 24;

    /** 爽约次数上限：累计 3 次限制报名 1 个月 */
    private static final int ABSENCE_LIMIT = 3;

    /** 限制报名天数 */
    private static final int RESTRICT_DAYS = 30;

    private final TrainCourseMapper courseMapper;
    private final TrainSessionMapper sessionMapper;
    private final TrainEnrollMapper enrollMapper;
    private final TrainAbsenceMapper absenceMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    // ==================================================================
    // 课程
    // ==================================================================

    @Override
    public PageResult<TrainCourseVO> pageCourses(String keyword, Integer type, Integer required,
                                                  Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<TrainCourse> wrapper = new LambdaQueryWrapper<TrainCourse>()
                .orderByDesc(TrainCourse::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(TrainCourse::getCourseCode, keyword)
                    .or().like(TrainCourse::getCourseName, keyword));
        }
        wrapper.eq(type != null, TrainCourse::getTrainType, type);
        wrapper.eq(required != null, TrainCourse::getRequired, required);
        Page<TrainCourse> page = courseMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toCourseVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public TrainCourseVO courseDetail(Long id) {
        return toCourseVOs(List.of(requireCourse(id))).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCourse(TrainCourseSaveRequest request) {
        TrainCourse duplicate = courseMapper.selectOne(new LambdaQueryWrapper<TrainCourse>()
                .eq(TrainCourse::getCourseCode, request.getCourseCode()).last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课程编码已存在");
        }
        TrainCourse entity = request.getId() == null
                ? new TrainCourse() : courseMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.TRAIN_COURSE_INVALID);
        }
        entity.setCourseCode(request.getCourseCode());
        entity.setCourseName(request.getCourseName());
        entity.setTrainType(request.getTrainType() == null ? 1 : request.getTrainType());
        entity.setTeacherType(request.getTeacherType() == null ? 1 : request.getTeacherType());
        entity.setTeacherId(request.getTeacherId());
        entity.setTeacherName(request.getTeacherName());
        entity.setTeacherOrg(request.getTeacherOrg());
        entity.setHours(request.getHours());
        entity.setDeptIds(request.getDeptIds());
        entity.setSeries(request.getSeries());
        entity.setRequired(request.getRequired() == null ? 0 : request.getRequired());
        entity.setPassScore(request.getPassScore() == null ? new BigDecimal("60") : request.getPassScore());
        entity.setFileId(request.getFileId());
        entity.setCost(request.getCost() == null ? BigDecimal.ZERO : request.getCost());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            courseMapper.insert(entity);
        } else {
            courseMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long id) {
        Long used = sessionMapper.selectCount(new LambdaQueryWrapper<TrainSession>()
                .eq(TrainSession::getCourseId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该课程下已有期次，不能删除，可改为停用");
        }
        courseMapper.deleteById(id);
    }

    // ==================================================================
    // 期次
    // ==================================================================

    @Override
    public PageResult<TrainSessionVO> pageSessions(String keyword, Long courseId, Integer status,
                                                    Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<TrainSession> wrapper = new LambdaQueryWrapper<TrainSession>()
                .orderByDesc(TrainSession::getStartTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(TrainSession::getSessionName, keyword);
        }
        wrapper.eq(courseId != null, TrainSession::getCourseId, courseId);
        wrapper.eq(status != null, TrainSession::getStatus, status);
        Page<TrainSession> page = sessionMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toSessionVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public TrainSessionVO sessionDetail(Long id) {
        return toSessionVOs(List.of(requireSession(id))).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSession(TrainSessionSaveRequest request) {
        requireCourse(request.getCourseId());
        if (request.getEndTime() != null && request.getStartTime() != null
                && request.getEndTime().isBefore(request.getStartTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        TrainSession entity = request.getId() == null
                ? new TrainSession() : sessionMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.TRAIN_SESSION_INVALID);
        }
        entity.setCourseId(request.getCourseId());
        entity.setSessionName(request.getSessionName());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setLocation(request.getLocation());
        entity.setOnlineLink(request.getOnlineLink());
        entity.setCapacity(request.getCapacity() == null ? 0 : request.getCapacity());
        entity.setEnrollDeadline(request.getEnrollDeadline());
        entity.setCheckinWay(request.getCheckinWay() == null ? 1 : request.getCheckinWay());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            entity.setStatus(SESSION_OPENING);
            entity.setEnrolledCount(0);
            entity.setWaitCount(0);
            sessionMapper.insert(entity);
        } else {
            sessionMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelSession(Long id) {
        TrainSession session = requireSession(id);
        if (Objects.equals(session.getStatus(), SESSION_CANCELED)) {
            return;
        }
        TrainSession patch = new TrainSession();
        patch.setId(id);
        patch.setStatus(SESSION_CANCELED);
        sessionMapper.updateById(patch);
        // 期次取消 → 所有报名置为已取消，但不记爽约（不是员工的错）
        List<TrainEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, id)
                .in(TrainEnroll::getStatus, List.of(ENROLL_ENROLLED, ENROLL_WAITING)));
        for (TrainEnroll e : enrolls) {
            TrainEnroll ep = new TrainEnroll();
            ep.setId(e.getId());
            ep.setStatus(ENROLL_CANCELLED);
            ep.setCancelTime(LocalDateTime.now());
            ep.setRemark("期次已取消");
            enrollMapper.updateById(ep);
        }
        log.info("培训期次取消: id={} 影响报名={}", id, enrolls.size());
    }

    // ==================================================================
    // 报名
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainEnrollVO enroll(Long sessionId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        TrainSession session = requireSession(sessionId);
        assertEnrollable(session);

        // 爽约限制：累计 3 次 → 1 个月内不能报名
        LocalDate restrictUntil = restrictUntilOf(userId);
        if (restrictUntil != null && restrictUntil.isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.TRAIN_ABSENCE_RESTRICTED,
                    "累计爽约 " + ABSENCE_LIMIT + " 次，报名受限至 " + restrictUntil);
        }

        // 已报名过（无论取消、候补还是别的状态）都不允许重复报名
        TrainEnroll existing = enrollMapper.selectOne(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .eq(TrainEnroll::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getStatus(), ENROLL_CANCELLED)) {
            throw new BizException(ErrorCode.TRAIN_ALREADY_ENROLLED);
        }

        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int enrolled = session.getEnrolledCount() == null ? 0 : session.getEnrolledCount();
        boolean full = capacity > 0 && enrolled >= capacity;

        TrainEnroll enroll = existing == null ? new TrainEnroll() : existing;
        enroll.setSessionId(sessionId);
        enroll.setUserId(userId);
        enroll.setEnrollWay(1);
        enroll.setStatus(full ? ENROLL_WAITING : ENROLL_ENROLLED);
        enroll.setRealHours(BigDecimal.ZERO);
        enroll.setCancelTime(null);
        if (existing == null) {
            enrollMapper.insert(enroll);
        } else {
            enrollMapper.updateById(enroll);
        }

        // 名额与候补计数
        TrainSession sp = new TrainSession();
        sp.setId(sessionId);
        if (full) {
            sp.setWaitCount((session.getWaitCount() == null ? 0 : session.getWaitCount()) + 1);
        } else {
            sp.setEnrolledCount(enrolled + 1);
        }
        sessionMapper.updateById(sp);

        log.info("培训报名: sessionId={} userId={} 状态={}", sessionId, userId, enroll.getStatus());
        return toEnrollVOs(List.of(enroll)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enrollBatch(TrainEnrollBatchRequest request) {
        TrainSession session = requireSession(request.getSessionId());
        if (Objects.equals(session.getStatus(), SESSION_CANCELED)) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "期次已取消");
        }
        // HR 指派可超额，但必须说明原因——超员是管理动作，不是默认行为
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "HR 指派必须填写原因");
        }
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int enrolled = session.getEnrolledCount() == null ? 0 : session.getEnrolledCount();
        int count = 0;
        for (Long userId : request.getUserIds()) {
            if (userMapper.selectById(userId) == null) {
                continue;
            }
            TrainEnroll existing = enrollMapper.selectOne(new LambdaQueryWrapper<TrainEnroll>()
                    .eq(TrainEnroll::getSessionId, request.getSessionId())
                    .eq(TrainEnroll::getUserId, userId)
                    .last("LIMIT 1"));
            if (existing != null && !Objects.equals(existing.getStatus(), ENROLL_CANCELLED)) {
                continue;
            }
            TrainEnroll enroll = existing == null ? new TrainEnroll() : existing;
            enroll.setSessionId(request.getSessionId());
            enroll.setUserId(userId);
            enroll.setEnrollWay(2);
            enroll.setAssignReason(request.getReason());
            enroll.setRealHours(BigDecimal.ZERO);
            enroll.setCancelTime(null);
            // 指派一律占正式名额（不受名额限制）
            enroll.setStatus(ENROLL_ENROLLED);
            if (existing == null) {
                enrollMapper.insert(enroll);
            } else {
                enrollMapper.updateById(enroll);
            }
            count++;
        }
        // 重算计数：让「已报名数」与实际一致
        refreshSessionCount(request.getSessionId());
        log.info("HR 指派培训: sessionId={} 人数={} capacity={} enrolled={}",
                request.getSessionId(), count, capacity, enrolled);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long enrollId) {
        TrainEnroll enroll = requireEnroll(enrollId);
        Long userId = SecurityContextUtil.getCurrentUserId();
        TrainSession session = requireSession(enroll.getSessionId());
        // 只能取消自己的报名
        if (!Objects.equals(enroll.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能取消自己的报名");
        }
        if (Objects.equals(enroll.getStatus(), ENROLL_CANCELLED)) {
            return;
        }
        // 已签到/已完成的不允许取消
        if (Objects.equals(enroll.getStatus(), ENROLL_CHECKED)
                || Objects.equals(enroll.getStatus(), ENROLL_COMPLETED)) {
            throw new BizException(ErrorCode.TRAIN_SESSION_STARTED, "已签到的记录不能取消");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean tooLate = session.getStartTime() != null
                && session.getStartTime().isBefore(now.plusHours(CANCEL_LIMIT_HOURS));

        TrainEnroll patch = new TrainEnroll();
        patch.setId(enrollId);
        patch.setCancelTime(now);
        if (tooLate) {
            // 距开课不足 24 小时 → 计爽约
            patch.setStatus(ENROLL_NOSHOW);
            TrainAbsence absence = new TrainAbsence();
            absence.setUserId(enroll.getUserId());
            absence.setSessionId(enroll.getSessionId());
            absence.setEnrollId(enrollId);
            absence.setAbsenceTime(now);
            // 累计达到上限 → 限制报名 1 个月
            long count = absenceMapper.selectCount(new LambdaQueryWrapper<TrainAbsence>()
                    .eq(TrainAbsence::getUserId, enroll.getUserId())
                    .ge(TrainAbsence::getAbsenceTime, now.minusYears(1)));
            if (count + 1 >= ABSENCE_LIMIT) {
                absence.setRestrictedUntil(LocalDate.now().plusDays(RESTRICT_DAYS));
            }
            absenceMapper.insert(absence);
        } else {
            patch.setStatus(ENROLL_CANCELLED);
            patch.setRemark("用户取消报名");
        }
        enrollMapper.updateById(patch);
        refreshSessionCount(enroll.getSessionId());
        log.info("培训取消报名: enrollId={} 距开课不足24h={}", enrollId, tooLate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long enrollId) {
        TrainEnroll enroll = requireEnroll(enrollId);
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (!Objects.equals(enroll.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能为自己签到");
        }
        TrainSession session = requireSession(enroll.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        // 签到窗口：开课前 30 分钟 ~ 结束后
        LocalDateTime openAt = session.getStartTime() == null
                ? now : session.getStartTime().minusMinutes(30);
        if (now.isBefore(openAt)) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "还没到签到时间");
        }
        if (session.getEndTime() != null && now.isAfter(session.getEndTime())) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "本期已结束，不能签到");
        }
        if (Objects.equals(enroll.getStatus(), ENROLL_CHECKED)
                || Objects.equals(enroll.getStatus(), ENROLL_COMPLETED)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "已签到，不要重复签到");
        }
        if (Objects.equals(enroll.getStatus(), ENROLL_CANCELLED)
                || Objects.equals(enroll.getStatus(), ENROLL_NOSHOW)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "已取消或已记为爽约的报名不能签到");
        }

        TrainCourse course = courseMapper.selectById(session.getCourseId());
        BigDecimal hours = course == null || course.getHours() == null ? BigDecimal.ZERO : course.getHours();
        TrainEnroll patch = new TrainEnroll();
        patch.setId(enrollId);
        patch.setStatus(ENROLL_CHECKED);
        patch.setCheckinTime(now);
        // ⚠️ 学时按签到计算，不按报名
        patch.setRealHours(hours);
        enrollMapper.updateById(patch);
        log.info("培训签到: enrollId={} 学时={}", enrollId, hours);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void score(TrainScoreRequest request) {
        TrainEnroll enroll = requireEnroll(request.getEnrollId());
        TrainSession session = requireSession(enroll.getSessionId());
        // 未签到不能录成绩：报了名没来，不能给成绩
        if (!Objects.equals(enroll.getStatus(), ENROLL_CHECKED)
                && !Objects.equals(enroll.getStatus(), ENROLL_COMPLETED)) {
            throw new BizException(ErrorCode.TRAIN_NOT_CHECKED_IN);
        }
        TrainCourse course = courseMapper.selectById(session.getCourseId());
        BigDecimal pass = course == null || course.getPassScore() == null
                ? new BigDecimal("60") : course.getPassScore();
        boolean passed = request.getScore().compareTo(pass) >= 0;
        boolean required = course != null && Objects.equals(course.getRequired(), 1);

        TrainEnroll patch = new TrainEnroll();
        patch.setId(request.getEnrollId());
        patch.setScore(request.getScore());
        patch.setAssessWay(request.getAssessWay() == null ? 1 : request.getAssessWay());
        patch.setPassed(passed ? 1 : 0);
        patch.setSatisfied(request.getSatisfied());
        patch.setCertFileId(request.getCertFileId());
        patch.setRemark(request.getRemark());
        // 必修课：签到 + 及格才结业；选修课只记学时
        patch.setStatus(passed ? ENROLL_COMPLETED : ENROLL_CHECKED);
        enrollMapper.updateById(patch);

        // 必修课未完成 → 提醒直属上级（写日志，本期无推送通道）
        if (required && !passed) {
            log.info("必修课未通过，需提醒直属上级: enrollId={} score={}", request.getEnrollId(), request.getScore());
        }
        log.info("培训成绩录入: enrollId={} 成绩={} 及格={} 结业={}",
                request.getEnrollId(), request.getScore(), passed, patch.getStatus());
    }

    @Override
    public List<TrainEnrollVO> listEnrolls(Long sessionId) {
        List<TrainEnroll> list = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .orderByAsc(TrainEnroll::getId));
        return toEnrollVOs(list);
    }

    @Override
    public PageResult<TrainEnrollVO> myEnrolls(Integer status, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<TrainEnroll> wrapper = new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getUserId, userId)
                .orderByDesc(TrainEnroll::getId);
        wrapper.eq(status != null, TrainEnroll::getStatus, status);
        Page<TrainEnroll> page = enrollMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toEnrollVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public List<TrainSessionVO> myAvailableSessions() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        List<TrainSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<TrainSession>()
                .eq(TrainSession::getStatus, SESSION_OPENING)
                .orderByAsc(TrainSession::getStartTime));
        Set<Long> enrolledIds = sessions.isEmpty() ? Set.of()
                : enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getUserId, userId)
                .in(TrainEnroll::getSessionId, sessions.stream().map(TrainSession::getId).toList()))
                .stream().map(TrainEnroll::getSessionId).collect(Collectors.toSet());
        List<TrainSession> available = sessions.stream()
                .filter(s -> !enrolledIds.contains(s.getId()))
                .toList();
        return toSessionVOs(available);
    }

    // ==================================================================
    // 统计
    // ==================================================================

    @Override
    public TrainStatisticVO statistics(String year) {
        TrainStatisticVO vo = new TrainStatisticVO();
        int yr = StringUtils.hasText(year) ? Integer.parseInt(year) : LocalDate.now().getYear();
        LocalDateTime from = LocalDateTime.of(yr, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(yr + 1, 1, 1, 0, 0);

        List<TrainEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .ge(TrainEnroll::getCreateTime, from)
                .lt(TrainEnroll::getCreateTime, to));
        List<TrainSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<TrainSession>()
                .ge(TrainSession::getStartTime, from)
                .lt(TrainSession::getStartTime, to));
        List<TrainCourse> courses = courseMapper.selectList(new LambdaQueryWrapper<TrainCourse>());
        Map<Long, TrainCourse> courseMap = courses.stream()
                .collect(Collectors.toMap(TrainCourse::getId, c -> c, (a, b) -> a));
        // 把课程ID塞进报名的临时字段，统计时按课程分组就不用反复查库
        Map<Long, TrainSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(TrainSession::getId, s -> s, (a, b) -> a));
        for (TrainEnroll e : enrolls) {
            TrainSession s = sessionMap.get(e.getSessionId());
            if (s != null) {
                e.setSessionCourseId(s.getCourseId());
            }
        }

        // 总学时与人均学时：按签到（real_hours）累加，报名不算
        BigDecimal totalHours = enrolls.stream()
                .map(e -> e.getRealHours() == null ? BigDecimal.ZERO : e.getRealHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long staffCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4)));
        vo.setTotalHours(totalHours);
        vo.setStaffCount(staffCount == null ? 0 : staffCount.intValue());
        vo.setAvgHours(vo.getStaffCount() == 0 ? BigDecimal.ZERO
                : totalHours.divide(new BigDecimal(vo.getStaffCount()), 1, RoundingMode.HALF_UP));

        // 必修完成率
        long requiredEnroll = enrolls.stream()
                .filter(e -> {
                    TrainCourse c = courseMap.get(e.getSessionCourseId());
                    return c != null && Objects.equals(c.getRequired(), 1);
                }).count();
        long requiredDone = enrolls.stream()
                .filter(e -> {
                    TrainCourse c = courseMap.get(e.getSessionCourseId());
                    return c != null && Objects.equals(c.getRequired(), 1)
                            && Objects.equals(e.getStatus(), ENROLL_COMPLETED);
                }).count();
        vo.setRequiredShouldCount((int) requiredEnroll);
        vo.setRequiredDoneCount((int) requiredDone);
        vo.setRequiredFinishRate(requiredEnroll == 0 ? 0 : (int) (requiredDone * 100 / requiredEnroll));

        // 满意度
        List<Integer> satisfied = enrolls.stream().map(TrainEnroll::getSatisfied)
                .filter(Objects::nonNull).toList();
        vo.setSatisfaction(satisfied.isEmpty() ? BigDecimal.ZERO
                : new BigDecimal(satisfied.stream().mapToInt(Integer::intValue).sum())
                .divide(new BigDecimal(satisfied.size()), 1, RoundingMode.HALF_UP));

        // 培训投入
        vo.setTotalCost(courses.stream()
                .map(c -> c.getCost() == null ? BigDecimal.ZERO : c.getCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 课程维度
        Map<Long, List<TrainEnroll>> byCourse = enrolls.stream()
                .filter(e -> e.getSessionCourseId() != null)
                .collect(Collectors.groupingBy(TrainEnroll::getSessionCourseId));
        List<TrainStatisticVO.CourseStat> courseStats = new ArrayList<>();
        byCourse.forEach((cid, list) -> {
            TrainStatisticVO.CourseStat stat = new TrainStatisticVO.CourseStat();
            stat.setCourseId(cid);
            TrainCourse c = courseMap.get(cid);
            stat.setCourseName(c == null ? "未知课程" : c.getCourseName());
            stat.setRequired(c == null ? 0 : c.getRequired());
            stat.setEnrollCount(list.size());
            stat.setCompletedCount((int) list.stream()
                    .filter(e -> Objects.equals(e.getStatus(), ENROLL_COMPLETED)).count());
            long checked = list.stream().filter(e -> e.getCheckinTime() != null).count();
            stat.setAttendRate(list.isEmpty() ? 0 : (int) (checked * 100 / list.size()));
            BigDecimal hours = list.stream().map(e -> e.getRealHours() == null ? BigDecimal.ZERO : e.getRealHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stat.setAvgHours(list.isEmpty() ? BigDecimal.ZERO
                    : hours.divide(new BigDecimal(list.size()), 1, RoundingMode.HALF_UP));
            List<Integer> sat = list.stream().map(TrainEnroll::getSatisfied).filter(Objects::nonNull).toList();
            stat.setSatisfaction(sat.isEmpty() ? BigDecimal.ZERO
                    : new BigDecimal(sat.stream().mapToInt(Integer::intValue).sum())
                    .divide(new BigDecimal(sat.size()), 1, RoundingMode.HALF_UP));
            courseStats.add(stat);
        });
        courseStats.sort(Comparator.comparing(TrainStatisticVO.CourseStat::getEnrollCount).reversed());
        vo.setCourseStats(courseStats);

        // 部门维度
        Map<Long, String> deptNames = departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Map<Long, User> userMap = enrolls.stream().map(TrainEnroll::getUserId).filter(Objects::nonNull)
                .distinct().map(userMapper::selectById).filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, List<TrainEnroll>> byDept = new LinkedHashMap<>();
        for (TrainEnroll e : enrolls) {
            User u = userMap.get(e.getUserId());
            if (u == null || u.getDeptId() == null) {
                continue;
            }
            byDept.computeIfAbsent(u.getDeptId(), k -> new ArrayList<>()).add(e);
        }
        List<TrainStatisticVO.DeptStat> deptStats = new ArrayList<>();
        byDept.forEach((did, list) -> {
            TrainStatisticVO.DeptStat stat = new TrainStatisticVO.DeptStat();
            stat.setDeptId(did);
            stat.setDeptName(deptNames.getOrDefault(did, "未知部门"));
            stat.setEnrollCount(list.size());
            stat.setCompletedCount((int) list.stream()
                    .filter(e -> Objects.equals(e.getStatus(), ENROLL_COMPLETED)).count());
            stat.setTotalHours(list.stream()
                    .map(e -> e.getRealHours() == null ? BigDecimal.ZERO : e.getRealHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            deptStats.add(stat);
        });
        deptStats.sort(Comparator.comparing(TrainStatisticVO.DeptStat::getTotalHours).reversed());
        vo.setDeptStats(deptStats);
        return vo;
    }

    // ==================================================================
    // 组装与工具
    // ==================================================================

    private List<TrainCourseVO> toCourseVOs(List<TrainCourse> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = courses.stream().map(TrainCourse::getId).collect(Collectors.toSet());
        Map<Long, Long> sessionCounts = sessionMapper.selectList(new LambdaQueryWrapper<TrainSession>()
                        .in(TrainSession::getCourseId, courseIds)).stream()
                .collect(Collectors.groupingBy(TrainSession::getCourseId, Collectors.counting()));
        List<TrainEnroll> allEnrolls = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .in(TrainEnroll::getSessionId, sessionMapper.selectList(
                        new LambdaQueryWrapper<TrainSession>().in(TrainSession::getCourseId, courseIds))
                        .stream().map(TrainSession::getId).toList()));
        Map<Long, Long> completedCounts = allEnrolls.stream()
                .filter(e -> Objects.equals(e.getStatus(), ENROLL_COMPLETED))
                .collect(Collectors.groupingBy(e -> {
                    TrainSession s = sessionMapper.selectById(e.getSessionId());
                    return s == null ? 0L : s.getCourseId();
                }, Collectors.counting()));
        List<TrainCourseVO> vos = new ArrayList<>(courses.size());
        for (TrainCourse c : courses) {
            TrainCourseVO vo = new TrainCourseVO();
            vo.setId(c.getId());
            vo.setCourseCode(c.getCourseCode());
            vo.setCourseName(c.getCourseName());
            vo.setTrainType(c.getTrainType());
            vo.setTrainTypeLabel(TRAIN_TYPE_LABEL.getOrDefault(c.getTrainType(), "通用素养"));
            vo.setTeacherType(c.getTeacherType());
            vo.setTeacherTypeLabel(Objects.equals(c.getTeacherType(), 2) ? "外部讲师" : "内部讲师");
            vo.setTeacherId(c.getTeacherId());
            vo.setTeacherName(c.getTeacherName());
            vo.setTeacherOrg(c.getTeacherOrg());
            vo.setHours(c.getHours());
            vo.setDeptIds(c.getDeptIds());
            vo.setSeries(c.getSeries());
            vo.setRequired(c.getRequired());
            vo.setRequiredLabel(Objects.equals(c.getRequired(), 1) ? "必修" : "选修");
            vo.setPassScore(c.getPassScore());
            vo.setFileId(c.getFileId());
            vo.setCost(c.getCost());
            vo.setStatus(c.getStatus());
            vo.setStatusLabel(Objects.equals(c.getStatus(), 2) ? "停用" : "启用");
            vo.setRemark(c.getRemark());
            vo.setSessionCount(sessionCounts.getOrDefault(c.getId(), 0L).intValue());
            vo.setCompletedCount(completedCounts.getOrDefault(c.getId(), 0L).intValue());
            vos.add(vo);
        }
        return vos;
    }

    private List<TrainSessionVO> toSessionVOs(List<TrainSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = sessions.stream().map(TrainSession::getCourseId).collect(Collectors.toSet());
        Map<Long, TrainCourse> courseMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(TrainCourse::getId, c -> c, (a, b) -> a));
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        Set<Long> sessionIds = sessions.stream().map(TrainSession::getId).collect(Collectors.toSet());
        Map<Long, Integer> myStatusMap = sessionIds.isEmpty() || currentUserId == null ? Map.of()
                : enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .in(TrainEnroll::getSessionId, sessionIds)
                .eq(TrainEnroll::getUserId, currentUserId)).stream()
                .collect(Collectors.toMap(TrainEnroll::getSessionId, TrainEnroll::getStatus, (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();

        List<TrainSessionVO> vos = new ArrayList<>(sessions.size());
        for (TrainSession s : sessions) {
            TrainSessionVO vo = new TrainSessionVO();
            vo.setId(s.getId());
            vo.setCourseId(s.getCourseId());
            TrainCourse course = courseMap.get(s.getCourseId());
            if (course != null) {
                vo.setCourseCode(course.getCourseCode());
                vo.setCourseName(course.getCourseName());
                vo.setTrainType(course.getTrainType());
                vo.setTrainTypeLabel(TRAIN_TYPE_LABEL.getOrDefault(course.getTrainType(), "通用素养"));
                vo.setRequired(course.getRequired());
                vo.setRequiredLabel(Objects.equals(course.getRequired(), 1) ? "必修" : "选修");
                vo.setHours(course.getHours());
            }
            vo.setSessionName(s.getSessionName());
            vo.setStartTime(s.getStartTime());
            vo.setEndTime(s.getEndTime());
            vo.setLocation(s.getLocation());
            vo.setOnlineLink(s.getOnlineLink());
            vo.setCapacity(s.getCapacity());
            vo.setEnrolledCount(s.getEnrolledCount());
            vo.setWaitCount(s.getWaitCount());
            vo.setEnrollDeadline(s.getEnrollDeadline());
            vo.setStatus(s.getStatus());
            vo.setStatusLabel(SESSION_STATUS_LABEL.getOrDefault(s.getStatus(), "未知"));
            vo.setCheckinWay(s.getCheckinWay());
            vo.setRemark(s.getRemark());
            vo.setCreateTime(s.getCreateTime());

            int capacity = s.getCapacity() == null ? 0 : s.getCapacity();
            int enrolled = s.getEnrolledCount() == null ? 0 : s.getEnrolledCount();
            vo.setRemainQuota(capacity <= 0 ? -1 : Math.max(capacity - enrolled, 0));
            boolean closed = s.getEnrollDeadline() != null && s.getEnrollDeadline().isBefore(now);
            vo.setEnrollClosed(closed);
            boolean started = s.getStartTime() != null && !s.getStartTime().isAfter(now);
            vo.setStarted(started);
            vo.setEnrollable(Objects.equals(s.getStatus(), SESSION_OPENING) && !closed && !started);
            vo.setMyEnrollStatus(myStatusMap.get(s.getId()));
            vos.add(vo);
        }
        return vos;
    }

    private List<TrainEnrollVO> toEnrollVOs(List<TrainEnroll> enrolls) {
        if (enrolls.isEmpty()) {
            return List.of();
        }
        Set<Long> sessionIds = enrolls.stream().map(TrainEnroll::getSessionId).collect(Collectors.toSet());
        Map<Long, TrainSession> sessionMap = sessionIds.isEmpty() ? Map.of()
                : sessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(TrainSession::getId, s -> s, (a, b) -> a));
        Set<Long> courseIds = sessionMap.values().stream().map(TrainSession::getCourseId)
                .collect(Collectors.toSet());
        Map<Long, TrainCourse> courseMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(TrainCourse::getId, c -> c, (a, b) -> a));
        Set<Long> userIds = enrolls.stream().map(TrainEnroll::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Set<Long> deptIds = userMap.values().stream().map(User::getDeptId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        List<TrainEnrollVO> vos = new ArrayList<>(enrolls.size());
        for (TrainEnroll e : enrolls) {
            TrainEnrollVO vo = new TrainEnrollVO();
            vo.setId(e.getId());
            vo.setSessionId(e.getSessionId());
            TrainSession session = sessionMap.get(e.getSessionId());
            if (session != null) {
                vo.setSessionName(session.getSessionName());
                vo.setCourseId(session.getCourseId());
                TrainCourse course = courseMap.get(session.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getCourseName());
                    vo.setTrainType(course.getTrainType());
                    vo.setRequired(course.getRequired());
                    vo.setRequiredLabel(Objects.equals(course.getRequired(), 1) ? "必修" : "选修");
                    vo.setHours(course.getHours());
                }
            }
            vo.setUserId(e.getUserId());
            User user = userMap.get(e.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setDeptName(deptNames.get(user.getDeptId()));
            }
            vo.setEnrollWay(e.getEnrollWay());
            vo.setEnrollWayLabel(ENROLL_WAY_LABEL.getOrDefault(e.getEnrollWay(), "自助报名"));
            vo.setStatus(e.getStatus());
            vo.setStatusLabel(ENROLL_STATUS_LABEL.getOrDefault(e.getStatus(), "未知"));
            vo.setAssignReason(e.getAssignReason());
            vo.setCheckinTime(e.getCheckinTime());
            vo.setRealHours(e.getRealHours());
            vo.setScore(e.getScore());
            vo.setAssessWay(e.getAssessWay());
            vo.setPassed(e.getPassed());
            vo.setSatisfied(e.getSatisfied());
            vo.setCertFileId(e.getCertFileId());
            vo.setCancelTime(e.getCancelTime());
            vo.setRemark(e.getRemark());
            vo.setCreateTime(e.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    private TrainCourse requireCourse(Long id) {
        TrainCourse course = courseMapper.selectById(id);
        if (course == null) {
            throw new BizException(ErrorCode.TRAIN_COURSE_INVALID);
        }
        return course;
    }

    private TrainSession requireSession(Long id) {
        TrainSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BizException(ErrorCode.TRAIN_SESSION_INVALID);
        }
        return session;
    }

    private TrainEnroll requireEnroll(Long id) {
        TrainEnroll enroll = enrollMapper.selectById(id);
        if (enroll == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "报名记录不存在");
        }
        return enroll;
    }

    /** 期次可报名校验 */
    private void assertEnrollable(TrainSession session) {
        if (!Objects.equals(session.getStatus(), SESSION_OPENING)) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "该期次不接受报名");
        }
        if (session.getEnrollDeadline() != null
                && session.getEnrollDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "报名已截止");
        }
        if (session.getStartTime() != null && session.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "该期次已开始");
        }
    }

    /** 爽约限制截止日（未受限则 null） */
    private LocalDate restrictUntilOf(Long userId) {
        List<TrainAbsence> list = absenceMapper.selectList(new LambdaQueryWrapper<TrainAbsence>()
                .eq(TrainAbsence::getUserId, userId)
                .isNotNull(TrainAbsence::getRestrictedUntil)
                .orderByDesc(TrainAbsence::getAbsenceTime));
        return list.isEmpty() ? null : list.get(0).getRestrictedUntil();
    }

    /** 重算期次的已报名数与候补数 */
    private void refreshSessionCount(Long sessionId) {
        Long enrolled = enrollMapper.selectCount(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .eq(TrainEnroll::getStatus, ENROLL_ENROLLED));
        Long waiting = enrollMapper.selectCount(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .eq(TrainEnroll::getStatus, ENROLL_WAITING));
        TrainSession patch = new TrainSession();
        patch.setId(sessionId);
        patch.setEnrolledCount(enrolled == null ? 0 : enrolled.intValue());
        patch.setWaitCount(waiting == null ? 0 : waiting.intValue());
        sessionMapper.updateById(patch);
    }
}
