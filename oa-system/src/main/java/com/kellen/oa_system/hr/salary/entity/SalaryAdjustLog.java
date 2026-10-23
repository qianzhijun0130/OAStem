package com.kellen.oa_system.hr.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薪资手工调整留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_salary_adjust_log")
public class SalaryAdjustLog extends BaseEntity {

    /** 核算记录ID */
    private Long recordId;

    /** 被调整字段 */
    private String fieldName;

    /** 字段中文名 */
    private String fieldLabel;

    /** 调整前 */
    private BigDecimal beforeValue;

    /** 调整后 */
    private BigDecimal afterValue;

    /** 调整原因 */
    private String reason;

    /** 操作人 */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
