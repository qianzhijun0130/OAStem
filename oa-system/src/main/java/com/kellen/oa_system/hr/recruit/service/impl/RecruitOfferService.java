package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.hr.recruit.dto.RecruitConvertRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitOfferSaveRequest;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitOffer;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitOfferMapper;
import com.kellen.oa_system.hr.recruit.support.RecruitConstants;
import com.kellen.oa_system.hr.recruit.support.RecruitOfferAssembler;
import com.kellen.oa_system.hr.recruit.support.RecruitSupport;
import com.kellen.oa_system.hr.recruit.vo.RecruitOfferVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Offer 与「转员工」。
 *
 * <p>状态机：待审批 → 已发出 → 已接受 / 已拒绝 / 已过期。每一步只认前一个状态，
 * 跳步一律 {@code OFFER_STATUS_INVALID}。</p>
 *
 * <p><b>转员工是这条流水线唯一的出口</b>，也是数据风险最高的一步：
 * 按手机号查重，命中已有档案时必须显式选择「复用」或「新建」，
 * 并把选择与操作人写进 {@code convert_log}——一人两条档案会让薪资串数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitOfferService {

    private final RecruitOfferMapper offerMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserServiceImpl userService;
    private final RecruitSupport support;
    private final RecruitOfferAssembler assembler;

    public PageResult<RecruitOfferVO> pageOffers(Integer status, Long candidateId,
                                                  Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitOffer> wrapper = new LambdaQueryWrapper<RecruitOffer>()
                .orderByDesc(RecruitOffer::getId);
        wrapper.eq(status != null, RecruitOffer::getStatus, status);
        wrapper.eq(candidateId != null, RecruitOffer::getCandidateId, candidateId);
        Page<RecruitOffer> page = offerMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public RecruitOfferVO offerDetail(Long id) {
        RecruitOffer offer = support.requireOffer(id);
        return assembler.toVOs(List.of(offer)).get(0);
    }

    public Long saveOffer(RecruitOfferSaveRequest request) {
        RecruitCandidate candidate = support.requireCandidate(request.getCandidateId());
        // 候选人必须到终试阶段才能发 offer
        if (candidate.getStage() == null || candidate.getStage() < RecruitConstants.STAGE_FINAL) {
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
                ? LocalDate.now().plusDays(RecruitConstants.OFFER_VALID_DAYS) : request.getExpireDate());
        entity.setStatus(RecruitConstants.OFFER_PENDING);
        entity.setSendWay(request.getSendWay());
        entity.setRemark(request.getRemark());
        entity.setOfferNo(nextOfferNo());
        offerMapper.insert(entity);
        log.info("Offer 发起: id={} no={} 候选人={}", entity.getId(), entity.getOfferNo(),
                request.getCandidateId());
        return entity.getId();
    }

    public void sendOffer(Long id, Integer sendWay) {
        RecruitOffer offer = support.requireOffer(id);
        if (!Objects.equals(offer.getStatus(), RecruitConstants.OFFER_PENDING)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID);
        }
        RecruitOffer patch = new RecruitOffer();
        patch.setId(id);
        patch.setStatus(RecruitConstants.OFFER_SENT);
        patch.setSendWay(sendWay == null ? 1 : sendWay);
        patch.setSendTime(LocalDateTime.now());
        offerMapper.updateById(patch);
        // 候选人阶段推进到「已发offer」
        RecruitCandidate cp = new RecruitCandidate();
        cp.setId(offer.getCandidateId());
        cp.setStage(RecruitConstants.STAGE_OFFER);
        candidateMapper.updateById(cp);
    }

    public void acceptOffer(Long id) {
        RecruitOffer offer = support.requireOffer(id);
        if (!Objects.equals(offer.getStatus(), RecruitConstants.OFFER_SENT)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID);
        }
        if (support.isExpired(offer)) {
            throw new BizException(ErrorCode.OFFER_EXPIRED);
        }
        RecruitOffer patch = new RecruitOffer();
        patch.setId(id);
        patch.setStatus(RecruitConstants.OFFER_ACCEPTED);
        patch.setAcceptTime(LocalDateTime.now());
        offerMapper.updateById(patch);
    }

    public void refuseOffer(Long id, String reason) {
        RecruitOffer offer = support.requireOffer(id);
        if (!Objects.equals(offer.getStatus(), RecruitConstants.OFFER_SENT)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID);
        }
        RecruitOffer patch = new RecruitOffer();
        patch.setId(id);
        patch.setStatus(RecruitConstants.OFFER_REFUSED);
        patch.setRefuseReason(reason);
        offerMapper.updateById(patch);
    }

    public Long convertToEmployee(RecruitConvertRequest request) {
        RecruitOffer offer = support.requireOffer(request.getOfferId());
        // 过期 offer 不能转员工：候选人可能已经接受了别家
        if (support.isExpired(offer)) {
            RecruitOffer expirePatch = new RecruitOffer();
            expirePatch.setId(offer.getId());
            expirePatch.setStatus(RecruitConstants.OFFER_EXPIRED);
            offerMapper.updateById(expirePatch);
            throw new BizException(ErrorCode.OFFER_EXPIRED);
        }
        if (!Objects.equals(offer.getStatus(), RecruitConstants.OFFER_ACCEPTED)) {
            throw new BizException(ErrorCode.OFFER_STATUS_INVALID, "只有已接受的 offer 才能转员工");
        }
        RecruitCandidate candidate = support.requireCandidate(offer.getCandidateId());

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
        cp.setStage(RecruitConstants.STAGE_HIRED);
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
}
