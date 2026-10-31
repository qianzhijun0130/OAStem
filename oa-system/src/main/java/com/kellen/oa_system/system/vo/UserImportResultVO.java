package com.kellen.oa_system.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量导入结果。
 *
 * <p><b>策略已从「整批校验、全对才入库」改为「逐行成功、失败行跳过」</b>
 * （BIZ-OPT-09）。原因：300 人的入职表错 2 行时，整批拒绝会逼 HR 改完重传、
 * 每轮都要重新核对「谁已经建过了」。现在的语义是——成功的已经落库，
 * 失败的行按行号修正后重传即可，已成功的不重复创建。</p>
 *
 * <p>因此判断成败不能只看是否有异常：HTTP 与业务码恒为成功，
 * <b>必须读 successCount / failCount / errors</b>。</p>
 */
@Data
@Builder
public class UserImportResultVO {

    /** 是否有数据成功入库（部分成功也为 true；为 false 表示一行都没进） */
    private Boolean imported;

    /** 有效数据行数（不含表头与空行） */
    private Integer totalRows;

    /** 成功导入条数 */
    private Integer successCount;

    /** 失败行数（校验不通过 + 入库异常） */
    private Integer failCount;

    /** 失败明细（行号 + 原因），超过上限时只返回前若干条 */
    private List<UserImportErrorVO> errors;

    /** 面向用户的结论性提示 */
    private String message;
}
