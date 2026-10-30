package com.kellen.oa_system.common.util;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 读取工具（xlsx / xls）。
 *
 * <p>与 {@link ExcelExportUtil} 对称：把「单元格取值」这类易踩坑的细节集中处理，
 * 业务代码只面对 {@code List<List<String>>}。典型坑：</p>
 * <ul>
 *   <li>手机号 / 身份证在 Excel 里常被存成数值，直接 toString 会得到 {@code 1.8907745656E10}；</li>
 *   <li>日期单元格是「数值 + 日期格式」，需按 {@link DateUtil#isCellDateFormatted} 判别后转换；</li>
 *   <li>公式单元格要取缓存结果而不是公式串。</li>
 * </ul>
 */
public final class ExcelImportUtil {

    /** 单次导入的最大数据行数，防止误传超大文件把内存撑爆 */
    public static final int MAX_DATA_ROWS = 5000;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExcelImportUtil() {
    }

    /**
     * 读取第一个工作表的全部行（含表头），按默认上限 {@link #MAX_DATA_ROWS} 校验。
     *
     * @param file 上传的 Excel 文件
     * @return 行文本列表，每行按列顺序排列；单元格不足时该行长度即为实际列数
     * @throws BizException 文件为空、格式非法、行数超限时抛出
     */
    public static List<List<String>> readFirstSheet(MultipartFile file) {
        return readFirstSheet(file, MAX_DATA_ROWS);
    }

    /**
     * 读取第一个工作表的全部行（含表头），使用调用方指定的行数上限。
     *
     * <p>上限不能全局统一：员工导入一次几十行，5000 足够；而考勤机导出的是「人 × 天 × 方向」，
     * 一家 500 人的公司一个月就有上万行。若都卡 5000，考勤机数据会被迫拆成多个文件手工分批。</p>
     *
     * @param maxDataRows 单次允许的最大数据行数（不含表头）
     */
    public static List<List<String>> readFirstSheet(MultipartFile file, int maxDataRows) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_EMPTY);
        }
        int limit = maxDataRows < 1 ? MAX_DATA_ROWS : maxDataRows;
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID);
            }
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                // 只有表头（或空表），没有可导入的数据
                throw new BizException(ErrorCode.USER_IMPORT_EMPTY);
            }
            if (lastRowNum > limit) {
                throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID,
                        "单次导入不能超过 " + limit + " 行，请拆分后分批导入");
            }

            List<List<String>> rows = new ArrayList<>(lastRowNum + 1);
            for (int i = 0; i <= lastRowNum; i++) {
                rows.add(readRow(sheet.getRow(i)));
            }
            return rows;
        } catch (BizException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // WorkbookFactory 对损坏 / 非 Excel 文件会抛各种运行时异常，统一转为业务异常
            throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID);
        }
    }

    /** 判断一行是否整行为空（模板里常见的空行需要跳过） */
    public static boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** 安全取列：列不存在时返回空串，避免调用方到处判 null */
    public static String cellAt(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    private static List<String> readRow(Row row) {
        if (row == null) {
            return List.of();
        }
        int lastCellNum = row.getLastCellNum();
        if (lastCellNum <= 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>(lastCellNum);
        for (int i = 0; i < lastCellNum; i++) {
            values.add(readCell(row.getCell(i)));
        }
        return values;
    }

    private static String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return readNumericCell(cell);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private static String readNumericCell(Cell cell) {
        double raw = cell.getNumericCellValue();
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
            // 日期序列号为整数说明不含时间部分（如 2026-09-29），输出 yyyy-MM-dd；
            // 含小数则是日期时间，统一格式化为 yyyy-MM-dd HH:mm:ss
            return raw == Math.floor(raw)
                    ? dateTime.toLocalDate().toString()
                    : DATE_TIME_FORMATTER.format(dateTime);
        }
        if (raw == Math.floor(raw) && !Double.isInfinite(raw)) {
            // 整数（手机号、工号流水等）按 long 输出，规避 1.8907745656E10 这类科学计数法
            return String.valueOf((long) raw);
        }
        return BigDecimal.valueOf(raw).stripTrailingZeros().toPlainString();
    }
}
