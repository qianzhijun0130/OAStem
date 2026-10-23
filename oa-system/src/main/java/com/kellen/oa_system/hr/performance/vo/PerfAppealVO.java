package com.kellen.oa_system.hr.performance.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 申诉视图。
 */
@Data
public class PerfAppealVO {

    /** 主键 */
    private Long id;

    /** 考核表ID */
    private Long sheetId;

    /** 申诉人 */
    private String realName;

    /** 申诉理由 */
    private String reason;

    /** 1待处理 2维持 3调整 */
    private Integer status;

    /** 状态文本 */
    private String statusLabel;

    /** 处理意见 */
    private String handleOpinion;

    /** 处理人 */
    private String handlerName;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理前分数 */
    private BigDecimal beforeScore;

    /** 处理后分数 */
    private BigDecimal afterScore;

    /** 处理前等级 */
    private String beforeGrade;

    /** 处理后等级 */
    private String afterGrade;

    /** 申诉时间 */
    private LocalDateTime createTime;

}
