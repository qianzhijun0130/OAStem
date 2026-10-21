package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公司实体，对应表 oa_company。
 *
 * <p>此前系统只用到 {@code oa_department.company_id}，没有实体承接这张表。
 * 审批列表需要展示「所属公司」时，必须把部门上的 companyId 翻译成公司名，
 * 故补一个最小实体 + Mapper（只读场景，不做增删改）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_company")
public class Company extends AuditEntity {

    private String companyName;

    private String companyCode;

    private Long parentId;

    private String legalPerson;

    private String address;

    private String phone;

    private String email;

    private Integer sort;

    private Integer status;
}
