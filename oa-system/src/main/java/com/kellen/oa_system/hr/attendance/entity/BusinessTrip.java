package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出差单，对应表 oa_business_trip。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_business_trip")
public class BusinessTrip extends AuditEntity {


    /** 单据编号 */
    private String billCode;

    /** 申请人ID */
    private Long userId;

    /** 出差地点 */
    private String destination;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 出差天数 */
    private java.math.BigDecimal days;

    /** 预算金额 */
    private java.math.BigDecimal budget;

    /** 出差事由 */
    private String reason;

    /** 关联流程实例ID */
    private Long instanceId;

    /** 状态：0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer tripStatus;
}
