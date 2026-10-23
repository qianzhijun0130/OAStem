package com.kellen.oa_system.hr.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 薪资项配置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_salary_item")
public class SalaryItem extends AuditEntity {

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 类型：1收入 2扣款 */
    private Integer itemType;

    /** 是否计入社保/公积金基数 */
    private Integer includeSocial;

    /** 是否计入个税应税收入 */
    private Integer includeTax;

    /** 是否允许核算时手工调整 */
    private Integer editable;

    /** 排序 */
    private Integer sort;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
