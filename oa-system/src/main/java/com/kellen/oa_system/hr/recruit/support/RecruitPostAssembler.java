package com.kellen.oa_system.hr.recruit.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitDemandMapper;
import com.kellen.oa_system.hr.recruit.vo.RecruitPostVO;
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
 * 招聘职位 VO 组装。
 *
 * <p>候选人数与入职数按职位一次分组统计，而不是逐行 count——
 * 职位列表是招聘页的默认视图，逐行统计会让首屏多出 2N 次查询。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitPostAssembler {

    private final RecruitDemandMapper demandMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final UserMapper userMapper;

    public List<RecruitPostVO> toVOs(List<RecruitPost> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> demandIds = posts.stream().map(RecruitPost::getDemandId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> demandCodes = demandIds.isEmpty() ? new HashMap<>()
                : demandMapper.selectBatchIds(demandIds).stream()
                .collect(Collectors.toMap(RecruitDemand::getId,
                        d -> Objects.requireNonNullElse(d.getDemandCode(), ""), (a, b) -> a));
        Set<Long> hrIds = posts.stream().map(RecruitPost::getHrId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> hrNames = hrIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(hrIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.requireNonNullElse(u.getRealName(), ""),
                        (a, b) -> a));
        Set<Long> postIds = posts.stream().map(RecruitPost::getId).collect(Collectors.toSet());
        List<RecruitCandidate> allCandidates = postIds.isEmpty() ? List.of()
                : candidateMapper.selectList(new LambdaQueryWrapper<RecruitCandidate>()
                .in(RecruitCandidate::getPostId, postIds));
        Map<Long, Long> candidateCount = allCandidates.stream()
                .collect(Collectors.groupingBy(RecruitCandidate::getPostId, Collectors.counting()));
        Map<Long, Long> hiredCount = allCandidates.stream()
                .filter(c -> Objects.equals(c.getStage(), RecruitConstants.STAGE_HIRED))
                .collect(Collectors.groupingBy(RecruitCandidate::getPostId, Collectors.counting()));

        List<RecruitPostVO> vos = new ArrayList<>(posts.size());
        for (RecruitPost p : posts) {
            RecruitPostVO vo = new RecruitPostVO();
            vo.setId(p.getId());
            vo.setDemandId(p.getDemandId());
            vo.setDemandCode(demandCodes.get(p.getDemandId()));
            vo.setPostName(p.getPostName());
            vo.setCity(p.getCity());
            vo.setSalaryMin(p.getSalaryMin());
            vo.setSalaryMax(p.getSalaryMax());
            vo.setHeadCount(p.getHeadCount());
            vo.setChannel(p.getChannel());
            vo.setChannelLabel(RecruitConstants.CHANNEL_LABEL.getOrDefault(p.getChannel(), "内推"));
            vo.setStatus(p.getStatus());
            vo.setStatusLabel(RecruitConstants.POST_STATUS_LABEL.getOrDefault(p.getStatus(), "未知"));
            vo.setHrId(p.getHrId());
            vo.setHrName(hrNames.get(p.getHrId()));
            vo.setPublishTime(p.getPublishTime());
            vo.setRemark(p.getRemark());
            vo.setCreateTime(p.getCreateTime());
            vo.setCandidateCount(candidateCount.getOrDefault(p.getId(), 0L).intValue());
            vo.setHiredCount(hiredCount.getOrDefault(p.getId(), 0L).intValue());
            vos.add(vo);
        }
        return vos;
    }
}
