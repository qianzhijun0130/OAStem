package com.kellen.oa_system.hr.recruit.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.hr.recruit.entity.RecruitInterview;
import com.kellen.oa_system.hr.recruit.entity.RecruitOffer;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitDemandMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitInterviewMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitOfferMapper;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 招聘主线的取数与校验公共件。
 *
 * <p>五个实体域（需求/职位/候选人/面试/offer）互相引用得很密：面试要取候选人、
 * offer 要取候选人、转员工要取 offer。若每个服务各写一份 {@code requireXxx}，
 * 迟早出现「这里报『候选人不存在』、那里报 400 空消息」的不一致。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitSupport {

    private final RecruitDemandMapper demandMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final RecruitInterviewMapper interviewMapper;
    private final RecruitOfferMapper offerMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;

    public RecruitDemand requireDemand(Long id) {
        RecruitDemand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "招聘需求不存在");
        }
        return demand;
    }

    public RecruitCandidate requireCandidate(Long id) {
        RecruitCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "候选人不存在");
        }
        return candidate;
    }

    public RecruitInterview requireInterview(Long id) {
        RecruitInterview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试不存在");
        }
        return interview;
    }

    public RecruitOffer requireOffer(Long id) {
        RecruitOffer offer = offerMapper.selectById(id);
        if (offer == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Offer 不存在");
        }
        return offer;
    }

    public void requireDept(Long deptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ErrorCode.DEMAND_DEPT_INVALID);
        }
    }

    public void requirePosition(Long positionId) {
        Position position = positionMapper.selectById(positionId);
        if (position == null) {
            throw new BizException(ErrorCode.DEMAND_POSITION_INVALID);
        }
        if (position.getStatus() != null && position.getStatus() == 0) {
            throw new BizException(ErrorCode.DEMAND_POSITION_INVALID);
        }
    }

    /**
     * 解析逗号分隔的 id 串（面试官列表等）。
     *
     * <p>脏数据直接跳过，不让整个列表挂掉——这类字段来自历史导入，
     * 一条非法值把详情页打成 500 是最常见的「数据带崩页面」。</p>
     */
    public List<Long> parseIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String s : ids.split(",")) {
            String t = s.trim();
            if (StringUtils.hasText(t)) {
                try {
                    result.add(Long.parseLong(t));
                } catch (NumberFormatException ignored) {
                    // 脏数据直接跳过，不让整个列表挂掉
                }
            }
        }
        return result;
    }

    /** 手机号脱敏：138****8000 */
    public String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /** offer 是否已过期（过期不可接受、不可转员工） */
    public boolean isExpired(RecruitOffer offer) {
        return offer.getExpireDate() != null && offer.getExpireDate().isBefore(LocalDate.now());
    }
}
