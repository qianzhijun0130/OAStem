package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班单，对应表 oa_overtime。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_overtime")
public class Overtime extends AuditEntity {


    /** 单据编号 */
    private String billCode;

    /** 申请人ID */
    private Long userId;

    /** 加班日期 */
    private LocalDate overtimeDate;

    /** 类型：1工作日 2休息日 3法定节假日 */
    private Integer overtimeType;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 加班时长（小时） */
    private java.math.BigDecimal hours;

    /** 补偿：1转调休 2加班费 */
    private Integer compensateType;

    /** 加班事由 */
    private String reason;

    /** 关联流程实例ID */
    private Long instanceId;

    /** 状态：0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer overtimeStatus;
}
