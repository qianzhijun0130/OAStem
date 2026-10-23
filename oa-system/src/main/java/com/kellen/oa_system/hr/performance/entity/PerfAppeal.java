package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 绩效申诉（每张考核表一次）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_appeal")
public class PerfAppeal extends AuditEntity {

    /** 考核表ID */
    private Long sheetId;

    /** 申诉人 */
    private Long userId;

    /** 申诉理由 */
    private String reason;

    /** 1待处理 2维持 3调整 */
    private Integer status;

    /** 处理人 */
    private Long handleBy;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理意见 */
    private String handleOpinion;

    /** 处理前分数 */
    private BigDecimal beforeScore;

    /** 处理后分数 */
    private BigDecimal afterScore;

    /** 处理前等级 */
    private String beforeGrade;

    /** 处理后等级 */
    private String afterGrade;
}
