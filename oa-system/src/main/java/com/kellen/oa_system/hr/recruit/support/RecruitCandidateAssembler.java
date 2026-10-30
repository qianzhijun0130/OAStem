package com.kellen.oa_system.hr.recruit.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitInterview;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitInterviewMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.vo.RecruitCandidateVO;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 候选人 VO 组装。
 *
 * <p>手机号一律输出脱敏值（{@code mobileMasked}）——简历是个人信息，
 * 列表页不需要完整号码；原值仍随 {@code mobile} 返回给有权限的编辑弹窗。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitCandidateAssembler {

    private final RecruitPostMapper postMapper;
    private final RecruitInterviewMapper interviewMapper;
    private final UserMapper userMapper;
    private final RecruitSupport support;

    public List<RecruitCandidateVO> toVOs(List<RecruitCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> postIds = candidates.stream().map(RecruitCandidate::getPostId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> postNames = postIds.isEmpty() ? new HashMap<>()
                : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(RecruitPost::getId, RecruitPost::getPostName, (a, b) -> a));
        Set<Long> referrerIds = candidates.stream().map(RecruitCandidate::getReferrerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> referrerNames = referrerIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(referrerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.requireNonNullElse(u.getRealName(), ""),
                        (a, b) -> a));
        Set<Long> candidateIds = candidates.stream().map(RecruitCandidate::getId).collect(Collectors.toSet());
        Map<Long, Long> interviewCounts = candidateIds.isEmpty() ? new HashMap<>()
                : interviewMapper.selectList(new LambdaQueryWrapper<RecruitInterview>()
                .in(RecruitInterview::getCandidateId, candidateIds)).stream()
                .collect(Collectors.groupingBy(RecruitInterview::getCandidateId, Collectors.counting()));

        List<RecruitCandidateVO> vos = new ArrayList<>(candidates.size());
        for (RecruitCandidate c : candidates) {
            RecruitCandidateVO vo = new RecruitCandidateVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setMobile(c.getMobile());
            vo.setMobileMasked(support.maskMobile(c.getMobile()));
            vo.setEmail(c.getEmail());
            vo.setGender(c.getGender());
            vo.setGenderLabel(c.getGender() == null ? null : (c.getGender() == 1 ? "男" : "女"));
            vo.setBirthDate(c.getBirthDate());
            vo.setPostId(c.getPostId());
            vo.setPostName(postNames.get(c.getPostId()));
            vo.setDemandId(c.getDemandId());
            vo.setResumeFileId(c.getResumeFileId());
            vo.setSource(c.getSource());
            vo.setSourceLabel(RecruitConstants.SOURCE_LABEL.getOrDefault(c.getSource(), "主动投递"));
            vo.setReferrerId(c.getReferrerId());
            vo.setReferrerName(referrerNames.get(c.getReferrerId()));
            vo.setStage(c.getStage());
            vo.setStageLabel(RecruitConstants.STAGE_LABEL.getOrDefault(c.getStage(), "未知"));
            vo.setRejectType(c.getRejectType());
            vo.setRejectReason(c.getRejectReason());
            vo.setExpectSalary(c.getExpectSalary());
            vo.setCurrentSalary(c.getCurrentSalary());
            vo.setEducation(c.getEducation());
            vo.setExperience(c.getExperience());
            vo.setAuthorized(c.getAuthorized());
            vo.setTalentPool(c.getTalentPool());
            vo.setPoolExpireDate(c.getPoolExpireDate());
            vo.setUserId(c.getUserId());
            vo.setRemark(c.getRemark());
            vo.setCreateTime(c.getCreateTime());
            vo.setInterviewCount(interviewCounts.getOrDefault(c.getId(), 0L).intValue());
            vos.add(vo);
        }
        return vos;
    }
}
