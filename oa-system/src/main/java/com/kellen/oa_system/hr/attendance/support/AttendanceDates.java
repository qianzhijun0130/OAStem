package com.kellen.oa_system.hr.attendance.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 月份解析与月份串生成。
 *
 * <p>只需一处存在：考勤几乎所有接口都以 {@code yyyy-MM} 为入口，
 * 若各自 {@code YearMonth.parse}，就无法保证「空值取当月」「非法值统一 400」这两个约定一致。</p>
 */
public final class AttendanceDates {

    private AttendanceDates() {
    }

    /** 解析 yyyy-MM；空值取当月 */
    public static YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), AttendanceConstants.MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份格式应为 yyyy-MM");
        }
    }

    /** 日期所在月份的标准串（yyyy-MM） */
    public static String monthOf(LocalDate date) {
        return YearMonth.from(date).format(AttendanceConstants.MONTH_FORMAT);
    }
}
