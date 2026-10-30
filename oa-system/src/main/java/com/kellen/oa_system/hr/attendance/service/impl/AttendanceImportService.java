package com.kellen.oa_system.hr.attendance.service.impl;

import com.kellen.oa_system.common.util.ExcelImportUtil;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.AttendanceMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceImportLoader;
import com.kellen.oa_system.hr.attendance.support.AttendanceMachineParser;
import com.kellen.oa_system.hr.attendance.support.AttendanceMachineParser.Punch;
import com.kellen.oa_system.hr.attendance.support.AttendanceRules;
import com.kellen.oa_system.hr.attendance.vo.MachineImportResultVO;
import com.kellen.oa_system.system.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 考勤机原始数据导入。
 *
 * <p><b>幂等靠「同人同天同侧同时间」判定，不靠不去重地追加记录</b>：企业最常见的用法是
 * 「月底把整月数据导一次，发现漏了几天再导一次同一个文件」。若每次追加，第二次导入就会
 * 把每人每天变成两条记录，月结直接翻倍。所以重复上传同一文件的结果必须是
 * {@code imported == 0}、{@code duplicate == 人天数}。</p>
 *
 * <p><b>判定规则一律走 {@link AttendanceRules}</b>：本类只负责把「刷卡时间点」写进考勤行，
 * 迟到/早退/缺卡/缺勤全部交给规则类。否则导入算出的迟到分钟与日结、审批重算算出来的不一致，
 * 而三者共用同一条明细行——同一份数据三种说法，无法解释。</p>
 *
 * <p>取数全部委托 {@link AttendanceImportLoader}，写入循环里没有任何查询入口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceImportService {

    /** 单批上限：500 人 × 31 天 × 2 次 ≈ 3.1 万行，取 2 万留余量 */
    public static final int MAX_ROWS = 20000;

    /** 返回给前端的错误/提示条数上限，避免一次性渲染上万行 */
    private static final int MAX_DETAIL_ROWS = 50;

    /** 打卡来源：2 = 考勤机（1 系统 3 补卡，见 Attendance 实体注释） */
    private static final int SOURCE_MACHINE = 2;

    private final AttendanceMapper attendanceMapper;
    private final AttendanceImportLoader loader;
    private final AttendanceRules rules;

    /**
     * 导入考勤机数据。逐行校验、能进的行照常写，坏行原样退回。
     *
     * @param file HR 上传的 Excel（必含「工号」与「打卡时间」；「日期」「方向」可选）
     */
    public MachineImportResultVO importMachineRecords(MultipartFile file) {
        List<List<String>> rows = ExcelImportUtil.readFirstSheet(file, MAX_ROWS);
        AttendanceMachineParser.Columns columns = AttendanceMachineParser.resolveHeader(rows.get(0));
        MachineImportResultVO result = new MachineImportResultVO();

        List<Punch> punches = AttendanceMachineParser.parse(rows, columns, new Sink(result));
        if (punches.isEmpty()) {
            return result;
        }
        Map<String, List<Punch>> byDay = new LinkedHashMap<>();
        for (Punch p : punches) {
            byDay.computeIfAbsent(p.dayKey(), k -> new ArrayList<>()).add(p);
        }
        loader.assertNoLockedMonth(byDay.keySet());
        applyPunches(byDay, loader.loadUsers(punches), result);
        log.info("考勤机数据导入: total={} imported={} duplicate={} failed={}",
                result.getTotal(), result.getImported(), result.getDuplicate(), result.getFailed());
        return result;
    }

    // ==================================================================
    // 写入
    // ==================================================================

    private void applyPunches(Map<String, List<Punch>> byDay, Map<String, User> users,
                              MachineImportResultVO result) {
        // 先把工号解析成员工：查不到的工号逐行报错，剩下的才进入写库阶段
        Map<String, User> dayOwner = new HashMap<>();
        for (Map.Entry<String, List<Punch>> entry : byDay.entrySet()) {
            Punch first = entry.getValue().get(0);
            User user = users.get(first.workNumber());
            if (user == null) {
                addError(result, first.rowNo(), "工号 " + first.workNumber() + " 在系统中不存在");
                continue;
            }
            dayOwner.put(entry.getKey(), user);
        }
        if (dayOwner.isEmpty()) {
            return;
        }
        List<Long> userIds = dayOwner.values().stream().map(User::getId).distinct().toList();
        LocalDate minDate = dayOwner.keySet().stream().map(AttendanceImportService::dateOf)
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = dayOwner.keySet().stream().map(AttendanceImportService::dateOf)
                .max(LocalDate::compareTo).orElseThrow();
        Map<String, Attendance> existing = loader.loadAttendances(userIds, minDate, maxDate);
        Map<String, ShiftSchedule> schedules = loader.loadSchedules(userIds, minDate, maxDate);
        Map<Long, Shift> shifts = loader.loadShifts();

        for (Map.Entry<String, List<Punch>> entry : byDay.entrySet()) {
            User user = dayOwner.get(entry.getKey());
            if (user == null) {
                continue;
            }
            List<Punch> punches = entry.getValue();
            LocalDate date = punches.get(0).date();
            ShiftSchedule schedule = schedules.get(user.getId() + "|" + date);
            if (schedule == null || schedule.getShiftId() == null) {
                // 未排班不产生考勤记录：没有班次就算不出迟到早退，硬写进去只会让月结分母失准
                addError(result, punches.get(0).rowNo(), date + " 未排班，无法判定考勤状态");
                continue;
            }
            Shift shift = shifts.get(schedule.getShiftId());
            if (shift == null) {
                addError(result, punches.get(0).rowNo(), date + " 的班次已被删除，请先补排班");
                continue;
            }
            writeOneDay(user, date, shift, punches, existing.get(user.getId() + "|" + date), result);
        }
    }

    private void writeOneDay(User user, LocalDate date, Shift shift, List<Punch> punches,
                             Attendance current, MachineImportResultVO result) {
        boolean created = current == null;
        Attendance attendance = created ? new Attendance() : current;
        int rowNo = punches.get(0).rowNo();
        int written = 0;
        int duplicated = 0;

        int[] counters = applySide(attendance, true,
                AttendanceMachineParser.resolveTime(punches, AttendanceMachineParser.DIRECTION_IN),
                result, rowNo, date);
        written += counters[0];
        duplicated += counters[1];
        counters = applySide(attendance, false,
                AttendanceMachineParser.resolveTime(punches, AttendanceMachineParser.DIRECTION_OUT),
                result, rowNo, date);
        written += counters[0];
        duplicated += counters[1];

        if (written == 0) {
            if (duplicated > 0) {
                // 两侧都是同一批数据 → 重复导入，按「人天」计一次，与 imported 同口径
                result.setDuplicate(result.getDuplicate() + 1);
            }
            return;
        }

        attendance.setUserId(user.getId());
        attendance.setAttendanceDate(date);
        attendance.setShiftId(shift.getId());
        // 数据变了就必须重新日结，否则月结继续沿用旧的 settled=1 结果
        attendance.setSettled(0);
        rules.applyStatus(attendance, shift, date);
        // 当天若有已通过的请假/出差，日结与月结会覆盖成假勤；这里不重复判断，避免两处各写一遍
        if (created) {
            attendanceMapper.insert(attendance);
        } else {
            attendanceMapper.updateById(attendance);
        }
        result.setImported(result.getImported() + 1);
    }

    /**
     * 写入一侧（上班或下班），返回 {@code [写入数, 重复数]}。
     *
     * <p>三条规则：空缺 → 补上；已有且为考勤机来源 → 覆盖（考勤机是权威来源，
     * 重导修正过的数据要能生效）；已有且为网页打卡/补卡 → <b>保留原值</b>并给提示。
     * 第三条是有意的：网页打卡与补卡都经过本人确认或审批，不该被一份 Excel 悄悄改掉。</p>
     */
    private int[] applySide(Attendance attendance, boolean checkIn, LocalDateTime target,
                            MachineImportResultVO result, int rowNo, LocalDate date) {
        if (target == null) {
            return new int[]{0, 0};
        }
        LocalDateTime current = checkIn ? attendance.getCheckInTime() : attendance.getCheckOutTime();
        Integer source = checkIn ? attendance.getCheckInSource() : attendance.getCheckOutSource();
        String side = checkIn ? "上班卡" : "下班卡";
        if (current == null) {
            if (checkIn) {
                attendance.setCheckInTime(target);
                attendance.setCheckInSource(SOURCE_MACHINE);
            } else {
                attendance.setCheckOutTime(target);
                attendance.setCheckOutSource(SOURCE_MACHINE);
            }
            return new int[]{1, 0};
        }
        if (current.equals(target)) {
            return new int[]{0, 1};
        }
        if (source != null && source == SOURCE_MACHINE) {
            if (checkIn) {
                attendance.setCheckInTime(target);
            } else {
                attendance.setCheckOutTime(target);
            }
            return new int[]{1, 0};
        }
        addWarning(result, rowNo, date + " " + side + "已有网页打卡/补卡记录，保留原值");
        return new int[]{0, 0};
    }

    private static LocalDate dateOf(String dayKey) {
        return LocalDate.parse(dayKey.substring(dayKey.indexOf('|') + 1));
    }

    // ==================================================================
    // 结果累计
    // ==================================================================

    private void addError(MachineImportResultVO result, int rowNo, String message) {
        result.setFailed(result.getFailed() + 1);
        if (result.getErrors().size() < MAX_DETAIL_ROWS) {
            result.getErrors().add(new MachineImportResultVO.RowError(rowNo, message));
        }
    }

    private void addWarning(MachineImportResultVO result, int rowNo, String message) {
        if (result.getWarnings().size() < MAX_DETAIL_ROWS) {
            result.getWarnings().add(new MachineImportResultVO.RowError(rowNo, message));
        }
    }

    /** 把解析阶段的回调桥接到结果对象（解析与统计解耦在 parser 里做） */
    private record Sink(MachineImportResultVO result) implements AttendanceMachineParser.RowErrorSink {

        @Override
        public void onDataRow() {
            result.setTotal(result.getTotal() + 1);
        }

        @Override
        public void onError(int rowNo, String message) {
            result.setFailed(result.getFailed() + 1);
            if (result.getErrors().size() < MAX_DETAIL_ROWS) {
                result.getErrors().add(new MachineImportResultVO.RowError(rowNo, message));
            }
        }
    }
}
