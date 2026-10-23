package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.attendance.dto.HrRequestSaveRequest;
import com.kellen.oa_system.hr.attendance.entity.BusinessTrip;
import com.kellen.oa_system.hr.attendance.entity.CardSupplement;
import com.kellen.oa_system.hr.attendance.entity.Overtime;
import com.kellen.oa_system.hr.attendance.mapper.BusinessTripMapper;
import com.kellen.oa_system.hr.attendance.mapper.CardSupplementMapper;
import com.kellen.oa_system.hr.attendance.mapper.OvertimeMapper;
import com.kellen.oa_system.hr.attendance.service.HrRequestService;
import com.kellen.oa_system.hr.attendance.vo.HrRequestVO;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.leave.entity.Leave;
import com.kellen.oa_system.hr.leave.mapper.LeaveMapper;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 加班 / 出差 / 补卡实现。
 *
 * <p>与请假一致：提交时先确认流程已发布，再落台账、起审批；三者都需要业务回写，
 * 各自的 {@code ApprovalCallback} 在审批终态改状态（加班转调休在回调里加额度）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrRequestServiceImpl implements HrRequestService {

    private static final String FLOW_OVERTIME = "HR_OVERTIME";
    private static final String FLOW_TRIP = "HR_TRIP";
    private static final String FLOW_CARD = "HR_CARD";

    private static final int STATUS_APPROVING = 1;

    /** 单月加班上限（劳动法第 41 条） */
    private static final BigDecimal MAX_OVERTIME_HOURS = BigDecimal.valueOf(36);
    /** 补卡可回溯天数 */
    private static final int CARD_LOOKBACK_DAYS = 30;
    /** 每月补卡上限 */
    private static final int CARD_MONTH_LIMIT = 3;

    private static final Map<Integer, String> STATUS_LABEL = Map.of(
            0, "草稿", 1, "审批中", 2, "已通过", 3, "已驳回", 4, "已撤销");

    private final OvertimeMapper overtimeMapper;
    private final BusinessTripMapper tripMapper;
    private final CardSupplementMapper cardMapper;
    private final LeaveMapper leaveMapper;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalService approvalService;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(HrRequestSaveRequest request) {
        Integer bizType = request.getBizType();
        if (bizType == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "业务类型不能为空");
        }
        return switch (bizType) {
            case 1 -> submitOvertime(request);
            case 2 -> submitTrip(request);
            case 3 -> submitCard(request);
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的业务类型");
        };
    }

    // ---------------- 加班 ----------------

    private Long submitOvertime(HrRequestSaveRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (request.getOvertimeDate() == null || request.getHours() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "加班日期与时长不能为空");
        }
        requireFlow(FLOW_OVERTIME);

        // 单月累计（含审批中）不得超过 36 小时：超了要在提交时拦住，而不是等 HR 事后发现
        YearMonth ym = YearMonth.from(request.getOvertimeDate());
        BigDecimal used = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                        .eq(Overtime::getUserId, userId)
                        .between(Overtime::getOvertimeDate, ym.atDay(1), ym.atEndOfMonth())
                        .in(Overtime::getOvertimeStatus, List.of(STATUS_APPROVING, 2)))
                .stream().map(o -> Objects.requireNonNullElse(o.getHours(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (used.add(request.getHours()).compareTo(MAX_OVERTIME_HOURS) > 0) {
            throw new BizException(ErrorCode.HR_OVERTIME_EXCEED_LIMIT,
                    "本月已加班 " + used.stripTrailingZeros().toPlainString() + " 小时，本次 "
                            + request.getHours().stripTrailingZeros().toPlainString() + " 小时将超过 36 小时上限");
        }

        Overtime entity = new Overtime();
        entity.setUserId(userId);
        entity.setOvertimeDate(request.getOvertimeDate());
        entity.setOvertimeType(Objects.requireNonNullElse(request.getOvertimeType(), 1));
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setHours(request.getHours());
        entity.setCompensateType(Objects.requireNonNullElse(request.getCompensateType(), 1));
        entity.setReason(request.getReason());
        entity.setOvertimeStatus(STATUS_APPROVING);
        overtimeMapper.insert(entity);
        entity.setBillCode(billCode(FLOW_OVERTIME, entity.getId()));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("overtimeId", entity.getId());
        form.put("overtimeDate", request.getOvertimeDate().toString());
        form.put("hours", request.getHours());
        form.put("compensateType", entity.getCompensateType());
        form.put("reason", request.getReason());
        entity.setInstanceId(startFlow(FLOW_OVERTIME, "加班申请 - "
                + request.getOvertimeDate() + " - " + request.getHours().stripTrailingZeros().toPlainString() + " 小时", form));
        overtimeMapper.updateById(entity);
        return entity.getId();
    }

    // ---------------- 出差 ----------------

    private Long submitTrip(HrRequestSaveRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (!StringUtils.hasText(request.getDestination())
                || request.getStartDate() == null || request.getEndDate() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "出差地点与起止日期不能为空");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "出差结束日期不能早于开始日期");
        }
        // 与已有请假冲突（语义冲突，直接拒绝）；出差与加班允许并存
        boolean conflict = leaveMapper.selectCount(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .in(Leave::getLeaveStatus, List.of(STATUS_APPROVING, 2))
                .le(Leave::getStartTime, request.getEndDate().atTime(23, 59, 59))
                .ge(Leave::getEndTime, request.getStartDate().atStartOfDay())) > 0;
        if (conflict) {
            throw new BizException(ErrorCode.HR_TRIP_LEAVE_CONFLICT);
        }
        requireFlow(FLOW_TRIP);

        BusinessTrip entity = new BusinessTrip();
        entity.setUserId(userId);
        entity.setDestination(request.getDestination());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setDays(BigDecimal.valueOf(ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1));
        entity.setBudget(request.getBudget());
        entity.setReason(request.getReason());
        entity.setTripStatus(STATUS_APPROVING);
        tripMapper.insert(entity);
        entity.setBillCode(billCode(FLOW_TRIP, entity.getId()));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("tripId", entity.getId());
        form.put("destination", entity.getDestination());
        form.put("startDate", entity.getStartDate().toString());
        form.put("endDate", entity.getEndDate().toString());
        form.put("days", entity.getDays());
        form.put("budget", entity.getBudget());
        entity.setInstanceId(startFlow(FLOW_TRIP,
                "出差申请 - " + entity.getDestination() + " - " + entity.getDays().stripTrailingZeros().toPlainString() + " 天", form));
        tripMapper.updateById(entity);
        return entity.getId();
    }

    // ---------------- 补卡 ----------------

    private Long submitCard(HrRequestSaveRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (request.getCardDate() == null || request.getDirection() == null || request.getCardTime() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "补卡日期、方向与时间不能为空");
        }
        // 只能补近 30 天，且不能补未来
        LocalDate today = LocalDate.now();
        if (request.getCardDate().isAfter(today)
                || request.getCardDate().isBefore(today.minusDays(CARD_LOOKBACK_DAYS))) {
            throw new BizException(ErrorCode.HR_CARD_SUPPLEMENT_EXPIRED);
        }
        Long sameDay = cardMapper.selectCount(new LambdaQueryWrapper<CardSupplement>()
                .eq(CardSupplement::getUserId, userId)
                .eq(CardSupplement::getCardDate, request.getCardDate())
                .eq(CardSupplement::getDirection, request.getDirection())
                .in(CardSupplement::getCardStatus, List.of(STATUS_APPROVING, 2)));
        if (sameDay != null && sameDay > 0) {
            throw new BizException(ErrorCode.HR_CARD_ALREADY_EXISTS);
        }
        YearMonth ym = YearMonth.from(request.getCardDate());
        Long monthCount = cardMapper.selectCount(new LambdaQueryWrapper<CardSupplement>()
                .eq(CardSupplement::getUserId, userId)
                .between(CardSupplement::getCardDate, ym.atDay(1), ym.atEndOfMonth())
                .in(CardSupplement::getCardStatus, List.of(STATUS_APPROVING, 2)));
        if (monthCount != null && monthCount >= CARD_MONTH_LIMIT) {
            throw new BizException(ErrorCode.HR_CARD_SUPPLEMENT_LIMIT,
                    "本月已补卡 " + monthCount + " 次，上限 " + CARD_MONTH_LIMIT + " 次");
        }
        requireFlow(FLOW_CARD);

        CardSupplement entity = new CardSupplement();
        entity.setUserId(userId);
        entity.setCardDate(request.getCardDate());
        entity.setDirection(request.getDirection());
        entity.setCardTime(request.getCardTime());
        entity.setReason(request.getReason());
        entity.setCardStatus(STATUS_APPROVING);
        cardMapper.insert(entity);
        entity.setBillCode(billCode(FLOW_CARD, entity.getId()));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("cardId", entity.getId());
        form.put("cardDate", entity.getCardDate().toString());
        form.put("direction", entity.getDirection());
        form.put("cardTime", entity.getCardTime().toString());
        entity.setInstanceId(startFlow(FLOW_CARD, "补卡申请 - " + entity.getCardDate()
                + " - " + (entity.getDirection() == 1 ? "上班" : "下班"), form));
        cardMapper.updateById(entity);
        return entity.getId();
    }

    // ---------------- 撤销 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Integer bizType, Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        Long instanceId;
        switch (Objects.requireNonNullElse(bizType, 0)) {
            case 1 -> {
                Overtime o = overtimeMapper.selectById(id);
                assertOwnAndApproving(o == null ? null : o.getUserId(), o == null ? null : o.getOvertimeStatus());
                instanceId = o.getInstanceId();
                if (instanceId == null) {
                    patchOvertimeStatus(id, 4);
                    return;
                }
            }
            case 2 -> {
                BusinessTrip t = tripMapper.selectById(id);
                assertOwnAndApproving(t == null ? null : t.getUserId(), t == null ? null : t.getTripStatus());
                instanceId = t.getInstanceId();
                if (instanceId == null) {
                    patchTripStatus(id, 4);
                    return;
                }
            }
            case 3 -> {
                CardSupplement c = cardMapper.selectById(id);
                assertOwnAndApproving(c == null ? null : c.getUserId(), c == null ? null : c.getCardStatus());
                instanceId = c.getInstanceId();
                if (instanceId == null) {
                    patchCardStatus(id, 4);
                    return;
                }
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的业务类型");
        }
        // 走引擎撤销，状态由各回调统一回写，避免两处各改一次
        approvalService.withdraw(instanceId);
        log.info("假勤申请撤销: bizType={} id={}", bizType, id);
    }

    // ---------------- 查询 ----------------

    @Override
    @DataScope
    public PageResult<HrRequestVO> page(Integer bizType, String month, Long deptId, Integer status,
                                        String keyword, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        List<Long> visible = visibleUserIds();
        List<Long> deptUsers = null;
        if (deptId != null) {
            deptUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .in(User::getDeptId, hrDataScopeSupport.subtreeIds(deptId)))
                    .stream().map(User::getId).toList();
        }
        List<Long> keywordUsers = null;
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            keywordUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .and(w -> w.like(User::getRealName, kw).or().like(User::getWorkNumber, kw)))
                    .stream().map(User::getId).toList();
        }

        switch (Objects.requireNonNullElse(bizType, 0)) {
            case 1 -> {
                LambdaQueryWrapper<Overtime> w = new LambdaQueryWrapper<>();
                applyMonth(w, month);
                if (status != null) {
                    w.eq(Overtime::getOvertimeStatus, status);
                }
                w.orderByDesc(Overtime::getOvertimeDate).orderByDesc(Overtime::getId);
                applyUserFilter(w, Overtime::getUserId, visible, deptUsers, keywordUsers);
                if ((visible != null && visible.isEmpty()) || (deptUsers != null && deptUsers.isEmpty())
                        || (keywordUsers != null && keywordUsers.isEmpty())) {
                    return PageResult.empty((long) no, (long) size);
                }
                Page<Overtime> page = overtimeMapper.selectPage(new Page<>(no, size), w);
                List<HrRequestVO> vos = page.getRecords().stream().map(this::toOvertimeVO).collect(Collectors.toList());
                return PageResult.of(enrich(vos), page.getTotal(), page.getCurrent(), page.getSize());
            }
            case 2 -> {
                LambdaQueryWrapper<BusinessTrip> w = new LambdaQueryWrapper<>();
                if (StringUtils.hasText(month)) {
                    YearMonth ym = parseMonth(month);
                    w.le(BusinessTrip::getStartDate, ym.atEndOfMonth())
                            .ge(BusinessTrip::getEndDate, ym.atDay(1));
                }
                if (status != null) {
                    w.eq(BusinessTrip::getTripStatus, status);
                }
                w.orderByDesc(BusinessTrip::getStartDate).orderByDesc(BusinessTrip::getId);
                applyUserFilter(w, BusinessTrip::getUserId, visible, deptUsers, keywordUsers);
                if ((visible != null && visible.isEmpty()) || (deptUsers != null && deptUsers.isEmpty())
                        || (keywordUsers != null && keywordUsers.isEmpty())) {
                    return PageResult.empty((long) no, (long) size);
                }
                Page<BusinessTrip> page = tripMapper.selectPage(new Page<>(no, size), w);
                List<HrRequestVO> vos = page.getRecords().stream().map(this::toTripVO).collect(Collectors.toList());
                return PageResult.of(enrich(vos), page.getTotal(), page.getCurrent(), page.getSize());
            }
            case 3 -> {
                LambdaQueryWrapper<CardSupplement> w = new LambdaQueryWrapper<>();
                if (StringUtils.hasText(month)) {
                    YearMonth ym = parseMonth(month);
                    w.between(CardSupplement::getCardDate, ym.atDay(1), ym.atEndOfMonth());
                }
                if (status != null) {
                    w.eq(CardSupplement::getCardStatus, status);
                }
                w.orderByDesc(CardSupplement::getCardDate).orderByDesc(CardSupplement::getId);
                applyUserFilter(w, CardSupplement::getUserId, visible, deptUsers, keywordUsers);
                if ((visible != null && visible.isEmpty()) || (deptUsers != null && deptUsers.isEmpty())
                        || (keywordUsers != null && keywordUsers.isEmpty())) {
                    return PageResult.empty((long) no, (long) size);
                }
                Page<CardSupplement> page = cardMapper.selectPage(new Page<>(no, size), w);
                List<HrRequestVO> vos = page.getRecords().stream().map(this::toCardVO).collect(Collectors.toList());
                return PageResult.of(enrich(vos), page.getTotal(), page.getCurrent(), page.getSize());
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的业务类型");
        }
    }

    @Override
    public List<HrRequestVO> mine(Integer bizType) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        switch (Objects.requireNonNullElse(bizType, 0)) {
            case 1 -> {
                return enrich(overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                                .eq(Overtime::getUserId, userId)
                                .orderByDesc(Overtime::getOvertimeDate))
                        .stream().map(this::toOvertimeVO).collect(Collectors.toList()));
            }
            case 2 -> {
                return enrich(tripMapper.selectList(new LambdaQueryWrapper<BusinessTrip>()
                                .eq(BusinessTrip::getUserId, userId)
                                .orderByDesc(BusinessTrip::getStartDate))
                        .stream().map(this::toTripVO).collect(Collectors.toList()));
            }
            case 3 -> {
                return enrich(cardMapper.selectList(new LambdaQueryWrapper<CardSupplement>()
                                .eq(CardSupplement::getUserId, userId)
                                .orderByDesc(CardSupplement::getCardDate))
                        .stream().map(this::toCardVO).collect(Collectors.toList()));
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的业务类型");
        }
    }

    // ---------------- 内部 ----------------

    private void applyMonth(LambdaQueryWrapper<Overtime> wrapper, String month) {
        if (!StringUtils.hasText(month)) {
            return;
        }
        YearMonth ym = parseMonth(month);
        wrapper.between(Overtime::getOvertimeDate, ym.atDay(1), ym.atEndOfMonth());
    }

    /**
     * 把「数据范围 / 部门 / 关键词」三组用户条件拼到同一列上。
     *
     * <p>三者都是对 {@code user_id} 做 IN，交集语义天然成立；空集合由调用方提前返回空页，
     * 不会走到 SQL（否则会拼出非法的 {@code IN ()}）。</p>
     */
    private <T> void applyUserFilter(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> userColumn,
                                     List<Long> visible, List<Long> deptUsers, List<Long> keywordUsers) {
        if (visible != null) {
            wrapper.in(userColumn, visible);
        }
        if (deptUsers != null) {
            wrapper.in(userColumn, deptUsers);
        }
        if (keywordUsers != null) {
            wrapper.in(userColumn, keywordUsers);
        }
    }

    private void requireFlow(String flowCode) {
        FlowDefinition def = flowDefinitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getFlowCode, flowCode)
                .eq(FlowDefinition::getStatus, 1)
                .last("LIMIT 1"));
        if (def == null) {
            throw new BizException(ErrorCode.HR_FLOW_NOT_DEPLOYED,
                    "流程 " + flowCode + " 未发布，请先在「流程管理 → 流程模板」发布");
        }
    }

    private Long startFlow(String flowCode, String title, Map<String, Object> formData) {
        ApprovalStartRequest req = new ApprovalStartRequest();
        req.setFlowCode(flowCode);
        req.setTitle(title.length() > 190 ? title.substring(0, 190) : title);
        req.setFormData(objectMapper.writeValueAsString(formData));
        return approvalService.start(req);
    }

    private String billCode(String flowCode, Long id) {
        return flowCode + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%04d", id);
    }

    private void assertOwnAndApproving(Long ownerId, Integer status) {
        if (ownerId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "申请不存在或已删除");
        }
        if (!Objects.equals(ownerId, SecurityContextUtil.getCurrentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤销本人的申请");
        }
        if (!Objects.equals(status, STATUS_APPROVING)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅审批中的申请可以撤销");
        }
    }

    private void patchOvertimeStatus(Long id, int status) {
        Overtime p = new Overtime();
        p.setId(id);
        p.setOvertimeStatus(status);
        overtimeMapper.updateById(p);
    }

    private void patchTripStatus(Long id, int status) {
        BusinessTrip p = new BusinessTrip();
        p.setId(id);
        p.setTripStatus(status);
        tripMapper.updateById(p);
    }

    private void patchCardStatus(Long id, int status) {
        CardSupplement p = new CardSupplement();
        p.setId(id);
        p.setCardStatus(status);
        cardMapper.updateById(p);
    }

    private HrRequestVO toOvertimeVO(Overtime o) {
        HrRequestVO vo = new HrRequestVO();
        vo.setId(o.getId());
        vo.setBillCode(o.getBillCode());
        vo.setBizType(1);
        vo.setBizTypeLabel("加班");
        vo.setUserId(o.getUserId());
        vo.setBizDate(o.getOvertimeDate());
        vo.setStartTime(o.getStartTime());
        vo.setEndTime(o.getEndTime());
        vo.setHours(o.getHours());
        vo.setOvertimeType(o.getOvertimeType());
        vo.setCompensateType(o.getCompensateType());
        vo.setReason(o.getReason());
        vo.setInstanceId(o.getInstanceId());
        vo.setStatus(o.getOvertimeStatus());
        vo.setStatusLabel(STATUS_LABEL.getOrDefault(o.getOvertimeStatus(), "未知"));
        vo.setCanCancel(Objects.equals(o.getOvertimeStatus(), STATUS_APPROVING)
                && Objects.equals(o.getUserId(), SecurityContextUtil.getCurrentUserId()));
        vo.setCreateTime(o.getCreateTime());
        return vo;
    }

    private HrRequestVO toTripVO(BusinessTrip t) {
        HrRequestVO vo = new HrRequestVO();
        vo.setId(t.getId());
        vo.setBillCode(t.getBillCode());
        vo.setBizType(2);
        vo.setBizTypeLabel("出差");
        vo.setUserId(t.getUserId());
        vo.setStartDate(t.getStartDate());
        vo.setEndDate(t.getEndDate());
        vo.setDestination(t.getDestination());
        vo.setDays(t.getDays());
        vo.setBudget(t.getBudget());
        vo.setReason(t.getReason());
        vo.setInstanceId(t.getInstanceId());
        vo.setStatus(t.getTripStatus());
        vo.setStatusLabel(STATUS_LABEL.getOrDefault(t.getTripStatus(), "未知"));
        vo.setCanCancel(Objects.equals(t.getTripStatus(), STATUS_APPROVING)
                && Objects.equals(t.getUserId(), SecurityContextUtil.getCurrentUserId()));
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }

    private HrRequestVO toCardVO(CardSupplement c) {
        HrRequestVO vo = new HrRequestVO();
        vo.setId(c.getId());
        vo.setBillCode(c.getBillCode());
        vo.setBizType(3);
        vo.setBizTypeLabel("补卡");
        vo.setUserId(c.getUserId());
        vo.setBizDate(c.getCardDate());
        vo.setStartTime(c.getCardTime());
        vo.setDirection(c.getDirection());
        vo.setReason(c.getReason());
        vo.setInstanceId(c.getInstanceId());
        vo.setStatus(c.getCardStatus());
        vo.setStatusLabel(STATUS_LABEL.getOrDefault(c.getCardStatus(), "未知"));
        vo.setCanCancel(Objects.equals(c.getCardStatus(), STATUS_APPROVING)
                && Objects.equals(c.getUserId(), SecurityContextUtil.getCurrentUserId()));
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    /** 补全姓名 / 工号 / 部门（一次批量查询，避免逐行关联） */
    private List<HrRequestVO> enrich(List<HrRequestVO> vos) {
        if (vos.isEmpty()) {
            return vos;
        }
        Map<Long, User> users = userMapper.selectBatchIds(vos.stream()
                        .map(HrRequestVO::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        for (HrRequestVO vo : vos) {
            User u = users.get(vo.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
        }
        return vos;
    }

    private List<Long> visibleUserIds() {
        if (hrDataScopeSupport.isUnrestricted()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        hrDataScopeSupport.applyToUser(wrapper);
        return userMapper.selectList(wrapper.select(User::getId)).stream().map(User::getId).toList();
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份格式应为 yyyy-MM");
        }
    }
}
