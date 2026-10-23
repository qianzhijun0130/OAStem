package com.kellen.oa_system.hr.salary.dto;


import lombok.Data;

/**
 * 薪资项保存。
 */
@Data
public class SalaryItemSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 类型：1收入 2扣款 */
    private Integer itemType;

    /** 计入社保基数 */
    private Integer includeSocial;

    /** 计入个税 */
    private Integer includeTax;

    /** 允许手工调整 */
    private Integer editable;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private Integer status;

    /** 备注 */
    private String remark;

}
