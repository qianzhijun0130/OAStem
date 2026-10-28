package com.kellen.oa_system.hr.training.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.training.entity.TrainCourse;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainCourseMapper;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 期次的共享支撑（BE-OPT-08 拆分时抽出）。
 *
 * <p>为什么单独成一类而不是塞进 {@code TrainSessionService}：这三个能力被
 * <b>期次域</b>（自身 CRUD）与<b>报名域</b>（{@code myAvailableSessions}）同时需要。
 * 若放进 {@code TrainSessionService}，报名域就得反过来依赖期次服务，虽不致命，
 * 但会让「服务之间能否互相调用」变成需要逐个判断的问题；抽成无状态的支撑类后，
 * 依赖方向恒为 服务 → 支撑。</p>
 *
 * <p>本类只做「读校验 / VO 组装 / 计数回写」，不含业务分支判断，也不带事务注解。</p>
 */
@Component
@RequiredArgsConstructor
public class TrainSessionSupport {

    private final TrainSessionMapper sessionMapper;
    private final TrainCourseMapper courseMapper;
    private final TrainEnrollMapper enrollMapper;

    /** 期次存在性校验 */
    public TrainSession requireSession(Long id) {
        TrainSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BizException(ErrorCode.TRAIN_SESSION_INVALID);
        }
        return session;
    }

    /**
     * 期次 VO 组装。
     *
     * <p>「我的报名状态」取自当前登录人：同一个期次在不同用户眼里 {@code myEnrollStatus} 不同，
     * 因此这里必须读 {@code SecurityContextUtil}，不能把用户当参数由调用方传——调用方拿到的
     * 都是「当前用户」视角，多一个参数只会多一个传错的机会。</p>
     */
    public List<TrainSessionVO> toVOs(List<TrainSession> sessions) {
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
                vo.setTrainTypeLabel(TrainConstants.TRAIN_TYPE_LABEL.getOrDefault(course.getTrainType(), "通用素养"));
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
            vo.setStatusLabel(TrainConstants.SESSION_STATUS_LABEL.getOrDefault(s.getStatus(), "未知"));
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
            vo.setEnrollable(Objects.equals(s.getStatus(), TrainConstants.SESSION_OPENING) && !closed && !started);
            vo.setMyEnrollStatus(myStatusMap.get(s.getId()));
            vos.add(vo);
        }
        return vos;
    }

    /** 重算期次的已报名数与候补数（报名/取消/指派后调用，保证计数与明细一致） */
    public void refreshCounts(Long sessionId) {
        Long enrolled = enrollMapper.selectCount(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .eq(TrainEnroll::getStatus, TrainConstants.ENROLL_ENROLLED));
        Long waiting = enrollMapper.selectCount(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .eq(TrainEnroll::getStatus, TrainConstants.ENROLL_WAITING));
        TrainSession patch = new TrainSession();
        patch.setId(sessionId);
        patch.setEnrolledCount(enrolled == null ? 0 : enrolled.intValue());
        patch.setWaitCount(waiting == null ? 0 : waiting.intValue());
        sessionMapper.updateById(patch);
    }
}
