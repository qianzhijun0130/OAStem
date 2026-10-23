package com.kellen.oa_system.hr.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假申请，对应表 oa_leave。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave")
public class Leave extends AuditEntity {


    /** 单据编号 */
    private String billCode;

    /** 申请人ID */
    private Long userId;

    /** 假别：1事假 2病假 3年假 4婚假 5产假 6陪产假 7丧假 8调休 */
    private Integer leaveType;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 请假天数 */
    private java.math.BigDecimal leaveDays;

    /** 销假后实际休假天数 */
    private java.math.BigDecimal actualDays;

    /** 实际返岗日期 */
    private LocalDate returnDate;

    /** 请假原因 */
    private String reason;

    /** 证明材料 file_id */
    private String attachmentIds;

    /** 关联流程实例ID */
    private Long instanceId;

    /** 状态：0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer leaveStatus;

    /** 销假状态：0未销假 1销假审批中 2已销假 */
    private Integer cancelStatus;

    /** 销假流程实例ID */
    private Long cancelInstanceId;
}
