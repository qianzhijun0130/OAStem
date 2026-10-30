package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 考勤机数据导入结果。
 *
 * <p>导入<b>不做全量回滚</b>：逐行校验，能进的行照常写入，坏行原样退回给 HR 改。
 * 理由是一万行的文件里错三五行的概率极高，整批回滚会让人对着「导入失败」四个字
 * 反复猜是哪一行；而部分成功 + 逐行错误清单，HR 改完再传一次即可（重复行会自动跳过）。</p>
 *
 * <p>所以 HTTP 状态与业务码都是成功——<b>判断导入是否成功要看 {@code imported}
 * 与 {@code errors}</b>，不是看接口有没有报错。</p>
 */
@Data
public class MachineImportResultVO {

    /** 读取到的数据行数（不含表头、不含空行） */
    private Integer total = 0;

    /** 实际写入或更新的「人天」条数：一行一天可能对应两条刷卡记录，故与 total 不等 */
    private Integer imported = 0;

    /** 与已有考勤机记录一致而跳过（重复导入同一文件时全部落这里，幂等） */
    private Integer duplicate = 0;

    /** 校验不通过的行数（这些行的数据一条都没写进去） */
    private Integer failed = 0;

    /** 逐行错误，最多保留 {@code MAX_ERROR_ROWS} 条，避免前端渲染上万行 */
    private List<RowError> errors = new ArrayList<>();

    /**
     * 已跳过但不算失败的提示：例如该侧已有网页打卡或补卡记录，考勤机数据不覆盖它。
     *
     * <p>与 {@link #errors} 分开计数是有意的：这类行<b>另一侧可能已经写入成功</b>，
     * 若并入 failed，HR 会看到「失败 3 条」却找不到可修的地方。</p>
     */
    private List<RowError> warnings = new ArrayList<>();

    /** 单个错误行 */
    @Data
    public static class RowError {

        /** Excel 行号（从 1 开始，含表头，便于 HR 直接在文件里定位） */
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
