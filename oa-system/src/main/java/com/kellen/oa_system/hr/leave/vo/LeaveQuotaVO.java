package com.kellen.oa_system.hr.leave.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 假期额度视图。
 */
@Data
public class LeaveQuotaVO {

    private Long id;

    private Long userId;

    private String realName;

    private String workNumber;

    private Long deptId;

    private String deptName;

    private Integer leaveType;

    private String leaveTypeLabel;

    private Integer year;

    private BigDecimal totalDays;

    private BigDecimal carryOverDays;

    private BigDecimal usedDays;

    private BigDecimal lockedDays;

    /** 可用 = 总额 + 结转 − 已用 − 审批中占用 */
    private BigDecimal availableDays;

    private LocalDate expireDate;

    private String remark;
}
