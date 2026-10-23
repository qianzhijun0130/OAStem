package com.kellen.oa_system.hr.salary.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 工资条单项。
 */
@Data
public class SalaryItemLineVO {

    /** 编码 */
    private String itemCode;

    /** 名称 */
    private String itemName;

    /** 1收入 2扣款 */
    private Integer itemType;

    /** 金额 */
    private BigDecimal amount;

}
