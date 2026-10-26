package com.kellen.oa_system.hr.attendance.callback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.BusinessTrip;
import com.kellen.oa_system.hr.attendance.mapper.BusinessTripMapper;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import com.kellen.oa_system.workflow.callback.ApprovalCallback;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 出差审批终态回写（只改业务状态）。
 *
 * <p>出差期间的考勤由日结体现（排班仍在该时段时，日结看到的是当天实际打卡情况）。
 * 「出差期间免打卡」需要把出差区间展开成逐日考勤记录，属 P1 与考勤结算联动的部分。
 * 本类只做两件事：改业务状态 + 让受影响的日期重算，不在回调里自建第二套判定规则。</p>
 */
@Component
@RequiredArgsConstructor
public class TripApprovalCallback implements ApprovalCallback {

    public static final String FLOW_CODE = "HR_TRIP";

    private static final int STATUS_APPROVING = 1;

    private final BusinessTripMapper tripMapper;
    private final AttendanceService attendanceService;

    @Override
    public String flowCode() {
        return FLOW_CODE;
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        finish(instance, 2);
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
        BusinessTrip trip = tripMapper.selectOne(new LambdaQueryWrapper<BusinessTrip>()
                .eq(BusinessTrip::getInstanceId, instance.getId())
                .last("LIMIT 1"));
        if (trip == null || !Objects.equals(trip.getTripStatus(), STATUS_APPROVING)) {
            return;
        }
        BusinessTrip patch = new BusinessTrip();
        patch.setId(trip.getId());
        patch.setTripStatus(status);
        tripMapper.updateById(patch);
        // 出差状态变化会改变「当天算不算缺勤」：审批晚于日结时必须重算，否则同一天
        // 既计入旷工又计入出差天数（BE-OPT-01，与请假回调同一处理）。
        if (trip.getStartDate() != null && trip.getEndDate() != null) {
            attendanceService.resettleAfterCommit(trip.getUserId(), trip.getStartDate(), trip.getEndDate());
        }
    }
}
