package com.kellen.oa_system.hr.leave.callback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    }

    private Leave findByInstance(Long instanceId) {
        return leaveMapper.selectOne(new LambdaQueryWrapper<Leave>()
                .eq(Leave::getInstanceId, instanceId)
                .last("LIMIT 1"));
    }
}
