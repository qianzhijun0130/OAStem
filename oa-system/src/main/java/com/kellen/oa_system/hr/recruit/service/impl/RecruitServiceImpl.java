package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.hr.recruit.dto.RecruitCandidateSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitConvertRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitDemandSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitFeedbackRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitInterviewSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitOfferSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitPostSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitStageRequest;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.hr.recruit.entity.RecruitFeedback;
import com.kellen.oa_system.hr.recruit.entity.RecruitInterview;
import com.kellen.oa_system.hr.recruit.entity.RecruitOffer;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitFeedbackMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitInterviewMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitOfferMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitDemandMapper;
import com.kellen.oa_system.hr.recruit.service.RecruitService;
import com.kellen.oa_system.hr.recruit.vo.RecruitBoardVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitCandidateVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitDemandVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitFeedbackVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitInterviewVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitOfferVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitPostVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 招聘管理实现。
 *
 * <p><b>这个类里最要紧的三件事</b>：</p>
 * <ol>
 *   <li><b>候选人手机号去重</b>：同一手机号重复投递合并到同一人，不产生第二条；
 *       一人两条档案会让后续面试、offer、考勤全部对不上。</li>
 *   <li><b>阶段流转</b>：只允许前进或淘汰/放弃；「已入职」不可回退；
 *       回退需 HR 权限 + 理由并留痕。</li>
 *   <li><b>offer 转员工</b>：按「手机号 + 姓名」查重，命中已有档案时
 *       必须显式选择复用或新建，选择记进留痕——一人两条档案会让薪资串数据。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitServiceImpl implements RecruitService {

    private static final Map<Integer, String> DEMAND_STATUS_LABEL = Map.of(
            1, "待审批", 2, "招聘中", 3, "已暂停", 4, "已完成", 5, "已关闭", 6, "已驳回");

    private static final Map<Integer, String> URGENCY_LABEL = Map.of(1, "普通", 2, "紧急");

    private static final Map<Integer, String> REASON_TYPE_LABEL = Map.of(
            1, "新增", 2, "替补", 3, "储备");

    private static final Map<Integer, String> STAGE_LABEL = Map.of(
            1, "待筛选", 2, "初试", 3, "复试", 4, "终试", 5, "待定",
            6, "已发offer", 7, "已入职", 8, "已淘汰", 9, "已放弃");

    private static final Map<Integer, String> SOURCE_LABEL = Map.of(
            1, "内推", 2, "招聘平台", 3, "猎头", 4, "校招", 5, "主动投递");

    private static final Map<Integer, String> REJECT_LABEL = Map.of(
            1, "技能不匹配", 2, "经验不足", 3, "学历不符", 4, "薪资不匹配",
            5, "文化不匹配", 6, "候选人放弃");

    private static final Map<Integer, String> CHANNEL_LABEL = Map.of(
            1, "内推", 2, "官网", 3, "招聘平台");

    private static final Map<Integer, String> POST_STATUS_LABEL = Map.of(
            1, "草稿", 2, "已发布", 3, "已下线");

    private static final Map<Integer, String> INTERVIEW_STATUS_LABEL = Map.of(
            1, "待面试", 2, "已汇总", 3, "已取消");

    private static final Map<Integer, String> CONCLUSION_LABEL = Map.of(
            1, "通过", 2, "待定", 3, "不通过");

    private static final Map<Integer, String> WAY_LABEL = Map.of(
            1, "现场", 2, "电话", 3, "视频");

    private static final Map<Integer, String> OFFER_STATUS_LABEL = Map.of(
            1, "待审批", 2, "已发出", 3, "已接受", 4, "已拒绝", 5, "已过期", 6, "已作废");

    private static final Map<Integer, String> SEND_WAY_LABEL = Map.of(
            1, "邮件", 2, "短信", 3, "纸质");

    /** 需求状态 */
    private static final int DEMAND_PENDING = 1;
    private static final int DEMAND_RECRUITING = 2;
    private static final int DEMAND_CLOSED = 5;

    /** 候选人阶段 */
    private static final int STAGE_SCREENING = 1;
    private static final int STAGE_OFFER = 6;
    private static final int STAGE_HIRED = 7;
    private static final int STAGE_REJECTED = 8;
    private static final int STAGE_ABANDONED = 9;

    /** offer 状态 */
    private static final int OFFER_PENDING = 1;
    private static final int OFFER_SENT = 2;
    private static final int OFFER_ACCEPTED = 3;
    private static final int OFFER_REFUSED = 4;
    private static final int OFFER_EXPIRED = 5;

    /** offer 默认有效期：5 个工作日 */
    private static final int OFFER_VALID_DAYS = 7;

    private final RecruitDemandMapper demandMapper;
    private final RecruitPostMapper postMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final RecruitInterviewMapper interviewMapper;
    private final RecruitFeedbackMapper feedbackMapper;
    private final RecruitOfferMapper offerMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserServiceImpl userService;

    // ==================================================================
    // 需求
    // ==================================================================

    @Override
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
        return PageResult.of(toDemandVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public RecruitDemandVO demandDetail(Long id) {
        RecruitDemand demand = requireDemand(id);
        return toDemandVOs(List.of(demand)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDemand(RecruitDemandSaveRequest request) {
        requireDept(request.getDeptId());
        requirePosition(request.getPositionId());

        RecruitDemand entity = request.getId() == null
                ? new RecruitDemand() : demandMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "需求不存在");
        }
        // 已通过审批的需求不能直接改：改了编制口径，审批人批的就不是这份了
        if (entity.getId() != null && Objects.equals(entity.getStatus(), DEMAND_RECRUITING)) {
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
            entity.setStatus(DEMAND_PENDING);
            demandMapper.insert(entity);
        } else {
            demandMapper.updateById(entity);
        }
        log.info("招聘需求保存: id={} code={} 人数={} 超编制={}",
                entity.getId(), entity.getDemandCode(), entity.getHeadCount(),
                isOverQuota(entity) ? "是" : "否");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeDemand(Long id) {
        RecruitDemand demand = requireDemand(id);
        if (Objects.equals(demand.getStatus(), DEMAND_CLOSED)) {
            return;
        }
        RecruitDemand patch = new RecruitDemand();
        patch.setId(id);
        patch.setStatus(DEMAND_CLOSED);
        patch.setFinishTime(LocalDateTime.now());
        demandMapper.updateById(patch);
        log.info("招聘需求关闭: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveDemand(Long id) {
        RecruitDemand demand = requireDemand(id);
        if (!Objects.equals(demand.getStatus(), DEMAND_PENDING)) {
            throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID, "该需求不在待审批状态");
        }
        RecruitDemand patch = new RecruitDemand();
        patch.setId(id);
        patch.setStatus(DEMAND_RECRUITING);
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

    /** 编制缺口：岗位编制数 - 该部门该岗位的在岗人数 */
    private int[] quotaOf(RecruitDemand demand) {
        Position position = positionMapper.selectById(demand.getPositionId());
        int total = position == null || position.getHeadcount() == null ? 0 : position.getHeadcount();
        Long used = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getDeptId, demand.getDeptId())
                .eq(User::getPositionId, demand.getPositionId())
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4)));
        int usedInt = used == null ? 0 : used.intValue();
        return new int[]{total, usedInt, Math.max(total - usedInt, 0)};
    }

    /** 申请人数 > 编制缺口 → 超编制（只提示不拦截） */
    private boolean isOverQuota(RecruitDemand demand) {
        int[] quota = quotaOf(demand);
        return demand.getHeadCount() != null && demand.getHeadCount() > quota[2];
    }

    // ==================================================================
    // 职位
    // ==================================================================

    @Override
    public PageResult<RecruitPostVO> pagePosts(String keyword, Integer status, Long demandId,
                                                Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitPost> wrapper = new LambdaQueryWrapper<RecruitPost>()
                .orderByDesc(RecruitPost::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(RecruitPost::getPostName, keyword);
        }
        wrapper.eq(status != null, RecruitPost::getStatus, status);
        wrapper.eq(demandId != null, RecruitPost::getDemandId, demandId);
        Page<RecruitPost> page = postMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toPostVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePost(RecruitPostSaveRequest request) {
        RecruitPost entity = request.getId() == null
                ? new RecruitPost() : postMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "职位不存在");
        }
        if (request.getDemandId() != null) {
            requireDemand(request.getDemandId());
        }
        entity.setDemandId(request.getDemandId());
        entity.setPostName(request.getPostName());
        entity.setCity(request.getCity());
        entity.setSalaryMin(request.getSalaryMin());
        entity.setSalaryMax(request.getSalaryMax());
        entity.setHeadCount(request.getHeadCount());
        entity.setChannel(request.getChannel() == null ? 1 : request.getChannel());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setHrId(request.getHrId() == null ? SecurityContextUtil.getCurrentUserId() : request.getHrId());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            postMapper.insert(entity);
        } else {
            postMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        Long used = candidateMapper.selectCount(new LambdaQueryWrapper<RecruitCandidate>()
                .eq(RecruitCandidate::getPostId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该职位下已有候选人，不能删除，可改为下线");
        }
        postMapper.deleteById(id);
    }

    // ==================================================================
    // 候选人
    // ==================================================================

    @Override
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
        return PageResult.of(toCandidateVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public RecruitCandidateVO candidateDetail(Long id) {
        RecruitCandidate candidate = requireCandidate(id);
        return toCandidateVOs(List.of(candidate)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
            log.info("候选人合并: 手机号={} 原 id={}", request.getMobile(), existing.getId());
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
            entity.setStage(STAGE_SCREENING);
            // 简历保留 12 个月：过了就自动脱敏（文档要求）
            entity.setPoolExpireDate(LocalDate.now().plusMonths(12));
            candidateMapper.insert(entity);
        } else {
            candidateMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(Long id, RecruitStageRequest request) {
        RecruitCandidate candidate = requireCandidate(id);
        Integer current = candidate.getStage();
        Integer target = request.getStage();

        // 已入职不可再变更：人已经入职了，退回「复试」在业务上说不通
        if (Objects.equals(current, STAGE_HIRED)) {
            throw new BizException(ErrorCode.CANDIDATE_STAGE_FINAL);
        }
        // 淘汰/放弃是终态，也不能再变
        if (Objects.equals(current, STAGE_REJECTED) || Objects.equals(current, STAGE_ABANDONED)) {
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
            boolean terminal = target == STAGE_REJECTED || target == STAGE_ABANDONED;
            if (target == null || (target <= current && !terminal)) {
                throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID,
                        "阶段只能向前推进，或转为已淘汰/已放弃");
            }
        }

        // 淘汰必须填原因，否则事后没人知道为什么淘汰
        if (Objects.equals(target, STAGE_REJECTED) && !StringUtils.hasText(request.getRejectReason())) {
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

    @Override
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
        vo.setMobileMasked(maskMobile(user.getMobile()));
        vo.setUserId(user.getId());
        vo.setStage(STAGE_HIRED);
        vo.setStageLabel("已入职");
        vo.setRemark("已有员工档案（任职状态：" + user.getEmploymentStatus() + "）");
        if (StringUtils.hasText(name) && !Objects.equals(name, user.getRealName())) {
            vo.setRemark(vo.getRemark() + "，姓名与候选人不一致");
        }
        return vo;
    }

    // ==================================================================
    // 面试
    // ==================================================================

    @Override
    public PageResult<RecruitInterviewVO> pageInterviews(Long candidateId, Integer status,
                                                          Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitInterview> wrapper = new LambdaQueryWrapper<RecruitInterview>()
                .orderByDesc(RecruitInterview::getInterviewTime);
        wrapper.eq(candidateId != null, RecruitInterview::getCandidateId, candidateId);
        wrapper.eq(status != null, RecruitInterview::getStatus, status);
        Page<RecruitInterview> page = interviewMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toInterviewVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public RecruitInterviewVO interviewDetail(Long id) {
        RecruitInterview interview = requireInterview(id);
        return toInterviewVOs(List.of(interview)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveInterview(RecruitInterviewSaveRequest request) {
        RecruitCandidate candidate = requireCandidate(request.getCandidateId());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(RecruitFeedbackRequest request) {
        RecruitInterview interview = requireInterview(request.getInterviewId());
        List<Long> interviewers = parseIds(interview.getInterviewerIds());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void summarizeInterview(Long id, Integer conclusion, String summary, String continueReason) {
        RecruitInterview interview = requireInterview(id);
        List<Long> interviewers = parseIds(interview.getInterviewerIds());
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
                    && candidate.getStage() < STAGE_OFFER && candidate.getStage() < STAGE_HIRED) {
                int next = interview.getRoundNo() != null && interview.getRoundNo() >= 4
                        ? STAGE_OFFER - 1 : candidate.getStage() + 1;
                RecruitCandidate cp = new RecruitCandidate();
                cp.setId(candidate.getId());
                cp.setStage(Math.min(next, STAGE_OFFER - 1));
                candidateMapper.updateById(cp);
            }
        }
        log.info("面试汇总: id={} 结论={} 评价数={}", id, conclusion, submitted);
    }

    // ==================================================================
    // Offer
    // ==================================================================

    @Override
    public PageResult<RecruitOfferVO> pageOffers(Integer status, Long candidateId,
                                                  Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitOffer> wrapper = new LambdaQueryWrapper<RecruitOffer>()
                .orderByDesc(RecruitOffer::getId);
        wrapper.eq(status != null, RecruitOffer::getStatus, status);
        wrapper.eq(candidateId != null, RecruitOffer::getCandidateId, candidateId);
        Page<RecruitOffer> page = offerMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toOfferVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    @Override
    public RecruitOfferVO offerDetail(Long id) {
        RecruitOffer offer = requireOffer(id);
        return toOfferVOs(List.of(offer)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOffer(RecruitOfferSaveRequest request) {
        RecruitCandidate candidate = requireCandidate(request.getCandidateId());
        // 候选人必须到终试阶段才能发 offer
        if (candidate.getStage() == null || candidate.getStage() < 4) {
            throw new BizException(ErrorCode.RECRUIT_STAGE_INVALID, "候选人尚未进入终试阶段，不能发起 offer");
        }
        RecruitOffer entity = new RecruitOffer();
        entity.setCandidateId(request.getCandidateId());
        entity.setPostId(request.getPostId() == null ? candidate.getPostId() : request.getPostId());
        entity.setPositionId(request.getPositionId());
        entity.setPositionName(request.getPositionName());
        entity.setSalary(request.getSalary());
        entity.setProbationMonths(request.getProbationMonths() == null ? 3 : request.getProbationMonths());
        entity.setReportDate(request.getReportDate());
        // 有效期默认 5 个工作日（自然日约 7 天）
        entity.setExpireDate(request.getExpireDate() == null
                ? LocalDate.now().plusDays(OFFER_VALID_DAYS) : request.getExpireDate());
        entity.setStatus(OFFER_PENDING);
        entity.setSendWay(request.getSendWay());
        entity.setRemark(request.getRemark());
        entity.setOfferNo(nextOfferNo());
        offerMapper.insert(entity);
        log.info("Offer 发起: id={} no={} 候选人={}", entity.getId(), entity.getOfferNo(),
                request.getCandidateId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendOffer(Long id, Integer sendWay) {
        RecruitOffer offer = requireOffer(id);
        if (!Objects.equals(offer.getStatus(), OFFER_PENDING)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID);
        }
        RecruitOffer patch = new RecruitOffer();
        patch.setId(id);
        patch.setStatus(OFFER_SENT);
        patch.setSendWay(sendWay == null ? 1 : sendWay);
        patch.setSendTime(LocalDateTime.now());
        offerMapper.updateById(patch);
        // 候选人阶段推进到「已发offer」
        RecruitCandidate cp = new RecruitCandidate();
        cp.setId(offer.getCandidateId());
        cp.setStage(STAGE_OFFER);
        candidateMapper.updateById(cp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOffer(Long id) {
        RecruitOffer offer = requireOffer(id);
        if (!Objects.equals(offer.getStatus(), OFFER_SENT)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID);
        }
        if (isExpired(offer)) {
            throw new BizException(ErrorCode.OFFER_EXPIRED);
        }
        RecruitOffer patch = new RecruitOffer();
        patch.setId(id);
        patch.setStatus(OFFER_ACCEPTED);
        patch.setAcceptTime(LocalDateTime.now());
        offerMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refuseOffer(Long id, String reason) {
        RecruitOffer offer = requireOffer(id);
        if (!Objects.equals(offer.getStatus(), OFFER_SENT)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID);
        }
        RecruitOffer patch = new RecruitOffer();
        patch.setId(id);
        patch.setStatus(OFFER_REFUSED);
        patch.setRefuseReason(reason);
        offerMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToEmployee(RecruitConvertRequest request) {
        RecruitOffer offer = requireOffer(request.getOfferId());
        // 过期 offer 不能转员工：候选人可能已经接受了别家
        if (isExpired(offer)) {
            RecruitOffer expirePatch = new RecruitOffer();
            expirePatch.setId(offer.getId());
            expirePatch.setStatus(OFFER_EXPIRED);
            offerMapper.updateById(expirePatch);
            throw new BizException(ErrorCode.OFFER_EXPIRED);
        }
        if (!Objects.equals(offer.getStatus(), OFFER_ACCEPTED)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID, "只有已接受的 offer 才能转员工");
        }
        RecruitCandidate candidate = requireCandidate(offer.getCandidateId());

        // 按手机号查重：命中已有档案时必须显式选择复用/新建
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, candidate.getMobile())
                .eq(User::getIsDeleted, 0)
                .last("LIMIT 1"));
        Long userId;
        String convertLog;
        if (existing != null) {
            int mode = request.getReuseMode() == null ? 0 : request.getReuseMode();
            if (mode == 1) {
                userId = existing.getId();
                convertLog = "复用已有员工档案 id=" + existing.getId() + "（任职状态="
                        + existing.getEmploymentStatus() + "），操作人=" + SecurityContextUtil.getCurrentUserId();
                // 复用时把任职状态改回「待入职」：返聘场景
                User patch = new User();
                patch.setId(userId);
                patch.setEmploymentStatus(1);
                userMapper.updateById(patch);
            } else if (mode == 2) {
                userId = createEmployee(candidate, offer, request.getWorkNumber());
                convertLog = "经确认新建员工档案 id=" + userId + "（同手机号已有档案 id="
                        + existing.getId() + "），操作人=" + SecurityContextUtil.getCurrentUserId();
            } else {
                throw new BizException(ErrorCode.CONVERT_TARGET_REQUIRED);
            }
        } else {
            userId = createEmployee(candidate, offer, request.getWorkNumber());
            convertLog = "新建员工档案 id=" + userId + "，操作人=" + SecurityContextUtil.getCurrentUserId();
        }

        RecruitOffer patch = new RecruitOffer();
        patch.setId(offer.getId());
        patch.setUserId(userId);
        patch.setConvertLog(convertLog);
        offerMapper.updateById(patch);

        RecruitCandidate cp = new RecruitCandidate();
        cp.setId(candidate.getId());
        cp.setStage(STAGE_HIRED);
        cp.setUserId(userId);
        candidateMapper.updateById(cp);

        log.info("Offer 转员工: offerId={} 候选人={} 员工={} 留痕={}",
                offer.getId(), candidate.getId(), userId, convertLog);
        return userId;
    }

    /**
     * 新建员工档案（任职状态 = 待入职）。
     *
     * <p>工号按「部门前缀 + 3 位流水」生成，与员工档案模块同口径；
     * 这里不直接写 oa_user 的 work_no_seq，而是复用部门流水规则，
     * 避免两处各算一套导致工号冲突。</p>
     */
    private Long createEmployee(RecruitCandidate candidate, RecruitOffer offer, String workNumber) {
        User user = new User();
        user.setUsername(generateUsername(candidate.getMobile()));
        user.setRealName(candidate.getName());
        user.setMobile(candidate.getMobile());
        user.setEmail(candidate.getEmail());
        user.setGender(candidate.getGender());
        user.setEmploymentStatus(1);
        user.setEntryDate(offer.getReportDate());
        if (offer.getPositionId() != null) {
            user.setPositionId(offer.getPositionId());
            Position position = positionMapper.selectById(offer.getPositionId());
            user.setPosition(position == null ? null : position.getPositionName());
            user.setDeptId(position == null ? null : position.getDeptId());
        }
        // 公司由部门反查（与员工档案模块同一套口径，避免账号无公司归属）
        if (user.getDeptId() != null) {
            user.setCompanyId(userService.resolveCompanyId(user.getDeptId(), null));
        }
        user.setWorkNumber(workNumber);
        user.setStatus(1);
        user.setPassword(passwordEncoder.encode(UserServiceImpl.DEFAULT_PASSWORD));
        userMapper.insert(user);
        // 新账号必须绑定默认「普通员工」角色，否则登录后菜单为空、接口全 403
        userService.assignDefaultEmployeeRole(user.getId());
        return user.getId();
    }

    /** 用户名：手机号后 8 位 + 随机后缀，保证唯一且可读 */
    private String generateUsername(String mobile) {
        String base = mobile != null && mobile.length() >= 8
                ? mobile.substring(mobile.length() - 8) : "hr" + System.currentTimeMillis() % 100000;
        String username = "cand_" + base;
        Long cnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, username));
        return cnt != null && cnt > 0 ? username + "_" + (cnt + 1) : username;
    }

    private String nextOfferNo() {
        String prefix = "OF-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        Long cnt = offerMapper.selectCount(new LambdaQueryWrapper<RecruitOffer>()
                .likeRight(RecruitOffer::getOfferNo, prefix));
        return prefix + String.format("%04d", (cnt == null ? 0 : cnt) + 1);
    }

    private boolean isExpired(RecruitOffer offer) {
        return offer.getExpireDate() != null && offer.getExpireDate().isBefore(LocalDate.now());
    }

    // ==================================================================
    // 看板
    // ==================================================================

    @Override
    public RecruitBoardVO board(Long deptId) {
        RecruitBoardVO vo = new RecruitBoardVO();

        List<RecruitPost> posts = postMapper.selectList(new LambdaQueryWrapper<RecruitPost>()
                .eq(RecruitPost::getStatus, 2));
        List<RecruitDemand> demands = demandMapper.selectList(new LambdaQueryWrapper<RecruitDemand>()
                .in(RecruitDemand::getStatus, List.of(DEMAND_RECRUITING, 4)));
        if (deptId != null) {
            demands = demands.stream().filter(d -> Objects.equals(d.getDeptId(), deptId)).toList();
        }
        List<RecruitCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<RecruitCandidate>());

        vo.setPostingCount(posts.size());
        vo.setDemandHeadCount(demands.stream().map(d -> d.getHeadCount() == null ? 0 : d.getHeadCount())
                .reduce(0, Integer::sum));
        vo.setHiredCount((int) candidates.stream()
                .filter(c -> Objects.equals(c.getStage(), STAGE_HIRED)).count());

        // 平均招聘周期：审批通过 → 完成
        List<Long> cycles = demands.stream()
                .filter(d -> d.getApproveTime() != null && d.getFinishTime() != null)
                .map(d -> ChronoUnit.DAYS.between(d.getApproveTime().toLocalDate(), d.getFinishTime().toLocalDate()))
                .toList();
        vo.setAvgCycleDays(cycles.isEmpty() ? 0
                : (int) Math.round(cycles.stream().mapToLong(Long::longValue).average().orElse(0)));

        // 阶段漏斗
        List<RecruitBoardVO.StageCount> funnel = new ArrayList<>();
        int[] funnelStages = {1, 2, 3, 4, 5, 6, 7};
        int prev = 0;
        for (int stage : funnelStages) {
            RecruitBoardVO.StageCount item = new RecruitBoardVO.StageCount();
            item.setStage(stage);
            item.setLabel(STAGE_LABEL.get(stage));
            int count = (int) candidates.stream().filter(c -> Objects.equals(c.getStage(), stage)).count();
            item.setCount(count);
            // 转化率：相对上一阶段
            item.setConvertRate(prev == 0 ? 100 : (count * 100) / prev);
            prev = count;
            funnel.add(item);
        }
        vo.setStageFunnel(funnel);

        // 渠道转化
        List<RecruitBoardVO.ChannelStat> channels = new ArrayList<>();
        for (int s = 1; s <= 5; s++) {
            // ⚠️ 循环变量不是 effectively final，lambda 里不能直接捕获
            final int source = s;
            RecruitBoardVO.ChannelStat stat = new RecruitBoardVO.ChannelStat();
            stat.setSource(source);
            stat.setLabel(SOURCE_LABEL.get(source));
            List<RecruitCandidate> list = candidates.stream()
                    .filter(c -> Objects.equals(c.getSource(), source)).toList();
            stat.setResumeCount(list.size());
            Set<Long> ids = list.stream().map(RecruitCandidate::getId).collect(Collectors.toSet());
            // 注意：三元里两个分支类型必须一致（int vs Long 会编译不过）
            long interviewCount = ids.isEmpty() ? 0L : interviewMapper.selectCount(
                    new LambdaQueryWrapper<RecruitInterview>().in(RecruitInterview::getCandidateId, ids));
            stat.setInterviewCount((int) interviewCount);
            stat.setHireCount((int) list.stream().filter(c -> Objects.equals(c.getStage(), STAGE_HIRED)).count());
            stat.setInterviewRate(stat.getResumeCount() == 0 ? 0
                    : stat.getInterviewCount() * 100 / stat.getResumeCount());
            stat.setHireRate(stat.getResumeCount() == 0 ? 0
                    : stat.getHireCount() * 100 / stat.getResumeCount());
            channels.add(stat);
        }
        vo.setChannelStats(channels);

        // 淘汰原因分布
        Map<Integer, Integer> rejectMap = new LinkedHashMap<>();
        for (int t = 1; t <= 6; t++) {
            // ⚠️ 循环变量 t 不是 effectively final，lambda 里不能直接捕获 → 先复制一份
            final int rejectType = t;
            rejectMap.put(t, (int) candidates.stream()
                    .filter(c -> Objects.equals(c.getStage(), STAGE_REJECTED)
                            && Objects.equals(c.getRejectType(), rejectType)).count());
        }
        List<RecruitBoardVO.RejectStat> rejects = new ArrayList<>();
        rejectMap.forEach((type, count) -> {
            RecruitBoardVO.RejectStat stat = new RecruitBoardVO.RejectStat();
            stat.setRejectType(type);
            stat.setLabel(REJECT_LABEL.get(type));
            stat.setCount(count);
            rejects.add(stat);
        });
        vo.setRejectStats(rejects);

        // 部门维度
        Map<Long, String> deptNames = departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Map<Long, List<RecruitDemand>> byDept = demands.stream()
                .collect(Collectors.groupingBy(RecruitDemand::getDeptId));
        List<RecruitBoardVO.DeptStat> deptStats = new ArrayList<>();
        byDept.forEach((did, list) -> {
            RecruitBoardVO.DeptStat stat = new RecruitBoardVO.DeptStat();
            stat.setDeptId(did);
            stat.setDeptName(deptNames.getOrDefault(did, "未知部门"));
            stat.setDemandCount(list.size());
            stat.setHeadCount(list.stream().map(d -> d.getHeadCount() == null ? 0 : d.getHeadCount())
                    .reduce(0, Integer::sum));
            stat.setHiredCount(0);
            deptStats.add(stat);
        });
        deptStats.sort(Comparator.comparing(RecruitBoardVO.DeptStat::getHeadCount).reversed());
        vo.setDeptStats(deptStats);
        return vo;
    }

    // ==================================================================
    // 组装与工具
    // ==================================================================

    private List<RecruitDemandVO> toDemandVOs(List<RecruitDemand> demands) {
        if (demands.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = demands.stream().map(RecruitDemand::getDeptId).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? new HashMap<>()
                : departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Set<Long> positionIds = demands.stream().map(RecruitDemand::getPositionId).collect(Collectors.toSet());
        Map<Long, String> positionNames = positionIds.isEmpty() ? new HashMap<>()
                : positionMapper.selectBatchIds(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName, (a, b) -> a));
        Set<Long> hrIds = demands.stream().map(RecruitDemand::getHrId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> hrNames = hrIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(hrIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.requireNonNullElse(u.getRealName(), ""),
                        (a, b) -> a));

        List<RecruitDemandVO> vos = new ArrayList<>(demands.size());
        for (RecruitDemand d : demands) {
            RecruitDemandVO vo = new RecruitDemandVO();
            vo.setId(d.getId());
            vo.setDemandCode(d.getDemandCode());
            vo.setDeptId(d.getDeptId());
            vo.setDeptName(deptNames.get(d.getDeptId()));
            vo.setPositionId(d.getPositionId());
            vo.setPositionName(positionNames.get(d.getPositionId()));
            vo.setHeadCount(d.getHeadCount());
            vo.setUrgency(d.getUrgency());
            vo.setUrgencyLabel(URGENCY_LABEL.getOrDefault(d.getUrgency(), "普通"));
            vo.setExpectDate(d.getExpectDate());
            vo.setEducation(d.getEducation());
            vo.setExperience(d.getExperience());
            vo.setSkillRequire(d.getSkillRequire());
            vo.setSalaryMin(d.getSalaryMin());
            vo.setSalaryMax(d.getSalaryMax());
            vo.setReasonType(d.getReasonType());
            vo.setReasonTypeLabel(REASON_TYPE_LABEL.getOrDefault(d.getReasonType(), "新增"));
            vo.setReason(d.getReason());
            vo.setStatus(d.getStatus());
            vo.setStatusLabel(DEMAND_STATUS_LABEL.getOrDefault(d.getStatus(), "未知"));
            vo.setHrId(d.getHrId());
            vo.setHrName(hrNames.get(d.getHrId()));
            vo.setApproveTime(d.getApproveTime());
            vo.setFinishTime(d.getFinishTime());
            vo.setRemark(d.getRemark());
            vo.setCreateTime(d.getCreateTime());
            int[] quota = quotaOf(d);
            vo.setQuotaTotal(quota[0]);
            vo.setQuotaUsed(quota[1]);
            vo.setQuotaGap(quota[2]);
            vo.setOverQuota(isOverQuota(d) ? 1 : 0);
            if (d.getApproveTime() != null && d.getFinishTime() != null) {
                vo.setCycleDays((int) ChronoUnit.DAYS.between(
                        d.getApproveTime().toLocalDate(), d.getFinishTime().toLocalDate()));
            }
            vo.setHiredCount(0);
            vos.add(vo);
        }
        return vos;
    }

    private List<RecruitPostVO> toPostVOs(List<RecruitPost> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> demandIds = posts.stream().map(RecruitPost::getDemandId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> demandCodes = demandIds.isEmpty() ? new HashMap<>()
                : demandMapper.selectBatchIds(demandIds).stream()
                .collect(Collectors.toMap(RecruitDemand::getId,
                        d -> Objects.requireNonNullElse(d.getDemandCode(), ""), (a, b) -> a));
        Set<Long> hrIds = posts.stream().map(RecruitPost::getHrId).filter(Objects::nonNull).collect(Collectors.toSet());
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
                .filter(c -> Objects.equals(c.getStage(), STAGE_HIRED))
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
            vo.setChannelLabel(CHANNEL_LABEL.getOrDefault(p.getChannel(), "内推"));
            vo.setStatus(p.getStatus());
            vo.setStatusLabel(POST_STATUS_LABEL.getOrDefault(p.getStatus(), "未知"));
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

    private List<RecruitCandidateVO> toCandidateVOs(List<RecruitCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> postIds = candidates.stream().map(RecruitCandidate::getPostId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> postNames = postIds.isEmpty() ? new HashMap<>()
                : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(RecruitPost::getId, RecruitPost::getPostName, (a, b) -> a));
        Set<Long> referrerIds = candidates.stream().map(RecruitCandidate::getReferrerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
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
            vo.setMobileMasked(maskMobile(c.getMobile()));
            vo.setEmail(c.getEmail());
            vo.setGender(c.getGender());
            vo.setGenderLabel(c.getGender() == null ? null : (c.getGender() == 1 ? "男" : "女"));
            vo.setBirthDate(c.getBirthDate());
            vo.setPostId(c.getPostId());
            vo.setPostName(postNames.get(c.getPostId()));
            vo.setDemandId(c.getDemandId());
            vo.setResumeFileId(c.getResumeFileId());
            vo.setSource(c.getSource());
            vo.setSourceLabel(SOURCE_LABEL.getOrDefault(c.getSource(), "主动投递"));
            vo.setReferrerId(c.getReferrerId());
            vo.setReferrerName(referrerNames.get(c.getReferrerId()));
            vo.setStage(c.getStage());
            vo.setStageLabel(STAGE_LABEL.getOrDefault(c.getStage(), "未知"));
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

    private List<RecruitInterviewVO> toInterviewVOs(List<RecruitInterview> interviews) {
        if (interviews.isEmpty()) {
            return List.of();
        }
        Set<Long> candidateIds = interviews.stream().map(RecruitInterview::getCandidateId).collect(Collectors.toSet());
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
                .collect(Collectors.toSet());
        for (RecruitInterview i : interviews) {
            allInterviewerIds.addAll(parseIds(i.getInterviewerIds()));
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
                vo.setCandidateMobile(maskMobile(candidate.getMobile()));
            }
            vo.setPostId(i.getPostId());
            vo.setPostName(postNames.get(i.getPostId()));
            vo.setRoundNo(i.getRoundNo());
            vo.setInterviewTime(i.getInterviewTime());
            vo.setWay(i.getWay());
            vo.setWayLabel(WAY_LABEL.getOrDefault(i.getWay(), "现场"));
            vo.setLocation(i.getLocation());
            vo.setInterviewerIds(i.getInterviewerIds());
            List<Long> ids = parseIds(i.getInterviewerIds());
            vo.setInterviewerNames(ids.stream().map(userNames::get)
                    .filter(Objects::nonNull).toList());
            vo.setStatus(i.getStatus());
            vo.setStatusLabel(INTERVIEW_STATUS_LABEL.getOrDefault(i.getStatus(), "未知"));
            vo.setConclusion(i.getConclusion());
            vo.setConclusionLabel(CONCLUSION_LABEL.get(i.getConclusion()));
            vo.setSummary(i.getSummary());
            vo.setContinueReason(i.getContinueReason());
            vo.setRemark(i.getRemark());
            vo.setCreateTime(i.getCreateTime());
            List<RecruitFeedback> list = feedbackMap.getOrDefault(i.getId(), List.of());
            vo.setFeedbackCount(list.size());
            vo.setInterviewerCount(ids.size());
            vo.setAllSubmitted(ids.size() > 0 && list.size() >= ids.size());
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
                fvo.setConclusionLabel(CONCLUSION_LABEL.get(f.getConclusion()));
                fvo.setComment(f.getComment());
                fvo.setCreateTime(f.getCreateTime());
                return fvo;
            }).toList());
            vos.add(vo);
        }
        return vos;
    }

    private List<RecruitOfferVO> toOfferVOs(List<RecruitOffer> offers) {
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
                vo.setCandidateMobile(maskMobile(candidate.getMobile()));
            }
            vo.setPostId(o.getPostId());
            vo.setPostName(postNames.get(o.getPostId()));
            vo.setPositionId(o.getPositionId());
            vo.setPositionName(o.getPositionName());
            vo.setSalary(o.getSalary());
            vo.setProbationMonths(o.getProbationMonths());
            vo.setReportDate(o.getReportDate());
            vo.setExpireDate(o.getExpireDate());
            boolean expired = isExpired(o);
            vo.setExpired(expired ? 1 : 0);
            vo.setRemainDays(o.getExpireDate() == null ? 0
                    : (int) ChronoUnit.DAYS.between(LocalDate.now(), o.getExpireDate()));
            vo.setStatus(o.getStatus());
            vo.setStatusLabel(OFFER_STATUS_LABEL.getOrDefault(o.getStatus(), "未知"));
            vo.setSendWay(o.getSendWay());
            vo.setSendWayLabel(SEND_WAY_LABEL.get(o.getSendWay()));
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

    private RecruitDemand requireDemand(Long id) {
        RecruitDemand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "招聘需求不存在");
        }
        return demand;
    }

    private RecruitCandidate requireCandidate(Long id) {
        RecruitCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "候选人不存在");
        }
        return candidate;
    }

    private RecruitInterview requireInterview(Long id) {
        RecruitInterview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "面试不存在");
        }
        return interview;
    }

    private RecruitOffer requireOffer(Long id) {
        RecruitOffer offer = offerMapper.selectById(id);
        if (offer == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Offer 不存在");
        }
        return offer;
    }

    private void requireDept(Long deptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ErrorCode.DEMAND_DEPT_INVALID);
        }
    }

    private void requirePosition(Long positionId) {
        Position position = positionMapper.selectById(positionId);
        if (position == null) {
            throw new BizException(ErrorCode.DEMAND_POSITION_INVALID);
        }
        if (position.getStatus() != null && position.getStatus() == 0) {
            throw new BizException(ErrorCode.DEMAND_POSITION_INVALID);
        }
    }

    private List<Long> parseIds(String ids) {
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
    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
