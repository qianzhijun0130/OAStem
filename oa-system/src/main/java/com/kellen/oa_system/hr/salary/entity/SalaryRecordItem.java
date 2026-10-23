package com.kellen.oa_system.hr.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薪资核算明细（工资条按它逐项渲染）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_salary_record_item")
public class SalaryRecordItem extends BaseEntity {

    /** 核算记录ID */
    private Long recordId;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 类型：1收入 2扣款 */
    private Integer itemType;

    /** 金额 */
    private BigDecimal amount;

    /** 排序 */
    private Integer sort;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
