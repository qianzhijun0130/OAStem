package com.kellen.oa_system.hr.attendance.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.ExcelImportUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 考勤机文件的表头识别与行解析。
 *
 * <p>单独成类是因为这一段全是「格式兼容」的脏活：真实考勤机的导出格式没有任何标准，
 * 常见三种形态——①「工号 + 打卡时间」两列（最常见）；②「工号 + 日期 + 时间」三列；
 * ③「工号 + 日期 + 时间 + 方向」四列（系统模板的形状）。这三种都要能读，
 * 而写入逻辑只应关心「这个人这一天几点刷了卡」，不该被格式差异污染。</p>
 */
public final class AttendanceMachineParser {

    /** 方向：上班 */
    public static final int DIRECTION_IN = 1;
    /** 方向：下班 */
    public static final int DIRECTION_OUT = 2;
    /** 方向：文件未标注，后续按时间先后推断 */
    public static final int DIRECTION_UNKNOWN = 0;

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));

    private static final List<DateTimeFormatter> TIME_ONLY_FORMATS = List.of(
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("H:mm"));

    private AttendanceMachineParser() {
    }

    /** 表头识别结果，-1 表示该列不存在 */
    public record Columns(int workNumber, int date, int time, int direction) {
    }

    /** 一条原始刷卡记录 */
    public record Punch(int rowNo, String workNumber, LocalDate date, LocalDateTime time, int direction) {

        /** 聚合键：同一人同一天的多条刷卡要合并成一格考勤 */
        public String dayKey() {
            return workNumber + "|" + date;
        }
    }

    /**
     * 由表头定位各列。缺「工号」或「打卡时间」直接拒绝整份文件——
     * 这两列缺失说明传错了文件（比如传了工资表），继续解析只会产出满屏「格式错误」。
     */
    public static Columns resolveHeader(List<String> header) {
        int workNumber = -1;
        int date = -1;
        int time = -1;
        int direction = -1;
        for (int i = 0; i < header.size(); i++) {
            String cell = header.get(i) == null ? "" : header.get(i).replace(" ", "");
            if (workNumber < 0 && containsAny(cell, "工号", "员工编号", "人员编号", "账号")) {
                workNumber = i;
            } else if (direction < 0 && containsAny(cell, "方向", "打卡类型", "进出")) {
                direction = i;
            } else if (time < 0 && containsAny(cell, "时间", "刷卡", "签到")) {
                time = i;
            } else if (date < 0 && containsAny(cell, "日期")) {
                date = i;
            }
        }
        if (workNumber < 0 || time < 0) {
            throw new BizException(ErrorCode.HR_ATTENDANCE_IMPORT_HEADER,
                    "缺少必需列：工号、打卡时间。当前表头为 " + header);
        }
        return new Columns(workNumber, date, time, direction);
    }

    /**
     * 解析一整份数据（跳过表头与空行）。
     *
     * @param onError 每发现一行错误回调一次（行号从 1 开始、含表头，便于 HR 直接定位）
     */
    public static List<Punch> parse(List<List<String>> rows, Columns columns, RowErrorSink onError) {
        List<Punch> punches = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (ExcelImportUtil.isBlankRow(row)) {
                continue;
            }
            int rowNo = i + 1;
            onError.onDataRow();
            String workNumber = ExcelImportUtil.cellAt(row, columns.workNumber());
            if (workNumber.isBlank()) {
                onError.onError(rowNo, "工号为空");
                continue;
            }
            LocalDate dateCell = parseDate(ExcelImportUtil.cellAt(row, columns.date()));
            if (dateCell == null) {
                dateCell = parseDate(ExcelImportUtil.cellAt(row, columns.time()));
            }
            LocalDateTime time = parseDateTime(ExcelImportUtil.cellAt(row, columns.time()), dateCell);
            if (time == null) {
                onError.onError(rowNo, "打卡时间格式不正确（示例：2026-10-01 08:30:00）");
                continue;
            }
            punches.add(new Punch(rowNo, workNumber, time.toLocalDate(), time,
                    parseDirection(ExcelImportUtil.cellAt(row, columns.direction()))));
        }
        return punches;
    }

    /** 行解析回调：用于「先计入总数、再按需记错误」的两段式统计 */
    public interface RowErrorSink {

        /** 发现一条非空数据行（用于累计 total） */
        void onDataRow();

        void onError(int rowNo, String message);
    }

    /**
     * 取该侧应使用的时间点。
     *
     * <p>有方向标注就按标注；没有任何方向标注时按时间先后推断：一天只有一次刷卡 → 视为上班卡
     * （最常见的情形就是下班忘了打）；两次以上 → 最早为上班、最晚为下班，
     * 中间的（午休外出）忽略。若文件里标了方向但这一侧没有记录，说明这一侧确实没刷卡。</p>
     */
    public static LocalDateTime resolveTime(List<Punch> punches, int direction) {
        List<Punch> matched = punches.stream().filter(p -> p.direction() == direction).toList();
        if (!matched.isEmpty()) {
            return direction == DIRECTION_IN
                    ? matched.stream().map(Punch::time).min(LocalDateTime::compareTo).orElse(null)
                    : matched.stream().map(Punch::time).max(LocalDateTime::compareTo).orElse(null);
        }
        if (punches.stream().anyMatch(p -> p.direction() != DIRECTION_UNKNOWN)) {
            return null;
        }
        List<LocalDateTime> times = punches.stream().map(Punch::time).sorted().toList();
        if (times.size() == 1) {
            return direction == DIRECTION_IN ? times.get(0) : null;
        }
        return direction == DIRECTION_IN ? times.get(0) : times.get(times.size() - 1);
    }

    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String text, LocalDate dateCell) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (RuntimeException ignored) {
                // 换下一种格式继续试
            }
        }
        if (dateCell != null) {
            for (DateTimeFormatter formatter : TIME_ONLY_FORMATS) {
                try {
                    return LocalDateTime.of(dateCell, LocalTime.parse(value, formatter));
                } catch (RuntimeException ignored) {
                    // 换下一种格式继续试
                }
            }
        }
        return null;
    }

    public static int parseDirection(String text) {
        if (text == null || text.isBlank()) {
            return DIRECTION_UNKNOWN;
        }
        String value = text.trim().toLowerCase();
        if (value.equals("1") || containsAny(value, "上班", "签到", "进场", "in")) {
            return DIRECTION_IN;
        }
        if (value.equals("2") || containsAny(value, "下班", "签退", "出场", "out")) {
            return DIRECTION_OUT;
        }
        return DIRECTION_UNKNOWN;
    }

    public static boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
