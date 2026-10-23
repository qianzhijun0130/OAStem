package com.kellen.oa_system.hr.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 员工银行与社保账号，对应表 oa_employee_bank。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_bank")
public class EmployeeBank extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 开户行 */
    private String bankName;

    /** 银行卡号（加密） */
    private String bankAccount;

    /** 社保号（加密） */
    private String socialNo;

    /** 公积金账号（加密） */
    private String fundNo;
}
