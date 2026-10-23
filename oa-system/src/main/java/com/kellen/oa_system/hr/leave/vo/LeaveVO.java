package com.kellen.oa_system.hr.leave.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假单视图。
 */
@Data
public class LeaveVO {

    private Long id;

    private String billCode;

    private Long userId;

    private String realName;

    private String workNumber;

    private Long deptId;

    private String deptName;

    private Integer leaveType;

    private String leaveTypeLabel;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal leaveDays;

    private BigDecimal actualDays;

    private LocalDate returnDate;

    private String reason;

    private String attachmentIds;

    private Long instanceId;

    /** 0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer leaveStatus;

    private String leaveStatusLabel;

    /** 0未销假 1销假审批中 2已销假 */
    private Integer cancelStatus;

    /** 是否可撤销（仅审批中且为本人） */
    private Boolean canCancel;

    private LocalDateTime createTime;
}
