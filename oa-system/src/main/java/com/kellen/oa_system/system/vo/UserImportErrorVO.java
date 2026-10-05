package com.kellen.oa_system.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量导入的单行错误明细。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserImportErrorVO {

    /** Excel 中的行号（含表头，从 1 开始），便于管理员直接定位 */
    private Integer row;

    /** 失败原因 */
    private String message;
}
