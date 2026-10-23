package com.kellen.oa_system.hr.salary.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 薪资手工调整（必须填原因，写留痕）。
 */
@Data
public class SalaryAdjustRequest {

    /** 其他收入 */
    private BigDecimal otherIncome;

    /** 其他扣款 */
    private BigDecimal otherDeduct;

    /** 调整原因 */
    private String remark;

}
