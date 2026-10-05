package com.kellen.oa_system.common.util;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel 导出工具（xlsx）。
 *
 * <p>用 SXSSF（流式）而非 XSSF：日志类导出动辄上万行，XSSF 会把整份文档
 * 留在堆里，容易 OOM；SXSSF 只保留最近若干行、其余溢写到临时文件。</p>
 *
 * <p>调用方只需给出表头与行数据，写出与响应头由本工具统一处理，避免每个
 * Controller 各写一遍 Content-Disposition（中文文件名极易漏掉 RFC 5987 编码）。</p>
 */
public final class ExcelExportUtil {

    /** 内存中保留的行数，超出部分溢写到磁盘临时文件 */
    private static final int ROW_ACCESS_WINDOW_SIZE = 200;

    private static final int DEFAULT_COLUMN_WIDTH = 20;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExcelExportUtil() {
    }

    /**
     * 把数据写成 xlsx 并直接写入响应流。
     *
     * @param response     当前响应
     * @param downloadName 下载文件名（含扩展名），中文由 RFC 5987 编码
     * @param sheetName    工作表名
     * @param headers      表头
     * @param rows         数据行，每个元素按表头顺序排列；值可为 null
     */
    public static void write(HttpServletResponse response,
                             String downloadName,
                             String sheetName,
                             List<String> headers,
                             List<List<Object>> rows) {
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "响应对象为空，无法导出");
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // filename* 由 ContentDisposition 负责 RFC 5987 编码，中文文件名才能在各浏览器正确还原
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(downloadName, StandardCharsets.UTF_8)
                        .build()
                        .toString());

        // SXSSFWorkbook 必须 close 才会释放临时文件；dispose() 额外清掉溢写文件
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(StringUtils.hasText(sheetName) ? sheetName : "Sheet1");

            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH * 256);
            }
            // 冻结首行：滚动数据时表头始终可见
            sheet.createFreezePane(0, 1);

            if (rows != null) {
                int rowIndex = 1;
                for (List<Object> data : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int i = 0; i < headers.size(); i++) {
                        Object value = data != null && i < data.size() ? data.get(i) : null;
                        writeCell(row.createCell(i), value);
                    }
                }
            }

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            workbook.dispose();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导出文件写出失败");
        }
    }

    /**
     * 导出「仅供填写」的空白模板。
     *
     * <p>与 {@link #write} 的区别有两点，都是为了「下载—填写—上传」这条闭环：</p>
     * <ol>
     *   <li>数据表<b>只写表头</b>，刻意不放示例行——否则管理员填完直接上传，
     *       系统会把示例行当成真实数据一起导入；</li>
     *   <li>填写说明单独占一张工作表，不会污染数据表的结构。</li>
     * </ol>
     *
     * @param response     当前响应
     * @param downloadName 下载文件名（含扩展名）
     * @param sheetName    数据表名
     * @param headers      表头
     * @param notes        填写说明，每行一条
     */
    public static void writeBlankTemplate(HttpServletResponse response,
                                         String downloadName,
                                         String sheetName,
                                         List<String> headers,
                                         List<String> notes) {
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "响应对象为空，无法导出");
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(downloadName, StandardCharsets.UTF_8)
                        .build()
                        .toString());

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);

            Sheet dataSheet = workbook.createSheet(StringUtils.hasText(sheetName) ? sheetName : "Sheet1");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                dataSheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH * 256);
            }
            dataSheet.createFreezePane(0, 1);

            if (notes != null && !notes.isEmpty()) {
                Sheet noteSheet = workbook.createSheet("填写说明");
                noteSheet.setColumnWidth(0, DEFAULT_COLUMN_WIDTH * 4 * 256);
                for (int i = 0; i < notes.size(); i++) {
                    noteSheet.createRow(i).createCell(0).setCellValue(notes.get(i));
                }
            }

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            workbook.dispose();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "模板文件写出失败");
        }
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(DATE_TIME_FORMATTER.format(dateTime));
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(DATE_FORMATTER.format(date));
            return;
        }
        cell.setCellValue(value.toString());
    }

    private static CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
