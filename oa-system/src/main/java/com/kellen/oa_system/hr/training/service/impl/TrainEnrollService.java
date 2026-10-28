package com.kellen.oa_system.hr.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.training.dto.TrainEnrollBatchRequest;
import com.kellen.oa_system.hr.training.dto.TrainScoreRequest;
import com.kellen.oa_system.hr.training.entity.TrainAbsence;
import com.kellen.oa_system.hr.training.entity.TrainCourse;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainAbsenceMapper;
import com.kellen.oa_system.hr.training.mapper.TrainCourseMapper;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.support.TrainConstants;
import com.kellen.oa_system.hr.training.support.TrainEnrollSupport;
import com.kellen.oa_system.hr.training.support.TrainSessionSupport;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 培训报名领域（BE-OPT-08 从 {@code TrainServiceImpl} 拆出）。
 *
 * <p>三条容易做错的规则都在这里，改任何一条都要先看注释：</p>
 * <ol>
 *   <li><b>名额满 → 候补</b>：普通员工报名进候补（{@code waitCount}），HR 指派可超额但必须填原因；</li>
 *   <li><b>取消时限</b>：开课前 24 小时内取消计爽约，累计 3 次限制报名 1 个月；</li>
 *   <li><b>学时按签到算</b>：只报名不签到 → 学时 0；未签到不能录成绩。</li>
 * </ol>
 *
 * <p>不含事务注解，事务由门面持有。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainEnrollService {

    private final TrainEnrollMapper enrollMapper;
    private final TrainAbsenceMapper absenceMapper;
    private final TrainSessionMapper sessionMapper;
    private final TrainCourseMapper courseMapper;
    private final UserMapper userMapper;
    private final TrainSessionSupport sessionSupport;
    private final TrainEnrollSupport enrollSupport;

    /** 自助报名：满员进候补，重复报名（非已取消）一律拒 */
    public TrainEnrollVO enroll(Long sessionId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        TrainSession session = sessionSupport.requireSession(sessionId);
        assertEnrollable(session);

        // 爽约限制：累计 3 次 → 1 个月内不能报名
        LocalDate restrictUntil = enrollSupport.restrictUntilOf(userId);
        if (restrictUntil != null && restrictUntil.isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.TRAIN_ABSENCE_RESTRICTED,
                    "累计爽约 " + TrainConstants.ABSENCE_LIMIT + " 次，报名受限至 " + restrictUntil);
        }

        // 已报名过（无论取消、候补还是别的状态）都不允许重复报名
        TrainEnroll existing = enrollMapper.selectOne(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .eq(TrainEnroll::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getStatus(), TrainConstants.ENROLL_CANCELLED)) {
            throw new BizException(ErrorCode.TRAIN_ALREADY_ENROLLED);
        }

        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int enrolled = session.getEnrolledCount() == null ? 0 : session.getEnrolledCount();
        boolean full = capacity > 0 && enrolled >= capacity;

        TrainEnroll enroll = existing == null ? new TrainEnroll() : existing;
        enroll.setSessionId(sessionId);
        enroll.setUserId(userId);
        enroll.setEnrollWay(1);
        enroll.setStatus(full ? TrainConstants.ENROLL_WAITING : TrainConstants.ENROLL_ENROLLED);
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
        return enrollSupport.toVO(enroll);
    }

    /** HR 批量指派：可超额，但必须说明原因（超员是管理动作，不是默认行为） */
    public int enrollBatch(TrainEnrollBatchRequest request) {
        TrainSession session = sessionSupport.requireSession(request.getSessionId());
        if (Objects.equals(session.getStatus(), TrainConstants.SESSION_CANCELED)) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "期次已取消");
        }
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
            if (existing != null && !Objects.equals(existing.getStatus(), TrainConstants.ENROLL_CANCELLED)) {
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
            enroll.setStatus(TrainConstants.ENROLL_ENROLLED);
            if (existing == null) {
                enrollMapper.insert(enroll);
            } else {
                enrollMapper.updateById(enroll);
            }
            count++;
        }
        // 重算计数：让「已报名数」与实际一致
        sessionSupport.refreshCounts(request.getSessionId());
        log.info("HR 指派培训: sessionId={} 人数={} capacity={} enrolled={}",
                request.getSessionId(), count, capacity, enrolled);
        return count;
    }

    /** 取消报名：只能取消自己的；距开课不足 24 小时计爽约 */
    public void cancel(Long enrollId) {
        TrainEnroll enroll = enrollSupport.requireEnroll(enrollId);
        Long userId = SecurityContextUtil.getCurrentUserId();
        TrainSession session = sessionSupport.requireSession(enroll.getSessionId());
        if (!Objects.equals(enroll.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能取消自己的报名");
        }
        if (Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_CANCELLED)) {
            return;
        }
        if (Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_CHECKED)
                || Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_COMPLETED)) {
            throw new BizException(ErrorCode.TRAIN_SESSION_STARTED, "已签到的记录不能取消");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean tooLate = session.getStartTime() != null
                && session.getStartTime().isBefore(now.plusHours(TrainConstants.CANCEL_LIMIT_HOURS));

        TrainEnroll patch = new TrainEnroll();
        patch.setId(enrollId);
        patch.setCancelTime(now);
        if (tooLate) {
            // 距开课不足 24 小时 → 计爽约
            patch.setStatus(TrainConstants.ENROLL_NOSHOW);
            TrainAbsence absence = new TrainAbsence();
            absence.setUserId(enroll.getUserId());
            absence.setSessionId(enroll.getSessionId());
            absence.setEnrollId(enrollId);
            absence.setAbsenceTime(now);
            // 累计达到上限 → 限制报名 1 个月
            long count = absenceMapper.selectCount(new LambdaQueryWrapper<TrainAbsence>()
                    .eq(TrainAbsence::getUserId, enroll.getUserId())
                    .ge(TrainAbsence::getAbsenceTime, now.minusYears(1)));
            if (count + 1 >= TrainConstants.ABSENCE_LIMIT) {
                absence.setRestrictedUntil(LocalDate.now().plusDays(TrainConstants.RESTRICT_DAYS));
            }
            absenceMapper.insert(absence);
        } else {
            patch.setStatus(TrainConstants.ENROLL_CANCELLED);
            patch.setRemark("用户取消报名");
        }
        enrollMapper.updateById(patch);
        sessionSupport.refreshCounts(enroll.getSessionId());
        log.info("培训取消报名: enrollId={} 距开课不足24h={}", enrollId, tooLate);
    }

    /** 签到：窗口为开课前 30 分钟至结束；学时按签到写入 */
    public void checkin(Long enrollId) {
        TrainEnroll enroll = enrollSupport.requireEnroll(enrollId);
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (!Objects.equals(enroll.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能为自己签到");
        }
        TrainSession session = sessionSupport.requireSession(enroll.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime openAt = session.getStartTime() == null
                ? now : session.getStartTime().minusMinutes(30);
        if (now.isBefore(openAt)) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "还没到签到时间");
        }
        if (session.getEndTime() != null && now.isAfter(session.getEndTime())) {
            throw new BizException(ErrorCode.TRAIN_ENROLL_CLOSED, "本期已结束，不能签到");
        }
        if (Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_CHECKED)
                || Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_COMPLETED)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "已签到，不要重复签到");
        }
        if (Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_CANCELLED)
                || Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_NOSHOW)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "已取消或已记为爽约的报名不能签到");
        }

        TrainCourse course = courseMapper.selectById(session.getCourseId());
        BigDecimal hours = course == null || course.getHours() == null ? BigDecimal.ZERO : course.getHours();
        TrainEnroll patch = new TrainEnroll();
        patch.setId(enrollId);
        patch.setStatus(TrainConstants.ENROLL_CHECKED);
        patch.setCheckinTime(now);
        // ⚠️ 学时按签到计算，不按报名
        patch.setRealHours(hours);
        enrollMapper.updateById(patch);
        log.info("培训签到: enrollId={} 学时={}", enrollId, hours);
    }

    /** 成绩录入：未签到不能录；必修课「签到 + 及格」才结业 */
    public void score(TrainScoreRequest request) {
        TrainEnroll enroll = enrollSupport.requireEnroll(request.getEnrollId());
        TrainSession session = sessionSupport.requireSession(enroll.getSessionId());
        if (!Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_CHECKED)
                && !Objects.equals(enroll.getStatus(), TrainConstants.ENROLL_COMPLETED)) {
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
        patch.setStatus(passed ? TrainConstants.ENROLL_COMPLETED : TrainConstants.ENROLL_CHECKED);
        enrollMapper.updateById(patch);

        // 必修课未完成 → 提醒直属上级（写日志，本期无推送通道）
        if (required && !passed) {
            log.info("必修课未通过，需提醒直属上级: enrollId={} score={}", request.getEnrollId(), request.getScore());
        }
        log.info("培训成绩录入: enrollId={} 成绩={} 及格={} 结业={}",
                request.getEnrollId(), request.getScore(), passed, patch.getStatus());
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 期次可报名校验：状态、报名截止、是否已开始 */
    private void assertEnrollable(TrainSession session) {
        if (!Objects.equals(session.getStatus(), TrainConstants.SESSION_OPENING)) {
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

}
