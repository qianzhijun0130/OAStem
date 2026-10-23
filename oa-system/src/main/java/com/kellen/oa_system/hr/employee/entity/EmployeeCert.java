package com.kellen.oa_system.hr.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;


/**
 * 员工证照，对应表 oa_employee_cert。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_cert")
public class EmployeeCert extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 类型：1学历 2学位 3职业资格 4职称 5特种作业 6培训证书 */
    private Integer certType;

    /** 证照名称 */
    private String certName;

    /** 证书编号 */
    private String certNo;

    /** 发证机构 */
    private String issueOrg;

    /** 发证日期 */
    private LocalDate issueDate;

    /** 有效期至（NULL=长期） */
    private LocalDate expireDate;

    /** 扫描件 file_id */
    private String fileIds;

    private String remark;
}
