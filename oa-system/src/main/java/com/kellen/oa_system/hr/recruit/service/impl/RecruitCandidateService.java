package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.util.LogMasker;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.recruit.dto.RecruitCandidateSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitStageRequest;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.support.RecruitCandidateAssembler;
import com.kellen.oa_system.hr.recruit.support.RecruitConstants;
import com.kellen.oa_system.hr.recruit.support.RecruitSupport;
import com.kellen.oa_system.hr.recruit.vo.RecruitCandidateVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 候选人：招聘主线的核心实体。
 *
 * <p><b>手机号去重是硬约束</b>：同一手机号重复投递合并到同一人，不产生第二条。
 * 一人两条档案会让后续面试、offer、薪资全部对不上——而这类数据一旦产生，
 * 清理成本远高于当初多写一个 if。</p>
 *
 * <p><b>阶段流转</b>：只允许前进，或转「已淘汰/已放弃」；「已入职」不可变更；
 * 回退需 HR 权限 + 理由，并把理由拼进备注留痕。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitCandidateService {

    private final RecruitCandidateMapper candidateMapper;
    private final RecruitPostMapper postMapper;
    private final UserMapper userMapper;
    private final RecruitSupport support;
    private final RecruitCandidateAssembler assembler;

    public PageResult<RecruitCandidateVO> pageCandidates(String keyword, Integer stage, Integer source,
                                                         Long postId, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitCandidate> wrapper = new LambdaQueryWrapper<RecruitCandidate>()
                .orderByDesc(RecruitCandidate::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RecruitCandidate::getName, keyword)
                    .or().like(RecruitCandidate::getMobile, keyword));
        }
        wrapper.eq(stage != null, RecruitCandidate::getStage, stage);
        wrapper.eq(source != null, RecruitCandidate::getSource, source);
        wrapper.eq(postId != null, RecruitCandidate::getPostId, postId);
        Page<RecruitCandidate> page = candidateMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public RecruitCandidateVO candidateDetail(Long id) {
        RecruitCandidate candidate = support.requireCandidate(id);
        return assembler.toVOs(List.of(candidate)).get(0);
    }

    public Long saveCandidate(RecruitCandidateSaveRequest request) {
        // 未获授权不许保存：简历是个人信息，没有授权留不得
        if (!Objects.equals(request.getAuthorized(), 1)) {
            throw new BizException(ErrorCode.CANDIDATE_NOT_AUTHORIZED);
        }
        // 内推必须填内推人，否则内推奖励无从统计
        if (Objects.equals(request.getSource(), 1) && request.getReferrerId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "来源为内推时必须填写内推人");
        }
        if (request.getPostId() != null && postMapper.selectById(request.getPostId()) == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应聘职位不存在");
        }

        // 手机号去重：命中已有候选人 → 合并（更新字段），不新建
        RecruitCandidate existing = candidateMapper.selectOne(new LambdaQueryWrapper<RecruitCandidate>()
                .eq(RecruitCandidate::getMobile, request.getMobile())
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), request.getId())) {
            // 合并：补齐空字段，不覆盖已有阶段（阶段只能通过流转改）
            RecruitCandidate patch = new RecruitCandidate();
            patch.setId(existing.getId());
            if (!StringUtils.hasText(existing.getName()) && StringUtils.hasText(request.getName())) {
                patch.setName(request.getName());
            }
            if (existing.getEmail() == null) {
                patch.setEmail(request.getEmail());
            }
            if (existing.getResumeFileId() == null) {
                patch.setResumeFileId(request.getResumeFileId());
            }
            if (existing.getExpectSalary() == null) {
                patch.setExpectSalary(request.getExpectSalary());
            }
            if (existing.getCurrentSalary() == null) {
                patch.setCurrentSalary(request.getCurrentSalary());
            }
            if (existing.getEducation() == null) {
                patch.setEducation(request.getEducation());
            }
            if (existing.getExperience() == null) {
                patch.setExperience(request.getExperience());
            }
            patch.setAuthorized(1);
            candidateMapper.updateById(patch);
            // 手机号属个人信息，日志中必须脱敏（BE-OPT-11 全仓扫出的最后一处）
            log.info("候选人合并: 手机号={} 原 id={}", LogMasker.mobile(request.getMobile()), existing.getId());
            return existing.getId();
        }

        RecruitCandidate entity = request.getId() == null
                ? new RecruitCandidate() : candidateMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "候选人不存在");
        }
        entity.setName(request.getName());
        entity.setMobile(request.getMobile());
        entity.setEmail(request.getEmail());
        entity.setGender(request.getGender());
        entity.setBirthDate(request.getBirthDate());
        entity.setPostId(request.getPostId());
        entity.setDemandId(request.getDemandId());
        entity.setResumeFileId(request.getResumeFileId());
        entity.setSource(request.getSource() == null ? 5 : request.getSource());
        entity.setReferrerId(request.getReferrerId());
        entity.setExpectSalary(request.getExpectSalary());
        entity.setCurrentSalary(request.getCurrentSalary());
        entity.setEducation(request.getEducation());
        entity.setExperience(request.getExperience());
        entity.setAuthorized(1);
        entity.setTalentPool(request.getTalentPool() == null ? 0 : request.getTalentPool());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            entity.setStage(RecruitConstants.STAGE_SCREENING);
            // 简历保留 12 个月：过了就自动脱敏（文档要求）
            entity.setPoolExpireDate(LocalDate.now().plusMonths(12));
            candidateMapper.insert(entity);
        } else {
            candidateMapper.updateById(entity);
        }
        return entity.getId();
    }

    public void changeStage(Long id, RecruitStageRequest request) {
        RecruitCandidate candidate = support.requireCandidate(id);
        Integer current = candidate.getStage();
        Integer target = request.getStage();

        // 已入职不可再变更：人已经入职了，退回「复试」在业务上说不通
        if (Objects.equals(current, RecruitConstants.STAGE_HIRED)) {
            throw new BizException(ErrorCode.CANDIDATE_STAGE_FINAL);
        }
        // 淘汰/放弃是终态，也不能再变
        if (Objects.equals(current, RecruitConstants.STAGE_REJECTED)
                || Objects.equals(current, RecruitConstants.STAGE_ABANDONED)) {
            throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID, "候选人已结束招聘流程");
        }

        boolean rollback = Boolean.TRUE.equals(request.getRollback());
        if (rollback) {
            // 回退需 HR 权限 + 理由
            if (!HrRoles.isHr()) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有人事可以回退候选人阶段");
            }
            if (!StringUtils.hasText(request.getReason())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "回退候选人阶段必须填写理由");
            }
            if (target == null || target >= current) {
                throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID, "回退目标阶段必须小于当前阶段");
            }
        } else {
            // 正常推进：只能前进，或跳到淘汰/放弃
            boolean terminal = target == RecruitConstants.STAGE_REJECTED
                    || target == RecruitConstants.STAGE_ABANDONED;
            if (target == null || (target <= current && !terminal)) {
                throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID,
                        "阶段只能向前推进，或转为已淘汰/已放弃");
            }
        }

        // 淘汰必须填原因，否则事后没人知道为什么淘汰
        if (Objects.equals(target, RecruitConstants.STAGE_REJECTED)
                && !StringUtils.hasText(request.getRejectReason())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "淘汰候选人必须填写淘汰原因");
        }

        RecruitCandidate patch = new RecruitCandidate();
        patch.setId(id);
        patch.setStage(target);
        if (request.getRejectType() != null) {
            patch.setRejectType(request.getRejectType());
        }
        if (StringUtils.hasText(request.getRejectReason())) {
            patch.setRejectReason(request.getRejectReason());
        }
        if (rollback && StringUtils.hasText(request.getReason())) {
            // 回退理由拼在备注里留痕：谁在什么时候因为什么退回了
            String old = candidate.getRemark();
            String note = "[阶段回退] " + LocalDateTime.now().toLocalDate() + " "
                    + SecurityContextUtil.getCurrentUserId() + "：" + request.getReason();
            patch.setRemark(StringUtils.hasText(old) ? old + " | " + note : note);
        }
        candidateMapper.updateById(patch);
        log.info("候选人阶段变更: id={} {}→{} 回退={}", id, current, target, rollback);
    }

    /** 查重：按手机号找已有员工档案，供转员工时判断复用还是新建 */
    public RecruitCandidateVO findDuplicatedEmployee(String mobile, String name) {
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, mobile)
                .eq(User::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            return null;
        }
        RecruitCandidateVO vo = new RecruitCandidateVO();
        vo.setName(user.getRealName());
        vo.setMobile(user.getMobile());
        vo.setMobileMasked(support.maskMobile(user.getMobile()));
        vo.setUserId(user.getId());
        vo.setStage(RecruitConstants.STAGE_HIRED);
        vo.setStageLabel("已入职");
        vo.setRemark("已有员工档案（任职状态：" + user.getEmploymentStatus() + "）");
        if (StringUtils.hasText(name) && !Objects.equals(name, user.getRealName())) {
            vo.setRemark(vo.getRemark() + "，姓名与候选人不一致");
        }
        return vo;
    }
}
