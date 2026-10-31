package com.kellen.oa_system.system.support;

import com.kellen.oa_system.common.util.ExcelImportUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 导入单元格的读取与日期解析（导入链共用）。
 *
 * <p>为什么单独抽出来：解析器与合同解析器都要读单元格、都要容忍
 * 「2026-09-29 / 2026/9/29 / 2026.9.29 / 2026年9月29日」四种写法。
 * 两处各写一份的结果是某天只给其中一处加了新格式，于是「合同日期能导、
 * 入职日期不能导」——用户完全无法理解这种差异。</p>
 */
public final class UserImportCells {

    /** HR 实际会用的四种写法，按常见程度排序 */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"));

    private UserImportCells() {
    }

    /** 取单元格文本并去首尾空白；越界返回空串 */
    public static String text(List<String> row, int index) {
        return ExcelImportUtil.cellAt(row, index).trim();
    }

    /** 解析日期；无法识别返回 null（调用方据此决定是报错还是走默认值） */
    public static LocalDate date(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // 逐个尝试下一个格式
            }
        }
        return null;
    }
}
