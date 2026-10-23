package com.kellen.oa_system.hr.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 薪资档案保存（新增即调薪，不覆盖历史段）。
 */
@Data
public class SalaryProfileSaveRequest {

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

    /** 专项附加扣除 */
    private BigDecimal specialDeduct;

    /** 调薪原因 */
    private String changeReason;

}
