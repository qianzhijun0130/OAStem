package com.kellen.oa_system.hr.leave.callback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import com.kellen.oa_system.hr.leave.entity.Leave;
import com.kellen.oa_system.hr.leave.mapper.LeaveMapper;
import com.kellen.oa_system.hr.leave.service.LeaveQuotaService;
import com.kellen.oa_system.workflow.callback.ApprovalCallback;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * 请假审批终态回写。
 *
 * <p>只依赖 Mapper 与额度服务，<b>不依赖 ApprovalService / LeaveService</b>：
 * 否则会形成「审批服务 → 回调 → 请假服务 → 审批服务」的循环依赖。</p>
 *
 * <p>幂等：只有业务状态为「审批中」才处理，重复回调直接跳过。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveApprovalCallback implements ApprovalCallback {

    /** 与 LeaveServiceImpl.FLOW_CODE 一致 */
    public static final String FLOW_CODE = "HR_LEAVE";

    private static final int STATUS_APPROVING = 1;
    private static final int STATUS_PASSED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_CANCELLED = 4;

    /** 需要占用额度的假别：年假、调休 */
    private static final Set<Integer> QUOTA_TYPES = Set.of(3, 8);

    private final LeaveMapper leaveMapper;
    private final LeaveQuotaService quotaService;
    /**
     * 直接依赖 AttendanceService（而非 LeaveService）：{@code LeaveService -> AttendanceService}
     * 已是单向依赖，方向一致，不构成循环。
     */
    private final AttendanceService attendanceService;

    @Override
    public String flowCode() {
        return FLOW_CODE;
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        Leave leave = findByInstance(instance.getId());
        if (leave == null || !Objects.equals(leave.getLeaveStatus(), STATUS_APPROVING)) {
            return;
        }
        Leave patch = new Leave();
        patch.setId(leave.getId());
        patch.setLeaveStatus(STATUS_PASSED);
        leaveMapper.updateById(patch);
        // 占用转已用：审批通过才真正扣额度，避免「提交即扣、驳回还要手工退」的错账
        if (QUOTA_TYPES.contains(leave.getLeaveType())) {
            quotaService.consume(leave.getUserId(), leave.getLeaveType(),
                    leave.getStartTime().getYear(), leave.getLeaveDays());
        }
        log.info("请假审批通过: leaveId={} days={}", leave.getId(), leave.getLeaveDays());
        // 审批可能晚于当天 02:00 的日结，此时考勤行已被判为「缺勤」。
        // 不重算的话月结会同时计入「旷工」与「事假」，薪资按 3 倍日工资扣款（BE-OPT-01）。
        resettleFor(leave);
    }

    @Override
    public void onRejected(ApprovalInstance instance) {
        finish(instance, STATUS_REJECTED, "驳回");
    }

    @Override
    public void onCancelled(ApprovalInstance instance) {
        finish(instance, STATUS_CANCELLED, "撤销");
    }

    @Override
    public void onTerminated(ApprovalInstance instance) {
        finish(instance, STATUS_CANCELLED, "终止");
    }

    private void finish(ApprovalInstance instance, int status, String action) {
        Leave leave = findByInstance(instance.getId());
        if (leave == null || !Objects.equals(leave.getLeaveStatus(), STATUS_APPROVING)) {
            return;
        }
        Leave patch = new Leave();
        patch.setId(leave.getId());
        patch.setLeaveStatus(status);
        leaveMapper.updateById(patch);
        if (QUOTA_TYPES.contains(leave.getLeaveType())) {
            quotaService.release(leave.getUserId(), leave.getLeaveType(),
                    leave.getStartTime().getYear(), leave.getLeaveDays());
        }
        log.info("请假{}: leaveId={} 额度已释放", action, leave.getId());
        // 驳回/撤销/终止后，这段日期不再算请假，考勤应恢复为原来的判定结果
        resettleFor(leave);
    }

    /**
     * 让考官勤按当前已通过的假勤重新判定。
     *
     * <p>区间取请假单起止日；重算在审批事务提交后执行，避免把日结读写塞进审批事务。</p>
     */
    private void resettleFor(Leave leave) {
        if (leave.getStartTime() == null || leave.getEndTime() == null) {
            return;
        }
        attendanceService.resettleAfterCommit(leave.getUserId(),
                leave.getStartTime().toLocalDate(), leave.getEndTime().toLocalDate());
    }

    private Leave findByInstance(Long instanceId) {
        return leaveMapper.selectOne(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getInstanceId, instanceId)
                .last("LIMIT 1"));
    }
}
