package com.kellen.oa_system.hr.leave.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.hr.common.enums.LeaveTypeEnum;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.leave.dto.LeavePageQuery;
import com.kellen.oa_system.hr.leave.dto.LeaveSubmitRequest;
import com.kellen.oa_system.hr.leave.entity.Leave;
import com.kellen.oa_system.hr.leave.entity.LeavePolicy;
import com.kellen.oa_system.hr.leave.mapper.LeaveMapper;
import com.kellen.oa_system.hr.leave.mapper.LeavePolicyMapper;
import com.kellen.oa_system.hr.leave.service.LeaveQuotaService;
import com.kellen.oa_system.hr.leave.service.LeaveService;
import com.kellen.oa_system.hr.leave.vo.LeaveVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.ApprovalService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请假业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    /** 请假流程编码：与 oa_flow_definition.flow_code 对应 */
    public static final String FLOW_CODE = "HR_LEAVE";

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_APPROVING = 1;
    private static final int STATUS_PASSED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_CANCELLED = 4;

    /** 销假状态：0 未销假 / 2 已销假（1「审批中」保留给后续接审批流时用） */
    private static final int STATUS_CANCEL_DONE = 2;

    /** 需要额度校验的假别：年假、调休 */
    private static final Set<Integer> QUOTA_TYPES = Set.of(3, 8);
    /**
     * 按地方政策限制天数的假别：婚假、产假、陪产假、丧假。
     *
     * <p>不再本地枚举，改用 {@link LeaveTypeEnum#policyControlledCodes()}：
     * 「哪些假别受地方政策限制」这个属性已经长在枚举上（地方政策配置页也用同一份），
     * 两处各写一份的结果是新增一个法定假别时，政策页能配但校验不认。
     * 注：本集合与 {@link #QUOTA_TYPES} 是两套不同规则（额度 vs 法定天数），不要合并。</p>
     */
    private static final Set<Integer> POLICY_TYPES = LeaveTypeEnum.policyControlledCodes();

    /** 假别中文名：统一取自 {@link LeaveTypeEnum}，不再本地维护 */
    private static final Map<Integer, String> TYPE_LABEL = LeaveTypeEnum.labelMap();

    private static final Map<Integer, String> STATUS_LABEL = Map.of(
            0, "草稿", 1, "审批中", 2, "已通过", 3, "已驳回", 4, "已撤销");

    /** 请假导出列（与列表字段对齐；不含附件与审批实例等技术字段） */
    private static final List<String> EXPORT_HEADERS = List.of(
            "单号", "姓名", "工号", "部门", "假别", "开始时间", "结束时间",
            "请假天数", "实际天数", "状态", "返岗日期", "事由");

    private final LeaveMapper leaveMapper;
    private final LeavePolicyMapper policyMapper;
    private final LeaveQuotaService quotaService;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalService approvalService;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final DataScopeSupport dataScopeSupport;
    private final ObjectMapper objectMapper;
    private final AttendanceService attendanceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(LeaveSubmitRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        BigDecimal days = resolveDays(request);
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();

        if (!end.isAfter(start)) {
            throw new BizException(ErrorCode.HR_LEAVE_DAYS_INVALID);
        }
        // 病假 ≥3 天必须带诊断证明：这条规则后端必须自己判，表单规则只是体验层
        if (Objects.equals(request.getLeaveType(), 2) && days.compareTo(BigDecimal.valueOf(3)) >= 0
                && !StringUtils.hasText(request.getAttachmentIds())) {
            throw new BizException(ErrorCode.HR_LEAVE_ATTACHMENT_REQUIRED);
        }
        assertNoOverlap(userId, start, end);
        assertPolicyDays(request.getLeaveType(), days);

        // 先确认流程已发布：否则会在插了业务单之后才失败，留下一堆「审批中」的孤儿单
        FlowDefinition flow = flowDefinitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getFlowCode, FLOW_CODE)
                .eq(FlowDefinition::getStatus, 1)
                .eq(FlowDefinition::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (flow == null) {
            throw new BizException(ErrorCode.HR_FLOW_NOT_DEPLOYED);
        }

        int year = start.getYear();
        if (QUOTA_TYPES.contains(request.getLeaveType())) {
            if (!quotaService.lock(userId, request.getLeaveType(), year, days)) {
                throw new BizException(ErrorCode.HR_LEAVE_QUOTA_NOT_ENOUGH,
                        "「" + TYPE_LABEL.get(request.getLeaveType()) + "」可用额度不足，需 "
                                + days.stripTrailingZeros().toPlainString() + " 天");
            }
        }

        Leave leave = new Leave();
        leave.setUserId(userId);
        leave.setLeaveType(request.getLeaveType());
        leave.setStartTime(start);
        leave.setEndTime(end);
        leave.setLeaveDays(days);
        leave.setReason(request.getReason());
        leave.setAttachmentIds(request.getAttachmentIds());
        leave.setLeaveStatus(STATUS_APPROVING);
        leave.setCancelStatus(0);
        leaveMapper.insert(leave);
        // 与审批单同风格的单据编号：保存时生成，全程不变
        leave.setBillCode(FLOW_CODE + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%04d", leave.getId()));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("leaveId", leave.getId());
        formData.put("leaveType", request.getLeaveType());
        formData.put("leaveTypeLabel", TYPE_LABEL.get(request.getLeaveType()));
        formData.put("startTime", start.toString());
        formData.put("endTime", end.toString());
        formData.put("leaveDays", days);
        formData.put("reason", request.getReason());

        ApprovalStartRequest startReq = new ApprovalStartRequest();
        startReq.setFlowCode(FLOW_CODE);
        startReq.setTitle(TYPE_LABEL.get(request.getLeaveType()) + " - "
                + SecurityContextUtil.getCurrentUser().getUsername()
                + " - " + days.stripTrailingZeros().toPlainString() + " 天");
        startReq.setFormData(objectMapper.writeValueAsString(formData));
        Long instanceId = approvalService.start(startReq);

        leave.setInstanceId(instanceId);
        leaveMapper.updateById(leave);
        log.info("请假已提交: leaveId={} instanceId={} days={}", leave.getId(), instanceId, days);
        return leave.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long leaveId) {
        Leave leave = requireLeave(leaveId);
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (!Objects.equals(leave.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤销本人的请假单");
        }
        if (!Objects.equals(leave.getLeaveStatus(), STATUS_APPROVING)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅审批中的请假单可以撤销");
        }
        if (leave.getInstanceId() != null) {
            // 走引擎撤销：回调里统一改业务状态并释放额度，避免两处各释放一次
            approvalService.withdraw(leave.getInstanceId());
            return;
        }
        // 没有关联网实例（历史数据）时直接收尾
        leave.setLeaveStatus(STATUS_CANCELLED);
        leaveMapper.updateById(leave);
        releaseQuota(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnFromLeave(Long leaveId, LocalDate returnDate, String remark) {
        Leave leave = requireLeave(leaveId);
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        boolean self = Objects.equals(leave.getUserId(), currentUserId);
        if (!self && !HrRoles.isHr()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能销本人的请假单");
        }
        if (!Objects.equals(leave.getLeaveStatus(), STATUS_PASSED)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅已通过的请假单可以销假");
        }
        // 一对一、只允许一次：重复销假会让额度退两次
        if (leave.getCancelStatus() != null && leave.getCancelStatus() != 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该请假单已销假，不能重复操作");
        }
        LocalDate startDate = leave.getStartTime().toLocalDate();
        LocalDate endDate = leave.getEndTime().toLocalDate();
        if (returnDate == null || !returnDate.isAfter(startDate) || returnDate.isAfter(endDate.plusDays(1))) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "返岗日期须晚于请假开始日 " + startDate + " 且不晚于 " + endDate.plusDays(1));
        }
        // 实际休假天数：开始日到返岗日前一天
        BigDecimal actual = BigDecimal.valueOf(
                Duration.between(startDate.atStartOfDay(), returnDate.atStartOfDay()).toDays());
        if (actual.compareTo(leave.getLeaveDays()) > 0) {
            actual = leave.getLeaveDays();
        }
        BigDecimal refund = leave.getLeaveDays().subtract(actual);

        leave.setActualDays(actual);
        leave.setReturnDate(returnDate);
        leave.setCancelStatus(STATUS_CANCEL_DONE);
        leaveMapper.updateById(leave);

        if (QUOTA_TYPES.contains(leave.getLeaveType()) && refund.compareTo(BigDecimal.ZERO) > 0) {
            // 已通过时扣的是 used_days，销假退回的也是 used_days（不是 locked）
            quotaService.refund(leave.getUserId(), leave.getLeaveType(),
                    leave.getStartTime().getYear(), refund);
        }
        // 提前返岗后，返岗日之后原本被标成「假勤」的考勤记录必须作废重算，
        // 否则月汇总里那个人会同时有「休假天数」与「已返岗」两份数据
        attendanceService.resettleLeaveRange(leave.getUserId(), returnDate, endDate);
        log.info("销假完成: leaveId={} 原天数={} 实际={} 退回={} 备注={}",
                leave.getId(), leave.getLeaveDays(), actual, refund, remark);
    }

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public PageResult<LeaveVO> page(LeavePageQuery query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<Leave> wrapper = buildQueryWrapper(query);
        if (wrapper == null) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<Leave> page = leaveMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 组装列表与导出共用的查询条件。
     *
     * @return {@code null} 表示「过滤结果必然为空」（如数据范围为空、部门下无员工），
     *         让调用方直接短路，避免为一次空结果还去查库
     */
    private LambdaQueryWrapper<Leave> buildQueryWrapper(LeavePageQuery query) {
        LambdaQueryWrapper<Leave> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getMonth())) {
            YearMonth ym = parseMonth(query.getMonth());
            wrapper.between(Leave::getStartTime, ym.atDay(1).atStartOfDay(),
                    ym.atEndOfMonth().atTime(23, 59, 59));
        }
        if (query.getLeaveType() != null) {
            wrapper.eq(Leave::getLeaveType, query.getLeaveType());
        }
        if (query.getLeaveStatus() != null) {
            wrapper.eq(Leave::getLeaveStatus, query.getLeaveStatus());
        }
        if (query.getUserId() != null) {
            wrapper.eq(Leave::getUserId, query.getUserId());
        }
        List<Long> visible = visibleUserIds();
        if (visible != null) {
            if (visible.isEmpty()) {
                return null;
            }
            wrapper.in(Leave::getUserId, visible);
        }
        if (query.getDeptId() != null) {
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .in(User::getDeptId, hrDataScopeSupport.subtreeIds(query.getDeptId())))
                    .stream().map(User::getId).toList();
            if (ids.isEmpty()) {
                return null;
            }
            wrapper.in(Leave::getUserId, ids);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .and(w -> w.like(User::getRealName, kw).or().like(User::getWorkNumber, kw)))
                    .stream().map(User::getId).toList();
            if (ids.isEmpty()) {
                return null;
            }
            wrapper.in(Leave::getUserId, ids);
        }
        wrapper.orderByDesc(Leave::getStartTime).orderByDesc(Leave::getId);
        return wrapper;
    }

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public void export(LeavePageQuery query, jakarta.servlet.http.HttpServletResponse response) {
        LambdaQueryWrapper<Leave> wrapper = buildQueryWrapper(query);
        List<LeaveVO> rows;
        if (wrapper == null) {
            rows = List.of();
        } else {
            // 导出上限，避免一次性把整年数据读进内存；按筛选条件缩小范围更稳妥
            wrapper.last("LIMIT 5000");
            rows = toVOs(leaveMapper.selectList(wrapper));
        }
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (LeaveVO vo : rows) {
            data.add(List.of(
                    nvl(vo.getBillCode()),
                    nvl(vo.getRealName()),
                    nvl(vo.getWorkNumber()),
                    nvl(vo.getDeptName()),
                    nvl(vo.getLeaveTypeLabel()),
                    vo.getStartTime() == null ? "" : vo.getStartTime().toString().replace('T', ' '),
                    vo.getEndTime() == null ? "" : vo.getEndTime().toString().replace('T', ' '),
                    vo.getLeaveDays() == null ? "" : vo.getLeaveDays().toPlainString(),
                    vo.getActualDays() == null ? "" : vo.getActualDays().toPlainString(),
                    nvl(vo.getLeaveStatusLabel()),
                    vo.getReturnDate() == null ? "" : vo.getReturnDate().toString(),
                    nvl(vo.getReason())));
        }
        ExcelExportUtil.write(response, "请假记录.xlsx", "请假记录", EXPORT_HEADERS, data);
    }

    @Transactional(readOnly = true)
    @Override
    public List<LeaveVO> mine(Integer leaveStatus) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<Leave> wrapper = new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .orderByDesc(Leave::getStartTime)
                .orderByDesc(Leave::getId);
        if (leaveStatus != null) {
            wrapper.eq(Leave::getLeaveStatus, leaveStatus);
        }
        return toVOs(leaveMapper.selectList(wrapper));
    }

    @Transactional(readOnly = true)
    @Override
    public LeaveVO detail(Long leaveId) {
        Leave leave = requireLeave(leaveId);
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        User target = userMapper.selectById(leave.getUserId());
        boolean allowed = Objects.equals(leave.getUserId(), currentUserId)
                || (target != null && Objects.equals(target.getDirectLeaderId(), currentUserId))
                || SecurityContextUtil.getCurrentUser().getRoles().stream()
                        .anyMatch(r -> Set.of("SUPER_ADMIN", "HR", "HR_MANAGER").contains(r));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该请假单");
        }
        return toVOs(List.of(leave)).get(0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private BigDecimal resolveDays(LeaveSubmitRequest request) {
        if (request.getLeaveDays() != null) {
            return request.getLeaveDays().setScale(1, RoundingMode.HALF_UP);
        }
        long minutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        BigDecimal days = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(24 * 60), 1, RoundingMode.HALF_UP);
        // 不足半天按半天计（系统最小粒度 0.5 天）
        return days.compareTo(BigDecimal.valueOf(0.5)) < 0 ? BigDecimal.valueOf(0.5) : days;
    }

    /** 同一个人不允许时间重叠的请假（含跨假别），提示里带出冲突单据编号 */
    private void assertNoOverlap(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Leave> existing = leaveMapper.selectList(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getUserId, userId)
                .in(Leave::getLeaveStatus, List.of(STATUS_DRAFT, STATUS_APPROVING, STATUS_PASSED)));
        for (Leave other : existing) {
            boolean overlap = start.isBefore(other.getEndTime()) && other.getStartTime().isBefore(end);
            if (overlap) {
                throw new BizException(ErrorCode.HR_LEAVE_TIME_OVERLAP,
                        "与单据 " + (other.getBillCode() == null ? "#" + other.getId() : other.getBillCode())
                                + " 时间重叠");
            }
        }
    }

    /** 法定假别天数上限：读政策表，缺城市配置时回退「默认」 */
    private void assertPolicyDays(Integer leaveType, BigDecimal days) {
        if (!POLICY_TYPES.contains(leaveType)) {
            return;
        }
        int year = LocalDate.now().getYear();
        LeavePolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<LeavePolicy>()
                .eq(LeavePolicy::getCity, "默认")
                .eq(LeavePolicy::getLeaveType, leaveType)
                .eq(LeavePolicy::getEffectiveYear, year)
                .last("LIMIT 1"));
        if (policy == null || policy.getDays() == null) {
            return;
        }
        if (days.compareTo(policy.getDays()) > 0) {
            throw new BizException(ErrorCode.HR_LEAVE_POLICY_EXCEEDED,
                    "「" + TYPE_LABEL.get(leaveType) + "」上限 " + policy.getDays().stripTrailingZeros().toPlainString() + " 天");
        }
    }

    /** 释放占用额度（驳回 / 撤销 / 终止） */
    public void releaseQuota(Leave leave) {
        if (leave == null || !QUOTA_TYPES.contains(leave.getLeaveType()) || leave.getLeaveDays() == null) {
            return;
        }
        quotaService.release(leave.getUserId(), leave.getLeaveType(),
                leave.getStartTime().getYear(), leave.getLeaveDays());
    }

    private Leave requireLeave(Long leaveId) {
        Leave leave = leaveMapper.selectById(leaveId);
        if (leave == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请假单不存在或已删除");
        }
        return leave;
    }

    /** 数据范围 → 用户ID；null 表示不受限 */
    private List<Long> visibleUserIds() {
        // 快照为空 = 调用方漏标 @DataScope。返回 null 会被上层理解为"不受限"，
        // 造成「列表受限、导出不限」这类静默越权（BE-OPT-02 根因），故直接失败。
        if (dataScopeSupport.currentSnapshot() == null) {
            throw new IllegalStateException(
                    "调用方未标 @DataScope，数据权限无法判定：" + getClass().getSimpleName() + "#visibleUserIds");
        }
        if (hrDataScopeSupport.isUnrestricted()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        hrDataScopeSupport.applyToUser(wrapper);
        return userMapper.selectList(wrapper.select(User::getId)).stream().map(User::getId).toList();
    }

    private List<LeaveVO> toVOs(List<Leave> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(list.stream()
                        .map(Leave::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Long currentUserId = SecurityContextUtil.getCurrentUserId();

        List<LeaveVO> vos = new ArrayList<>(list.size());
        for (Leave l : list) {
            LeaveVO vo = new LeaveVO();
            vo.setId(l.getId());
            vo.setBillCode(l.getBillCode());
            vo.setUserId(l.getUserId());
            User u = users.get(l.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptId(u.getDeptId());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
            vo.setLeaveType(l.getLeaveType());
            vo.setLeaveTypeLabel(TYPE_LABEL.getOrDefault(l.getLeaveType(), "其他"));
            vo.setStartTime(l.getStartTime());
            vo.setEndTime(l.getEndTime());
            vo.setLeaveDays(l.getLeaveDays());
            vo.setActualDays(l.getActualDays());
            vo.setReturnDate(l.getReturnDate());
            vo.setReason(l.getReason());
            vo.setAttachmentIds(l.getAttachmentIds());
            vo.setInstanceId(l.getInstanceId());
            vo.setLeaveStatus(l.getLeaveStatus());
            vo.setLeaveStatusLabel(STATUS_LABEL.getOrDefault(l.getLeaveStatus(), "未知"));
            vo.setCancelStatus(l.getCancelStatus());
            vo.setCanCancel(Objects.equals(l.getLeaveStatus(), STATUS_APPROVING)
                    && Objects.equals(l.getUserId(), currentUserId));
            vo.setCreateTime(l.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份格式应为 yyyy-MM");
        }
    }

    /** 导出用：null 转空串，避免 Excel 单元格出现 "null" */

    private static String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
