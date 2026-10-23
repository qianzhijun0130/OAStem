package com.kellen.oa_system.hr.attendance.callback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.Overtime;
import com.kellen.oa_system.hr.attendance.mapper.OvertimeMapper;
import com.kellen.oa_system.hr.leave.service.LeaveQuotaService;
import com.kellen.oa_system.workflow.callback.ApprovalCallback;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 加班审批终态回写。
 *
 * <p>补偿方式为「转调休」时，在<b>审批通过</b>后才把时长折算进调休额度
 * （8 小时 = 1 天）。提交时加额度等于「还没批就先给假」，驳回后还得回滚，风险更高。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OvertimeApprovalCallback implements ApprovalCallback {

    public static final String FLOW_CODE = "HR_OVERTIME";

    /** 调休假别 */
    private static final int TYPE_COMPENSATORY = 8;
    /** 一天按 8 小时折算 */
    private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(8);
    /** 补偿方式：转调休 */
    private static final int COMPENSATE_LEAVE = 1;

    private static final int STATUS_APPROVING = 1;

    private final OvertimeMapper overtimeMapper;
    private final LeaveQuotaService quotaService;

    @Override
    public String flowCode() {
        return FLOW_CODE;
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        Overtime overtime = findByInstance(instance.getId());
        if (overtime == null || !Objects.equals(overtime.getOvertimeStatus(), STATUS_APPROVING)) {
            return;
        }
        patch(overtime.getId(), 2);
        if (Objects.equals(overtime.getCompensateType(), COMPENSATE_LEAVE)
                && overtime.getHours() != null && overtime.getHours().signum() > 0) {
            BigDecimal days = overtime.getHours().divide(HOURS_PER_DAY, 2, RoundingMode.HALF_UP);
            quotaService.increaseTotal(overtime.getUserId(), TYPE_COMPENSATORY,
                    overtime.getOvertimeDate().getYear(), days);
            log.info("加班转调休: overtimeId={} hours={} -> days={}",
                    overtime.getId(), overtime.getHours(), days);
        }
    }

    @Override
    public void onRejected(ApprovalInstance instance) {
        finish(instance, 3);
    }

    @Override
    public void onCancelled(ApprovalInstance instance) {
        finish(instance, 4);
    }

    @Override
    public void onTerminated(ApprovalInstance instance) {
        finish(instance, 4);
    }

    private void finish(ApprovalInstance instance, int status) {
        Overtime overtime = findByInstance(instance.getId());
        if (overtime == null || !Objects.equals(overtime.getOvertimeStatus(), STATUS_APPROVING)) {
            return;
        }
        patch(overtime.getId(), status);
    }

    private void patch(Long id, int status) {
        Overtime patch = new Overtime();
        patch.setId(id);
        patch.setOvertimeStatus(status);
        overtimeMapper.updateById(patch);
    }

    private Overtime findByInstance(Long instanceId) {
        return overtimeMapper.selectOne(new LambdaQueryWrapper<Overtime>()
                .eq(Overtime::getInstanceId, instanceId)
                .last("LIMIT 1"));
    }
}
