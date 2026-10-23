package com.kellen.oa_system.hr.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;


/**
 * 员工异动记录，对应表 oa_employee_change。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_change")
public class EmployeeChange extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 类型：1入职 2转正 3调岗 4调薪 5离职 6退休 */
    private Integer changeType;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 变更前快照 */
    private String beforeJson;

    /** 变更后快照 */
    private String afterJson;

    /** 关联流程实例ID */
    private Long instanceId;

    /** 变更说明 */
    private String remark;

    /** 操作人ID */
    private Long operatorId;
}
