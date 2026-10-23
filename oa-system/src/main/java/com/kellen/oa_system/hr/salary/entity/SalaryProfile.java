package com.kellen.oa_system.hr.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 薪资档案，分段生效。
 *
 * <p>调薪不是改这一行，而是新增一行更晚的 effective_date——历史月份必须能算回原值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_salary_profile")
public class SalaryProfile extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 岗位工资 */
    private BigDecimal postSalary;

    /** 绩效工资基数 */
    private BigDecimal perfSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 社保基数（空=按应发工资） */
    private BigDecimal socialBase;

    /** 公积金基数（空=同社保基数） */
    private BigDecimal fundBase;

    /** 参保城市 */
    private String city;

    /** 专项附加扣除（月度） */
    private BigDecimal specialDeduct;

    /** 调薪原因 */
    private String changeReason;
}
