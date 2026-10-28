package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMonthMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.attendance.support.AttendanceDates;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 月考勤封账 / 解封。
 *
 * <p>封账不是一个状态字段那么简单，它是<b>全模块的刹车</b>：封账后月结不再覆盖、
 * 审批重算会直接失败并提示先解封、销假也不动该月。所以单独成类——
 * 任何「悄悄改已封账数据」的改动都应该在这里被看见。</p>
 *
 * <p>解封仅限超级管理员，且必须填原因（原因进审计日志）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceLockService {

    private final AttendanceMonthMapper monthMapper;

    public void lockMonth(String month) {
        String text = AttendanceDates.parseMonth(month).format(AttendanceConstants.MONTH_FORMAT);
        List<AttendanceMonth> list = monthMapper.selectList(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, text));
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该月暂无考勤汇总，请先执行月结");
        }
        Long operator = SecurityContextUtil.getCurrentUserId();
        for (AttendanceMonth m : list) {
            if (Objects.equals(m.getStatus(), AttendanceConstants.MONTH_LOCKED)) {
                continue;
            }
            AttendanceMonth patch = new AttendanceMonth();
            patch.setId(m.getId());
            patch.setStatus(AttendanceConstants.MONTH_LOCKED);
            patch.setLockTime(LocalDateTime.now());
            patch.setLockBy(operator);
            monthMapper.updateById(patch);
        }
    }

    public void unlockMonth(String month, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "解封必须填写原因");
        }
        String text = AttendanceDates.parseMonth(month).format(AttendanceConstants.MONTH_FORMAT);
        List<AttendanceMonth> list = monthMapper.selectList(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getMonth, text));
        for (AttendanceMonth m : list) {
            AttendanceMonth patch = new AttendanceMonth();
            patch.setId(m.getId());
            patch.setStatus(AttendanceConstants.MONTH_OPEN);
            monthMapper.updateById(patch);
        }
        log.warn("考勤月份解封: month={} reason={} operator={}", text, reason,
                SecurityContextUtil.getCurrentUserId());
    }
}
