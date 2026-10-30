package com.kellen.oa_system.hr.recruit.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitFeedback;
import com.kellen.oa_system.hr.recruit.entity.RecruitInterview;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitFeedbackMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.vo.RecruitFeedbackVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitInterviewVO;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试 VO 组装（含面试官与各自评价）。
 *
 * <p>{@code allSubmitted} 是「能否汇总」的前置条件，必须由这里算：
 * 面试官 id 来自逗号串、评价来自子表，两边的解析规则一旦不同，
 * 页面会显示「可以汇总」而服务端报「还有 N 位未提交」。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitInterviewAssembler {

    private final RecruitCandidateMapper candidateMapper;
    private final RecruitFeedbackMapper feedbackMapper;
    private final RecruitPostMapper postMapper;
    private final UserMapper userMapper;
    private final RecruitSupport support;

    public List<RecruitInterviewVO> toVOs(List<RecruitInterview> interviews) {
        if (interviews.isEmpty()) {
            return List.of();
        }
        Set<Long> candidateIds = interviews.stream()
                .map(RecruitInterview::getCandidateId).collect(Collectors.toSet());
        Map<Long, RecruitCandidate> candidateMap = candidateIds.isEmpty() ? new HashMap<>()
                : candidateMapper.selectBatchIds(candidateIds).stream()
                .collect(Collectors.toMap(RecruitCandidate::getId, c -> c, (a, b) -> a));
        Set<Long> interviewIds = interviews.stream().map(RecruitInterview::getId).collect(Collectors.toSet());
        List<RecruitFeedback> feedbacks = interviewIds.isEmpty() ? List.of()
                : feedbackMapper.selectList(new LambdaQueryWrapper<RecruitFeedback>()
                .in(RecruitFeedback::getInterviewId, interviewIds));
        Map<Long, List<RecruitFeedback>> feedbackMap = feedbacks.stream()
                .collect(Collectors.groupingBy(RecruitFeedback::getInterviewId));
        Set<Long> allInterviewerIds = feedbacks.stream().map(RecruitFeedback::getInterviewerId)
                .collect(Collectors.toCollection(HashSet::new));
        for (RecruitInterview i : interviews) {
            allInterviewerIds.addAll(support.parseIds(i.getInterviewerIds()));
        }
        Map<Long, String> userNames = allInterviewerIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(allInterviewerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.requireNonNullElse(u.getRealName(), ""),
                        (a, b) -> a));
        Set<Long> postIds = interviews.stream().map(RecruitInterview::getPostId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> postNames = postIds.isEmpty() ? new HashMap<>()
                : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(RecruitPost::getId, RecruitPost::getPostName, (a, b) -> a));

        List<RecruitInterviewVO> vos = new ArrayList<>(interviews.size());
        for (RecruitInterview i : interviews) {
            RecruitInterviewVO vo = new RecruitInterviewVO();
            vo.setId(i.getId());
            vo.setCandidateId(i.getCandidateId());
            RecruitCandidate candidate = candidateMap.get(i.getCandidateId());
            if (candidate != null) {
                vo.setCandidateName(candidate.getName());
                vo.setCandidateMobile(support.maskMobile(candidate.getMobile()));
            }
            vo.setPostId(i.getPostId());
            vo.setPostName(postNames.get(i.getPostId()));
            vo.setRoundNo(i.getRoundNo());
            vo.setInterviewTime(i.getInterviewTime());
            vo.setWay(i.getWay());
            vo.setWayLabel(RecruitConstants.WAY_LABEL.getOrDefault(i.getWay(), "现场"));
            vo.setLocation(i.getLocation());
            vo.setInterviewerIds(i.getInterviewerIds());
            List<Long> ids = support.parseIds(i.getInterviewerIds());
            vo.setInterviewerNames(ids.stream().map(userNames::get)
                    .filter(Objects::nonNull).toList());
            vo.setStatus(i.getStatus());
            vo.setStatusLabel(RecruitConstants.INTERVIEW_STATUS_LABEL.getOrDefault(i.getStatus(), "未知"));
            vo.setConclusion(i.getConclusion());
            vo.setConclusionLabel(RecruitConstants.CONCLUSION_LABEL.get(i.getConclusion()));
            vo.setSummary(i.getSummary());
            vo.setContinueReason(i.getContinueReason());
            vo.setRemark(i.getRemark());
            vo.setCreateTime(i.getCreateTime());
            List<RecruitFeedback> list = feedbackMap.getOrDefault(i.getId(), List.of());
            vo.setFeedbackCount(list.size());
            vo.setInterviewerCount(ids.size());
            vo.setAllSubmitted(!ids.isEmpty() && list.size() >= ids.size());
            vo.setFeedbacks(list.stream().map(f -> {
                RecruitFeedbackVO fvo = new RecruitFeedbackVO();
                fvo.setId(f.getId());
                fvo.setInterviewId(f.getInterviewId());
                fvo.setInterviewerId(f.getInterviewerId());
                fvo.setInterviewerName(userNames.get(f.getInterviewerId()));
                fvo.setProfessional(f.getProfessional());
                fvo.setCommunication(f.getCommunication());
                fvo.setLogicScore(f.getLogicScore());
                fvo.setCultureMatch(f.getCultureMatch());
                fvo.setConclusion(f.getConclusion());
                fvo.setConclusionLabel(RecruitConstants.CONCLUSION_LABEL.get(f.getConclusion()));
                fvo.setComment(f.getComment());
                fvo.setCreateTime(f.getCreateTime());
                return fvo;
            }).toList());
            vos.add(vo);
        }
        return vos;
    }
}
