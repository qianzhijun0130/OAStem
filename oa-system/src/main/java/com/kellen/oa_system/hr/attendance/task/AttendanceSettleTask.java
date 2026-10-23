package com.kellen.oa_system.hr.attendance.task;

import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 考勤定时结算任务。
 *
 * <p>两个时间点：</p>
 * <ul>
 *   <li>每日 02:00 结算「昨天」——当天还没过完就结算会把正常打卡算成缺卡；</li>
 *   <li>每月 1 日 03:00 汇总「上个月」——必须在日结之后，否则汇总读到的是未结算数据。</li>
 * </ul>
 *
 * <p>两个动作都是幂等的：日结靠 {@code (user_id, attendance_date)} 唯一索引 + 覆盖写，
 * 月结靠 {@code (user_id, month)} 唯一索引 + 已封账跳过，重复执行不会产生重复数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceSettleTask {

    private final AttendanceService attendanceService;

    /** 每日 02:00 结算前一天的考勤 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void settleYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            int handled = attendanceService.settleDate(yesterday, false);
            log.info("考勤日结完成: date={} handled={}", yesterday, handled);
        } catch (RuntimeException e) {
            // 定时任务里的异常必须吞掉并记录，否则会中断后续所有调度
            log.error("考勤日结失败: date={}", yesterday, e);
        }
    }

    /** 每月 1 日 03:00 汇总上个月的考勤 */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void settleLastMonth() {
        String month = YearMonth.now().minusMonths(1).toString();
        try {
            int users = attendanceService.settleMonth(month);
            log.info("考勤月结完成: month={} users={}", month, users);
        } catch (RuntimeException e) {
            log.error("考勤月结失败: month={}", month, e);
        }
    }
}
