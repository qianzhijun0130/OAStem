package com.kellen.oa_system.hr.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 绩效校准留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_perf_calibrate_log")
public class PerfCalibrateLog extends BaseEntity {

    /** 考核表ID */
    private Long sheetId;

    /** 调整前分数 */
    private BigDecimal beforeScore;

    /** 调整后分数 */
    private BigDecimal afterScore;

    /** 调整前等级 */
    private String beforeGrade;

    /** 调整后等级 */
    private String afterGrade;

    /** 校准理由 */
    private String reason;

    /** 操作人 */
    private Long operatorId;

    /** 操作时间 */
    private LocalDateTime createTime;
}
