package com.kellen.oa_system.hr.attendance.callback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.CardSupplement;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.mapper.CardSupplementMapper;
import com.kellen.oa_system.workflow.callback.ApprovalCallback;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 补卡审批终态回写：通过后把补卡时间写进考勤明细。
 *
 * <p>写入后把 {@code settled} 置 0，让下一次日结重新计算状态与迟到/早退分钟——
 * 回调里不重复实现一遍班次判定逻辑（否则两套算法一定会慢慢跑偏）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardApprovalCallback implements ApprovalCallback {

    public static final String FLOW_CODE = "HR_CARD";

    private static final int STATUS_APPROVING = 1;
    /** 打卡来源：3 补卡 */
    private static final int SOURCE_SUPPLEMENT = 3;

    private final CardSupplementMapper cardMapper;
    private final AttendanceMapper attendanceMapper;

    @Override
    public String flowCode() {
        return FLOW_CODE;
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        CardSupplement card = findByInstance(instance.getId());
        if (card == null || !Objects.equals(card.getCardStatus(), STATUS_APPROVING)) {
            return;
        }
        CardSupplement patch = new CardSupplement();
        patch.setId(card.getId());
        patch.setCardStatus(2);
        cardMapper.updateById(patch);

        Attendance attendance = attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getUserId, card.getUserId())
                .eq(Attendance::getAttendanceDate, card.getCardDate())
                .last("LIMIT 1"));
        if (attendance == null) {
            // 当天没有考勤行（未排班）：补卡通过也不该凭空造一条考勤，
            // 待 HR 补排班后由日结生成
            log.info("补卡通过但当天无考勤记录，跳过写入: cardId={} date={}", card.getId(), card.getCardDate());
            return;
        }
        Attendance att = new Attendance();
        att.setId(attendance.getId());
        if (Objects.equals(card.getDirection(), 1)) {
            att.setCheckInTime(card.getCardTime());
            att.setCheckInSource(SOURCE_SUPPLEMENT);
            att.setCardMissing(recomputeMissing(attendance.getCardMissing(), 1));
        } else {
            att.setCheckOutTime(card.getCardTime());
            att.setCheckOutSource(SOURCE_SUPPLEMENT);
            att.setCardMissing(recomputeMissing(attendance.getCardMissing(), 2));
        }
        // 交回日结重算状态/工时，避免在回调里维护第二套判定规则
        att.setSettled(0);
        attendanceMapper.updateById(att);
        log.info("补卡已写入考勤: cardId={} date={} direction={}", card.getId(), card.getCardDate(), card.getDirection());
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

    /**
     * 缺卡标记收敛：0 无 / 1 缺上班 / 2 缺下班 / 3 缺两次。
     * 补上某个方向后，把对应位清掉。
     */
    private static Integer recomputeMissing(Integer current, int direction) {
        int value = current == null ? 0 : current;
        if (value == 0) {
            return 0;
        }
        if (direction == 1) {
            return value == 3 ? 2 : (value == 1 ? 0 : value);
        }
        return value == 3 ? 1 : (value == 2 ? 0 : value);
    }

    private void finish(ApprovalInstance instance, int status) {
        CardSupplement card = findByInstance(instance.getId());
        if (card == null || !Objects.equals(card.getCardStatus(), STATUS_APPROVING)) {
            return;
        }
        CardSupplement patch = new CardSupplement();
        patch.setId(card.getId());
        patch.setCardStatus(status);
        cardMapper.updateById(patch);
    }

    private CardSupplement findByInstance(Long instanceId) {
        return cardMapper.selectOne(new LambdaQueryWrapper<CardSupplement>()
                .eq(CardSupplement::getInstanceId, instanceId)
                .last("LIMIT 1"));
    }
}
