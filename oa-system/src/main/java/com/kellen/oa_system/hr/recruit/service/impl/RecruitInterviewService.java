package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.recruit.dto.RecruitFeedbackRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitInterviewSaveRequest;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitFeedback;
import com.kellen.oa_system.hr.recruit.entity.RecruitInterview;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitFeedbackMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitInterviewMapper;
import com.kellen.oa_system.hr.recruit.support.RecruitConstants;
import com.kellen.oa_system.hr.recruit.support.RecruitInterviewAssembler;
import com.kellen.oa_system.hr.recruit.support.RecruitSupport;
import com.kellen.oa_system.hr.recruit.vo.RecruitInterviewVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面试安排与评价。
 *
 * <p>三条「不许跳过」：不是本场面试官不能评价；同一面试官只能一条评价（重提覆盖自己那条）；
 * <b>评价没交齐不能汇总</b>——否则结论只代表一部分人。</p>
 *
 * <p>汇总通过后会推进候选人阶段（终试则停在上 offer 前一步）。这里直接用
 * {@code candidateMapper} 更新而不是走 {@link RecruitCandidateService#changeStage}：
 * 阶段推进规则（只前进 / 终态不可变）在汇总场景下是另一套判定，
 * 套用 changeStage 会被「必须填淘汰原因」等校验误伤。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitInterviewService {

    private final RecruitInterviewMapper interviewMapper;
    private final RecruitFeedbackMapper feedbackMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final RecruitSupport support;
    private final RecruitInterviewAssembler assembler;

    public PageResult<RecruitInterviewVO> pageInterviews(Long candidateId, Integer status,
                                                          Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitInterview> wrapper = new LambdaQueryWrapper<RecruitInterview>()
                .orderByDesc(RecruitInterview::getInterviewTime);
        wrapper.eq(candidateId != null, RecruitInterview::getCandidateId, candidateId);
        wrapper.eq(status != null, RecruitInterview::getStatus, status);
        Page<RecruitInterview> page = interviewMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public RecruitInterviewVO interviewDetail(Long id) {
        RecruitInterview interview = support.requireInterview(id);
        return assembler.toVOs(List.of(interview)).get(0);
    }

    public Long saveInterview(RecruitInterviewSaveRequest request) {
        RecruitCandidate candidate = support.requireCandidate(request.getCandidateId());
        RecruitInterview entity = request.getId() == null
                ? new RecruitInterview() : interviewMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试不存在");
        }
        // 同一候选人同一职位同一轮次唯一
        LambdaQueryWrapper<RecruitInterview> dup = new LambdaQueryWrapper<RecruitInterview>()
                .eq(RecruitInterview::getCandidateId, request.getCandidateId())
                .eq(RecruitInterview::getRoundNo, request.getRoundNo() == null ? 1 : request.getRoundNo())
                .last("LIMIT 1");
        if (request.getPostId() != null) {
            dup.eq(RecruitInterview::getPostId, request.getPostId());
        }
        if (request.getId() != null) {
            dup.ne(RecruitInterview::getId, request.getId());
        }
        if (interviewMapper.selectCount(dup) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该候选人本轮次已存在面试安排");
        }
        entity.setCandidateId(request.getCandidateId());
        entity.setPostId(request.getPostId() == null ? candidate.getPostId() : request.getPostId());
        entity.setRoundNo(request.getRoundNo() == null ? 1 : request.getRoundNo());
        entity.setInterviewTime(request.getInterviewTime());
        entity.setWay(request.getWay() == null ? 1 : request.getWay());
        entity.setLocation(request.getLocation());
        entity.setInterviewerIds(request.getInterviewerIds().stream()
                .map(String::valueOf).collect(Collectors.joining(",")));
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            entity.setStatus(1);
            interviewMapper.insert(entity);
        } else {
            interviewMapper.updateById(entity);
        }
        return entity.getId();
    }

    public void submitFeedback(RecruitFeedbackRequest request) {
        RecruitInterview interview = support.requireInterview(request.getInterviewId());
        List<Long> interviewers = support.parseIds(interview.getInterviewerIds());
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        if (!interviewers.contains(currentUserId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "你不是该场面试的面试官");
        }
        // 同一面试官只能一条评价（重提覆盖自己的）
        RecruitFeedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<RecruitFeedback>()
                .eq(RecruitFeedback::getInterviewId, request.getInterviewId())
                .eq(RecruitFeedback::getInterviewerId, currentUserId)
                .last("LIMIT 1"));
        RecruitFeedback feedback = existing == null ? new RecruitFeedback() : existing;
        feedback.setInterviewId(request.getInterviewId());
        feedback.setInterviewerId(currentUserId);
        feedback.setProfessional(request.getProfessional());
        feedback.setCommunication(request.getCommunication());
        feedback.setLogicScore(request.getLogicScore());
        feedback.setCultureMatch(request.getCultureMatch());
        feedback.setConclusion(request.getConclusion());
        feedback.setComment(request.getComment());
        if (existing == null) {
            feedbackMapper.insert(feedback);
        } else {
            feedbackMapper.updateById(feedback);
        }
        log.info("面试评价提交: interviewId={} 面试官={} 结论={}",
                request.getInterviewId(), currentUserId, request.getConclusion());
    }

    public void summarizeInterview(Long id, Integer conclusion, String summary, String continueReason) {
        RecruitInterview interview = support.requireInterview(id);
        List<Long> interviewers = support.parseIds(interview.getInterviewerIds());
        Long submitted = feedbackMapper.selectCount(new LambdaQueryWrapper<RecruitFeedback>()
                .eq(RecruitFeedback::getInterviewId, id));
        // 评价没交齐不能汇总：否则结论只代表一部分人
        if (submitted < interviewers.size()) {
            throw new BizException(ErrorCode.INTERVIEW_FEEDBACK_INCOMPLETE,
                    "还有 " + (interviewers.size() - submitted) + " 位面试官未提交评价");
        }
        // 有「不通过」仍要推进，必须说明理由
        Long notPassed = feedbackMapper.selectCount(new LambdaQueryWrapper<RecruitFeedback>()
                .eq(RecruitFeedback::getInterviewId, id)
                .eq(RecruitFeedback::getConclusion, 3));
        if (notPassed != null && notPassed > 0
                && conclusion != null && Objects.equals(conclusion, 1)
                && !StringUtils.hasText(continueReason)) {
            throw new BizException(ErrorCode.INTERVIEW_CONTINUE_REASON_REQUIRED);
        }
        RecruitInterview patch = new RecruitInterview();
        patch.setId(id);
        patch.setStatus(2);
        patch.setConclusion(conclusion);
        patch.setSummary(summary);
        patch.setContinueReason(continueReason);
        interviewMapper.updateById(patch);

        // 汇总结论推进候选人阶段：终试通过 → 待定/已发offer 由后续动作推进
        if (conclusion != null && Objects.equals(conclusion, 1)) {
            RecruitCandidate candidate = candidateMapper.selectById(interview.getCandidateId());
            if (candidate != null && candidate.getStage() != null
                    && candidate.getStage() < RecruitConstants.STAGE_OFFER
                    && candidate.getStage() < RecruitConstants.STAGE_HIRED) {
                int next = interview.getRoundNo() != null && interview.getRoundNo() >= 4
                        ? RecruitConstants.STAGE_OFFER - 1 : candidate.getStage() + 1;
                RecruitCandidate cp = new RecruitCandidate();
                cp.setId(candidate.getId());
                cp.setStage(Math.min(next, RecruitConstants.STAGE_OFFER - 1));
                candidateMapper.updateById(cp);
            }
        }
        log.info("面试汇总: id={} 结论={} 评价数={}", id, conclusion, submitted);
    }
}
