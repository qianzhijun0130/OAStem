package com.kellen.oa_system.hr.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;


/**
 * 劳动合同，对应表 oa_employee_contract。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_contract")
public class EmployeeContract extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 合同编号 */
    private String contractNo;

    /** 类型：1固定期限 2无固定期限 3实习 4劳务 5返聘 */
    private Integer contractType;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期（无固定期限为 NULL） */
    private LocalDate endDate;

    /** 签订次数 */
    private Integer signCount;

    /** 签订日期 */
    private LocalDate signDate;

    /** 扫描件 file_id，逗号分隔 */
    private String fileIds;

    /** 状态：1生效 2已到期 3已解除 */
    private Integer status;

    private String remark;
}
