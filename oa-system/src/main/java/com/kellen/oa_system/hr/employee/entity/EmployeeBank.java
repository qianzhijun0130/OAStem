package com.kellen.oa_system.hr.employee.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import com.kellen.oa_system.security.crypto.EncryptedStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 员工银行与社保账号，对应表 oa_employee_bank。
 *
 * <p>{@code autoResultMap = true} 是字段级加解密生效的前提：
 * 少了它结果映射不会带上 typeHandler，查询会把密文原样返回。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_employee_bank", autoResultMap = true)
public class EmployeeBank extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 开户行 */
    private String bankName;

    /** 银行卡号（库中 AES-GCM 密文） */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String bankAccount;

    /** 社保号（库中 AES-GCM 密文） */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String socialNo;

    /** 公积金账号（库中 AES-GCM 密文） */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String fundNo;
}
