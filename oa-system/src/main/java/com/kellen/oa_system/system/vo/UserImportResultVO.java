package com.kellen.oa_system.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量导入结果。
 *
 * <p>采用「整批校验、全对才入库」策略：只要有一行校验失败，整个文件都不会写入，
 * 并把逐行原因返回给调用方，避免出现「导了一半」的中间态数据。</p>
 */
@Data
@Builder
public class UserImportResultVO {

    /** 是否已成功入库 */
    private Boolean imported;

    /** 有效数据行数（不含表头与空行） */
    private Integer totalRows;

    /** 成功导入条数；未入库时为 0 */
    private Integer successCount;

    /** 校验失败行数；入库成功时为 0 */
    private Integer failCount;

    /** 失败明细（行号 + 原因），超过上限时只返回前若干条 */
    private List<UserImportErrorVO> errors;

    /** 面向用户的结论性提示 */
    private String message;
}
