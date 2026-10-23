package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班 / 出差 / 补卡 的统一台账视图（字段按 bizType 取用）。
 */
@Data
public class HrRequestVO {

    private Long id;

    private String billCode;

    /** 1加班 2出差 3补卡 */
    private Integer bizType;

    private String bizTypeLabel;

    private Long userId;

    private String realName;

    private String workNumber;

    private String deptName;

    /** 加班日期 / 补卡日期 */
    private LocalDate bizDate;

    /** 出差开始日期 */
    private LocalDate startDate;

    /** 出差结束日期 */
    private LocalDate endDate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String destination;

    private BigDecimal hours;

    private BigDecimal days;

    private BigDecimal budget;

    /** 加班类型：1工作日 2休息日 3法定节假日 */
    private Integer overtimeType;

    /** 补偿方式：1转调休 2加班费 */
    private Integer compensateType;

    /** 补卡方向：1上班 2下班 */
    private Integer direction;

    private String reason;

    private Long instanceId;

    /** 0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer status;

    private String statusLabel;

    private Boolean canCancel;

    private LocalDateTime createTime;
}
