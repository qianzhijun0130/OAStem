package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.recruit.dto.RecruitDemandSaveRequest;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.hr.recruit.mapper.RecruitDemandMapper;
import com.kellen.oa_system.hr.recruit.support.RecruitConstants;
import com.kellen.oa_system.hr.recruit.support.RecruitDemandAssembler;
import com.kellen.oa_system.hr.recruit.support.RecruitQuotaSupport;
import com.kellen.oa_system.hr.recruit.support.RecruitSupport;
import com.kellen.oa_system.hr.recruit.vo.RecruitDemandVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 招聘需求：整条流水线的起点。
 *
 * <p>两个不可跳过的约束：<b>已通过审批的需求不能直接改</b>（改了编制口径，审批人批的就不是这份了）、
 * <b>只有待审批才能审批</b>（否则可以反复「审批」把时间戳刷成招聘周期起点）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitDemandService {

    private final RecruitDemandMapper demandMapper;
    private final RecruitSupport support;
    private final RecruitQuotaSupport quotaSupport;
    private final RecruitDemandAssembler assembler;

    public PageResult<RecruitDemandVO> pageDemands(String keyword, Integer status, Long deptId,
                                                    Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitDemand> wrapper = new LambdaQueryWrapper<RecruitDemand>()
                .orderByDesc(RecruitDemand::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RecruitDemand::getDemandCode, keyword)
                    .or().like(RecruitDemand::getReason, keyword));
        }
        wrapper.eq(status != null, RecruitDemand::getStatus, status);
        wrapper.eq(deptId != null, RecruitDemand::getDeptId, deptId);
        Page<RecruitDemand> page = demandMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public RecruitDemandVO demandDetail(Long id) {
        RecruitDemand demand = support.requireDemand(id);
        return assembler.toVOs(List.of(demand)).get(0);
    }

    public Long saveDemand(RecruitDemandSaveRequest request) {
        support.requireDept(request.getDeptId());
        support.requirePosition(request.getPositionId());

        RecruitDemand entity = request.getId() == null
                ? new RecruitDemand() : demandMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "需求不存在");
        }
        // 已通过审批的需求不能直接改：改了编制口径，审批人批的就不是这份了
        if (entity.getId() != null && Objects.equals(entity.getStatus(), RecruitConstants.DEMAND_RECRUITING)) {
            throw new BizException(ErrorCode.DEMAND_APPROVED_READONLY);
        }
        entity.setDeptId(request.getDeptId());
        entity.setPositionId(request.getPositionId());
        entity.setHeadCount(request.getHeadCount());
        entity.setUrgency(request.getUrgency() == null ? 1 : request.getUrgency());
        entity.setExpectDate(request.getExpectDate());
        entity.setEducation(request.getEducation());
        entity.setExperience(request.getExperience());
        entity.setSkillRequire(request.getSkillRequire());
        entity.setSalaryMin(request.getSalaryMin());
        entity.setSalaryMax(request.getSalaryMax());
        entity.setReasonType(request.getReasonType() == null ? 1 : request.getReasonType());
        entity.setReason(request.getReason());
        entity.setHrId(request.getHrId() == null ? SecurityContextUtil.getCurrentUserId() : request.getHrId());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            entity.setDemandCode(nextDemandCode());
            entity.setStatus(RecruitConstants.DEMAND_PENDING);
            demandMapper.insert(entity);
        } else {
            demandMapper.updateById(entity);
        }
        log.info("招聘需求保存: id={} code={} 人数={} 超编制={}",
                entity.getId(), entity.getDemandCode(), entity.getHeadCount(),
                quotaSupport.isOverQuota(entity) ? "是" : "否");
        return entity.getId();
    }

    public void closeDemand(Long id) {
        RecruitDemand demand = support.requireDemand(id);
        if (Objects.equals(demand.getStatus(), RecruitConstants.DEMAND_CLOSED)) {
            return;
        }
        RecruitDemand patch = new RecruitDemand();
        patch.setId(id);
        patch.setStatus(RecruitConstants.DEMAND_CLOSED);
        patch.setFinishTime(LocalDateTime.now());
        demandMapper.updateById(patch);
        log.info("招聘需求关闭: id={}", id);
    }

    public void approveDemand(Long id) {
        RecruitDemand demand = support.requireDemand(id);
        if (!Objects.equals(demand.getStatus(), RecruitConstants.DEMAND_PENDING)) {
            throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID, "该需求不在待审批状态");
        }
        RecruitDemand patch = new RecruitDemand();
        patch.setId(id);
        patch.setStatus(RecruitConstants.DEMAND_RECRUITING);
        patch.setApproveTime(LocalDateTime.now());
        demandMapper.updateById(patch);
        log.info("招聘需求审批通过: id={} code={}", id, demand.getDemandCode());
    }

    /**
     * 需求编号：{@code ZP-{yyyyMM}-{4 位流水}}。
     *
     * <p>流水按「当月已有需求数 +1」生成。因为需求是低频操作（一个月几十条），
     * 用计数方式足够，也省掉了一张流水表。</p>
     */
    private String nextDemandCode() {
        String prefix = "ZP-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        Long cnt = demandMapper.selectCount(new LambdaQueryWrapper<RecruitDemand>()
                .likeRight(RecruitDemand::getDemandCode, prefix));
        return prefix + String.format("%04d", (cnt == null ? 0 : cnt) + 1);
    }
}
