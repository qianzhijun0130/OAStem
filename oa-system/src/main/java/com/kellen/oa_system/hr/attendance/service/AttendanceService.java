package com.kellen.oa_system.hr.attendance.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.attendance.dto.AttendancePageQuery;
import com.kellen.oa_system.hr.attendance.dto.ClockRequest;
import com.kellen.oa_system.hr.attendance.dto.ScheduleBatchRequest;
import com.kellen.oa_system.hr.attendance.dto.ShiftSaveRequest;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.hr.attendance.vo.AttendanceVO;
import com.kellen.oa_system.hr.attendance.vo.ShiftVO;
import com.kellen.oa_system.hr.attendance.vo.ScheduleVO;
import com.kellen.oa_system.hr.attendance.vo.TodayAttendanceVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤业务接口：班次、排班、打卡、明细、日结与月结。
 *
 * <p>日结与月结是两条独立的动作：日结把「排班 + 打卡」固化成考勤明细，
 * 月结把明细汇总成月度结果供薪资引用。顺序不能颠倒。</p>
 */
public interface AttendanceService {

    // ---------------- 班次 ----------------

    List<ShiftVO> listShifts();

    Long saveShift(ShiftSaveRequest request);

    void deleteShift(Long id);

    void updateShiftStatus(Long id, Integer status);

    // ---------------- 排班 ----------------

    /**
     * 按部门批量生成整月排班。
     *
     * @return 实际生成（或覆盖）的排班条数
     */
    int batchSchedule(ScheduleBatchRequest request);

    /** 某月某部门的排班明细（员工 × 日期），供排班页渲染日历 */
    List<ScheduleVO> listSchedule(Long deptId, String month);

    // ---------------- 打卡与查询 ----------------

    /** 今日考勤卡片（未排班时 scheduled=false） */
    TodayAttendanceVO today();

    /** 打卡：方向 1 上班 / 2 下班 */
    void clock(ClockRequest request);

    /** 考勤明细分页（受数据范围约束） */
    PageResult<AttendanceVO> page(AttendancePageQuery query);

    /** 我的某月考勤明细 */
    List<AttendanceVO> myMonth(String month);

    // ---------------- 汇总与结算 ----------------

    PageResult<AttendanceMonthVO> pageMonth(String month, Long deptId, Integer pageNo, Integer pageSize);

    /**
     * 日结指定日期：把排班 + 打卡固化为考勤明细。
     *
     * @param force true 表示重算（覆盖已日结结果），false 只处理未日结的排班
     * @return 处理的记录数
     */
    int settleDate(LocalDate date, boolean force);

    /** 月度汇总：把明细聚合到 oa_attendance_month（已封账的月份跳过） */
    int settleMonth(String month);

    /** 重算某月：先逐日日结，再做月汇总 */
    int rebuild(String month);

    /** 封账（封账后薪资才能引用） */
    void lockMonth(String month);

    /** 解封（仅超管，需填写原因） */
    void unlockMonth(String month, String reason);

    /**
     * 作废某人在指定区间内「因请假产生」的考勤记录，并把排班标记为未结算。
     *
     * <p>销假（提前返岗）后调用：返岗日之后这个人已经正常上班，之前按请假算出来的
     * 假勤记录必须作废，否则月汇总里会同时出现「休假天数」与「已返岗」两份矛盾数据。
     * 只动「状态为假勤且没有任何打卡」的行——有打卡说明当天确实来过，
     * 属于人工确认过的事实，不能因为销假被抹掉。</p>
     *
     * @return 被作废的天数
     */
    int resettleLeaveRange(Long userId, java.time.LocalDate from, java.time.LocalDate to);

    /** 导出月度汇总：扣款依据要给财务签字，不能只留在页面上 */
    void exportMonth(String month, Long deptId, jakarta.servlet.http.HttpServletResponse response);

    // ---------------- 假勤审批终态后的考勤重算（BE-OPT-01） ----------------

    /**
     * 失效某人在区间内的日结结果，等待重算（排班与考勤两侧的 settled 都置 0）。
     *
     * <p>为什么必须做：日结在每天 02:00 结算前一天，判定「缺勤」时只看<b>那一刻</b>已通过的假勤。
     * 请假多为 1~2 天多级审批，常常晚于日结通过；若不失效重算，当天的考勤行会永久停在「缺勤」，
     * 而月结又重新查询已通过假勤，导致同一天同时计入「旷工 1 天」与「事假 1 天」，
     * 薪资按旷工 2 倍 + 事假 1 倍 = 3 倍日工资扣款。</p>
     *
     * @return 受影响的排班条数
     */
    int invalidateSettlement(Long userId, java.time.LocalDate from, java.time.LocalDate to);

    /**
     * 立即重算某人区间内的考勤（以当前已通过的假勤为准）。
     *
     * <p>与 {@code rebuild} 的区别：只处理这一个人、这一段日期，不影响其他人；
     * 若区间覆盖已封账月份则直接抛错，不静默跳过——静默跳过会让错误留在工资单里。</p>
     *
     * @return 重算的排班条数
     */
    int resettle(Long userId, java.time.LocalDate from, java.time.LocalDate to);

    /**
     * 审批终态后重算，事务提交后再执行。
     *
     * <p>放在提交后是为了不把日结的读写塞进审批事务（会放大锁窗口、拉长审批响应）；
     * 失败只记录 ERROR 不阻断审批——月结侧已有「请假/出差当天不计旷工」的互斥兜底，
     * 单次重算失败不会再造成重复扣款。</p>
     */
    void resettleAfterCommit(Long userId, java.time.LocalDate from, java.time.LocalDate to);

}
