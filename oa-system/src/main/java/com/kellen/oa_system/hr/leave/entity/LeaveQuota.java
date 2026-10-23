package com.kellen.oa_system.hr.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 假期额度台账，对应表 oa_leave_quota。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave_quota")
public class LeaveQuota extends AuditEntity {


    /** 员工ID */
    private Long userId;

    /** 假别：1事假 2病假 3年假 4婚假 5产假 6陪产假 7丧假 8调休 */
    private Integer leaveType;

    /** 年度 */
    private Integer year;

    /** 年度总额度 */
    private java.math.BigDecimal totalDays;

    /** 上年结转 */
    private java.math.BigDecimal carryOverDays;

    /** 已使用 */
    private java.math.BigDecimal usedDays;

    /** 审批中占用 */
    private java.math.BigDecimal lockedDays;

    /** 结转额度失效日 */
    private LocalDate expireDate;

    /** 调整说明 */
    private String remark;
}
