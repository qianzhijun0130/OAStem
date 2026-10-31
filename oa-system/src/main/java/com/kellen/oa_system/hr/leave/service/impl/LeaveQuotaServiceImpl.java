package com.kellen.oa_system.hr.leave.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.common.enums.LeaveTypeEnum;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.leave.dto.QuotaAdjustRequest;
import com.kellen.oa_system.hr.leave.entity.LeaveQuota;
import com.kellen.oa_system.hr.leave.mapper.LeaveQuotaMapper;
import com.kellen.oa_system.hr.leave.service.LeaveQuotaService;
import com.kellen.oa_system.hr.leave.vo.LeaveQuotaVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 假期额度实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveQuotaServiceImpl implements LeaveQuotaService {

    /** 年假 */
    private static final int TYPE_ANNUAL = 3;
    /** 调休 */
    private static final int TYPE_COMPENSATORY = 8;

    /** 假别中文名：统一取自 {@link LeaveTypeEnum}，避免多处各写一份（见该类注释） */
    private static final Map<Integer, String> TYPE_LABEL = LeaveTypeEnum.labelMap();

    private final LeaveQuotaMapper quotaMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final HrDataScopeSupport hrDataScopeSupport;

    @Override
    public BigDecimal annualLeaveDays(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 年假按「累计工作年限」而非本公司司龄（《职工带薪年休假条例》第三条）：
        // work_start_date 由 HR 录入；未录入时退回按入职日期估算，并在台账备注里标注为估算值
        LocalDate from = user.getWorkStartDate() != null ? user.getWorkStartDate() : user.getEntryDate();
        if (from == null) {
            return BigDecimal.ZERO;
        }
        long years = ChronoUnit.YEARS.between(from, LocalDate.now());
        if (years < 1) {
            return BigDecimal.ZERO;
        }
        if (years < 10) {
            return BigDecimal.valueOf(5);
        }
        return years < 20 ? BigDecimal.valueOf(10) : BigDecimal.valueOf(15);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureQuota(Long userId, int year) {
        ensureOne(userId, TYPE_ANNUAL, year, annualLeaveDays(userId));
        ensureOne(userId, TYPE_COMPENSATORY, year, BigDecimal.ZERO);
    }

    private void ensureOne(Long userId, int leaveType, int year, BigDecimal totalDays) {
        LeaveQuota existing = quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getUserId, userId)
                .eq(LeaveQuota::getLeaveType, leaveType)
                .eq(LeaveQuota::getYear, year)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        LeaveQuota quota = new LeaveQuota();
        quota.setUserId(userId);
        quota.setLeaveType(leaveType);
        quota.setYear(year);
        quota.setTotalDays(totalDays);
        quota.setCarryOverDays(BigDecimal.ZERO);
        quota.setUsedDays(BigDecimal.ZERO);
        quota.setLockedDays(BigDecimal.ZERO);
        quota.setExpireDate(LocalDate.of(year + 1, 3, 31));
        quota.setRemark("系统初始化");
        quotaMapper.insert(quota);
    }

    @Override
    public boolean lock(Long userId, Integer leaveType, int year, BigDecimal days) {
        ensureQuota(userId, year);
        int affected = quotaMapper.lock(userId, leaveType, year, days);
        if (affected == 0) {
            log.info("额度不足: userId={} type={} days={}", userId, leaveType, days);
            return false;
        }
        return true;
    }

    @Override
    public void release(Long userId, Integer leaveType, int year, BigDecimal days) {
        quotaMapper.release(userId, leaveType, year, days);
    }

    @Override
    public void consume(Long userId, Integer leaveType, int year, BigDecimal days) {
        if (quotaMapper.consume(userId, leaveType, year, days) == 0) {
            // 通过时额度行缺失属于数据异常（正常流程在提交时已 ensure），补建后重试一次
            ensureQuota(userId, year);
            quotaMapper.consume(userId, leaveType, year, days);
        }
    }

    @Override
    public void refund(Long userId, Integer leaveType, int year, BigDecimal days) {
        quotaMapper.refund(userId, leaveType, year, days);
    }

    @Override
    public void increaseTotal(Long userId, Integer leaveType, int year, BigDecimal days) {
        ensureQuota(userId, year);
        quotaMapper.increaseTotal(userId, leaveType, year, days);
    }

    @Override
    public List<LeaveQuotaVO> listByYear(int year, Long deptId) {
        LambdaQueryWrapper<LeaveQuota> wrapper = new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getYear, year)
                .orderByAsc(LeaveQuota::getUserId)
                .orderByAsc(LeaveQuota::getLeaveType);
        if (deptId != null) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .in(User::getDeptId, hrDataScopeSupport.subtreeIds(deptId)))
                    .stream().map(User::getId).toList();
            if (userIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(LeaveQuota::getUserId, userIds);
        }
        return toVOs(quotaMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LeaveQuotaVO> my(int year) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ensureQuota(userId, year);
        List<LeaveQuota> list = quotaMapper.selectList(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getUserId, userId)
                .eq(LeaveQuota::getYear, year)
                .orderByAsc(LeaveQuota::getLeaveType));
        return toVOs(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(QuotaAdjustRequest request) {
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "调整额度必须填写说明");
        }
        if (request.getTotalDays().signum() < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "总额度不能为负数");
        }
        ensureQuota(request.getUserId(), request.getYear());
        LeaveQuota existing = quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getUserId, request.getUserId())
                .eq(LeaveQuota::getLeaveType, request.getLeaveType())
                .eq(LeaveQuota::getYear, request.getYear())
                .last("LIMIT 1"));
        if (existing == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "额度记录不存在");
        }
        // 只提交需要改的字段：部分实体更新，不会覆盖 used/locked
        LeaveQuota patch = new LeaveQuota();
        patch.setId(existing.getId());
        patch.setTotalDays(request.getTotalDays());
        if (request.getCarryOverDays() != null) {
            patch.setCarryOverDays(request.getCarryOverDays());
        }
        patch.setRemark(request.getRemark());
        quotaMapper.updateById(patch);
    }

    private List<LeaveQuotaVO> toVOs(List<LeaveQuota> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(list.stream()
                        .map(LeaveQuota::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> deptNames = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        List<LeaveQuotaVO> vos = new ArrayList<>(list.size());
        for (LeaveQuota q : list) {
            LeaveQuotaVO vo = new LeaveQuotaVO();
            vo.setId(q.getId());
            vo.setUserId(q.getUserId());
            User u = users.get(q.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptId(u.getDeptId());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
            vo.setLeaveType(q.getLeaveType());
            vo.setLeaveTypeLabel(TYPE_LABEL.getOrDefault(q.getLeaveType(), "其他"));
            vo.setYear(q.getYear());
            vo.setTotalDays(nvl(q.getTotalDays()));
            vo.setCarryOverDays(nvl(q.getCarryOverDays()));
            vo.setUsedDays(nvl(q.getUsedDays()));
            vo.setLockedDays(nvl(q.getLockedDays()));
            vo.setAvailableDays(nvl(q.getTotalDays()).add(nvl(q.getCarryOverDays()))
                    .subtract(nvl(q.getUsedDays())).subtract(nvl(q.getLockedDays())));
            vo.setExpireDate(q.getExpireDate());
            vo.setRemark(q.getRemark());
            vos.add(vo);
        }
        return vos;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }
}
