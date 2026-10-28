package com.kellen.oa_system.hr.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.training.dto.TrainSessionSaveRequest;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.support.TrainConstants;
import com.kellen.oa_system.hr.training.support.TrainSessionSupport;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 培训期次领域（BE-OPT-08 从 {@code TrainServiceImpl} 拆出）。
 *
 * <p>期次是「课程 + 时间 + 名额」的组合：保存时要校验课程存在与时间先后，
 * 取消时要把该期次下所有有效报名置为已取消（<b>但不记爽约</b>——不是员工的错）。</p>
 *
 * <p>不含事务注解，事务由门面持有。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainSessionService {

    private final TrainSessionMapper sessionMapper;
    private final TrainEnrollMapper enrollMapper;
    private final TrainCourseService courseService;
    private final TrainSessionSupport sessionSupport;

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
        return PageResult.of(sessionSupport.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public TrainSessionVO sessionDetail(Long id) {
        return sessionSupport.toVOs(List.of(sessionSupport.requireSession(id))).get(0);
    }

    public Long saveSession(TrainSessionSaveRequest request) {
        courseService.requireCourse(request.getCourseId());
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
            entity.setStatus(TrainConstants.SESSION_OPENING);
            entity.setEnrolledCount(0);
            entity.setWaitCount(0);
            sessionMapper.insert(entity);
        } else {
            sessionMapper.updateById(entity);
        }
        return entity.getId();
    }

    public void cancelSession(Long id) {
        TrainSession session = sessionSupport.requireSession(id);
        if (Objects.equals(session.getStatus(), TrainConstants.SESSION_CANCELED)) {
            return;
        }
        TrainSession patch = new TrainSession();
        patch.setId(id);
        patch.setStatus(TrainConstants.SESSION_CANCELED);
        sessionMapper.updateById(patch);
        // 期次取消 → 所有报名置为已取消，但不记爽约（不是员工的错）
        List<TrainEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, id)
                .in(TrainEnroll::getStatus, List.of(TrainConstants.ENROLL_ENROLLED, TrainConstants.ENROLL_WAITING)));
        for (TrainEnroll e : enrolls) {
            TrainEnroll ep = new TrainEnroll();
            ep.setId(e.getId());
            ep.setStatus(TrainConstants.ENROLL_CANCELLED);
            ep.setCancelTime(LocalDateTime.now());
            ep.setRemark("期次已取消");
            enrollMapper.updateById(ep);
        }
        log.info("培训期次取消: id={} 影响报名={}", id, enrolls.size());
    }
}
