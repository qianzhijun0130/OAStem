package com.kellen.oa_system.hr.attendance.service.impl;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.attendance.dto.AttendancePageQuery;
import com.kellen.oa_system.hr.attendance.dto.ClockRequest;
import com.kellen.oa_system.hr.attendance.dto.ScheduleBatchRequest;
import com.kellen.oa_system.hr.attendance.dto.ShiftSaveRequest;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.hr.attendance.vo.AttendanceVO;
import com.kellen.oa_system.hr.attendance.vo.MachineImportResultVO;
import com.kellen.oa_system.hr.attendance.vo.MissingCardVO;
import com.kellen.oa_system.hr.attendance.vo.ScheduleVO;
import com.kellen.oa_system.hr.attendance.vo.ShiftVO;
import com.kellen.oa_system.hr.attendance.vo.TodayAttendanceVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤业务门面。
 *
 * <p>本类只做三件事，别往里加业务逻辑：</p>
 * <ol>
 *   <li><b>事务边界</b>——所有 {@code @Transactional} 都留在这一层。日结与月结必须整段一个事务，
 *       中途提交会留下「考勤算了、汇总没算」的半套数据。</li>
 *   <li><b>数据范围</b>——{@code @DataScope} 必须标在 {@code page / pageMonth / exportMonth} 上。
 *       切面把可见范围写进 ThreadLocal，查询侧只消费；注解一旦漏标或下沉，
 *       {@code AttendanceScopeSupport} 会抛 {@code IllegalStateException}（BE-OPT-02 的做法：
 *       宁可报错，也不静默放行全公司数据）。</li>
 *   <li><b>委派</b>——按业务域转给下面六个服务，保证每个文件都小而专。</li>
 * </ol>
 *
 * <p>三个关键口径（与《开发文档/人力资源/02》一致）：未排班不产生考勤记录；
 * 只缺一次卡不判旷工；日结幂等（同一人同一天只有一行）。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceShiftService shiftService;
    private final AttendanceScheduleService scheduleService;
    private final AttendanceClockService clockService;
    private final AttendanceQueryService queryService;
    private final AttendanceSettleService settleService;
    private final AttendanceLockService lockService;
    private final AttendanceRecalcService recalcService;
    private final AttendanceExportService exportService;
    private final AttendanceImportService importService;
    private final AttendanceMissingCardService missingCardService;

    // ==================================================================
    // 班次
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public List<ShiftVO> listShifts() {
        return shiftService.listShifts();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveShift(ShiftSaveRequest request) {
        return shiftService.saveShift(request);
    }

    @Override
    public void deleteShift(Long id) {
        shiftService.deleteShift(id);
    }

    @Override
    public void updateShiftStatus(Long id, Integer status) {
        shiftService.updateShiftStatus(id, status);
    }

    // ==================================================================
    // 排班
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSchedule(ScheduleBatchRequest request) {
        return scheduleService.batchSchedule(request);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ScheduleVO> listSchedule(Long deptId, String month) {
        return scheduleService.listSchedule(deptId, month);
    }

    // ==================================================================
    // 打卡与查询
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public TodayAttendanceVO today() {
        return clockService.today();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clock(ClockRequest request) {
        clockService.clock(request);
    }

    @Transactional(readOnly = true)
    @DataScope
    @Override
    public PageResult<AttendanceVO> page(AttendancePageQuery query) {
        return queryService.page(query);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AttendanceVO> myMonth(String month) {
        return queryService.myMonth(month);
    }

    // ==================================================================
    // 汇总与结算
    // ==================================================================

    @Transactional(readOnly = true)
    @DataScope
    @Override
    public PageResult<AttendanceMonthVO> pageMonth(String month, Long deptId,
                                                   Integer pageNo, Integer pageSize) {
        return queryService.pageMonth(month, deptId, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleDate(LocalDate date, boolean force) {
        return settleService.settleDate(date, force);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleMonth(String month) {
        return settleService.settleMonth(month);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuild(String month) {
        return settleService.rebuild(month);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockMonth(String month) {
        lockService.lockMonth(month);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockMonth(String month, String reason) {
        lockService.unlockMonth(month, reason);
    }

    // ==================================================================
    // 假勤审批终态后的重算（BE-OPT-01）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int invalidateSettlement(Long userId, LocalDate from, LocalDate to) {
        return recalcService.invalidateSettlement(userId, from, to);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resettle(Long userId, LocalDate from, LocalDate to) {
        return recalcService.resettle(userId, from, to);
    }

    @Override
    public void resettleAfterCommit(Long userId, LocalDate from, LocalDate to) {
        recalcService.resettleAfterCommit(userId, from, to);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resettleLeaveRange(Long userId, LocalDate from, LocalDate to) {
        return recalcService.resettleLeaveRange(userId, from, to);
    }

    // ==================================================================
    // 考勤机数据导入与漏卡（BIZ-OPT-08）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MachineImportResultVO importMachineRecords(MultipartFile file) {
        return importService.importMachineRecords(file);
    }

    @Transactional(readOnly = true)
    @DataScope
    @Override
    public PageResult<MissingCardVO> pageMissingCard(String month, Long deptId, String keyword,
                                                     Integer pageNo, Integer pageSize) {
        return missingCardService.pageMissing(month, deptId, keyword, pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MissingCardVO> listMyMissingCard(String month) {
        return missingCardService.myMissing(month);
    }

    // ==================================================================
    // 导出
    // ==================================================================

    @Transactional(readOnly = true)
    @DataScope
    @Override
    public void exportMonth(String month, Long deptId, HttpServletResponse response) {
        exportService.exportMonth(month, deptId, response);
    }
}
