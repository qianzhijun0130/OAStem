package com.kellen.oa_system.hr.performance.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 绩效结果导入结果。
 *
 * <p>与考勤机导入同构：逐行校验、能进的行照常写、坏行连行号带原因退回，
 * <b>所以接口不报错也不代表全部成功——要看 {@code imported} 与 {@code failed}</b>。</p>
 *
 * <p>为什么不做全量回滚：一次导入几十行历史数据，错一两行是常态；整批回滚会让人对着
 * 「导入失败」反复猜是哪一行，而实际上大部分行是对的。</p>
 */
@Data
public class PerfImportResultVO {

    /** 读取到的数据行数（不含表头、不含空行） */
    private Integer total = 0;

    /** 成功写入的人数 */
    private Integer imported = 0;

    /** 覆盖了已有考核表的条数（其余为新建，历史员工在系统里通常还没有考核表） */
    private Integer updated = 0;

    /** 校验不通过的行数 */
    private Integer failed = 0;

    private List<RowError> errors = new ArrayList<>();

    @Data
    public static class RowError {

        /** Excel 行号（从 1 开始、含表头），便于 HR 直接定位 */
        private Integer row;

        private String message;

        public RowError() {
        }

        public RowError(Integer row, String message) {
            this.row = row;
            this.message = message;
        }
    }
}
