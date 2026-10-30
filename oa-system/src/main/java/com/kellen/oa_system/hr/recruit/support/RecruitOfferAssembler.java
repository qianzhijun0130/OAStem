package com.kellen.oa_system.hr.recruit.support;

import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitOffer;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.vo.RecruitOfferVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Offer VO 组装。
 *
 * <p>{@code expired} 与 {@code remainDays} 是前端「能否接受/能否转员工」的唯一依据，
 * 与服务的判定必须同源——因此都走 {@link RecruitSupport#isExpired}，
 * 不在页面侧另算一遍日期。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitOfferAssembler {

    private final RecruitCandidateMapper candidateMapper;
    private final RecruitPostMapper postMapper;
    private final RecruitSupport support;

    public List<RecruitOfferVO> toVOs(List<RecruitOffer> offers) {
        if (offers.isEmpty()) {
            return List.of();
        }
        Set<Long> candidateIds = offers.stream().map(RecruitOffer::getCandidateId).collect(Collectors.toSet());
        Map<Long, RecruitCandidate> candidateMap = candidateIds.isEmpty() ? new HashMap<>()
                : candidateMapper.selectBatchIds(candidateIds).stream()
                .collect(Collectors.toMap(RecruitCandidate::getId, c -> c, (a, b) -> a));
        Set<Long> postIds = offers.stream().map(RecruitOffer::getPostId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> postNames = postIds.isEmpty() ? new HashMap<>()
                : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(RecruitPost::getId, RecruitPost::getPostName, (a, b) -> a));

        List<RecruitOfferVO> vos = new ArrayList<>(offers.size());
        for (RecruitOffer o : offers) {
            RecruitOfferVO vo = new RecruitOfferVO();
            vo.setId(o.getId());
            vo.setOfferNo(o.getOfferNo());
            vo.setCandidateId(o.getCandidateId());
            RecruitCandidate candidate = candidateMap.get(o.getCandidateId());
            if (candidate != null) {
                vo.setCandidateName(candidate.getName());
                vo.setCandidateMobile(support.maskMobile(candidate.getMobile()));
            }
            vo.setPostId(o.getPostId());
            vo.setPostName(postNames.get(o.getPostId()));
            vo.setPositionId(o.getPositionId());
            vo.setPositionName(o.getPositionName());
            vo.setSalary(o.getSalary());
            vo.setProbationMonths(o.getProbationMonths());
            vo.setReportDate(o.getReportDate());
            vo.setExpireDate(o.getExpireDate());
            vo.setExpired(support.isExpired(o) ? 1 : 0);
            vo.setRemainDays(o.getExpireDate() == null ? 0
                    : (int) ChronoUnit.DAYS.between(LocalDate.now(), o.getExpireDate()));
            vo.setStatus(o.getStatus());
            vo.setStatusLabel(RecruitConstants.OFFER_STATUS_LABEL.getOrDefault(o.getStatus(), "未知"));
            vo.setSendWay(o.getSendWay());
            vo.setSendWayLabel(RecruitConstants.SEND_WAY_LABEL.get(o.getSendWay()));
            vo.setSendTime(o.getSendTime());
            vo.setAcceptTime(o.getAcceptTime());
            vo.setRefuseReason(o.getRefuseReason());
            vo.setUserId(o.getUserId());
            vo.setConvertLog(o.getConvertLog());
            vo.setRemark(o.getRemark());
            vo.setCreateTime(o.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }
}
