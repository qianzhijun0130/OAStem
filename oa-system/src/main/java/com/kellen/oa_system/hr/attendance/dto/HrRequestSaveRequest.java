package com.kellen.oa_system.hr.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班 / 出差 / 补卡 的统一提交入参。
 *
 * <p>三者都是「一次短期申请 + 一条台账」，字段少且高度相似，因此共用一个 DTO，
 * 由服务按 {@link #bizType} 做分支校验：1 加班、2 出差、3 补卡。
 * 这样避免三套几乎相同的 DTO/VO/Controller，代价是字段是全集的并集——
 * 每个分支只读自己关心的字段，并在服务里显式校验必填。</p>
 */
@Data
public class HrRequestSaveRequest {

    /** 业务类型：1加班 2出差 3补卡 */
    @Min(value = 1, message = "业务类型取值 1~3")
    @Max(value = 3, message = "业务类型取值 1~3")
    private Integer bizType;

    // ---------- 加班 ----------

    /** 加班类型：1工作日 2休息日 3法定节假日 */
    @Min(value = 1, message = "加班类型取值 1~3")
    @Max(value = 3, message = "加班类型取值 1~3")
    private Integer overtimeType;

    private LocalDate overtimeDate;

    /** 补偿方式：1转调休 2加班费 */
    @Min(value = 1, message = "补偿方式取值 1~2")
    @Max(value = 2, message = "补偿方式取值 1~2")
    private Integer compensateType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @DecimalMin(value = "0.5", message = "加班时长至少 0.5 小时")
    @DecimalMax(value = "24.0", message = "单次加班时长不能超过 24 小时")
    private BigDecimal hours;

    // ---------- 出差 ----------

    @Size(max = 200, message = "出差地点不能超过 200 字")
    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    @DecimalMin(value = "0", message = "预算不能为负")
    private BigDecimal budget;

    // ---------- 补卡 ----------

    private LocalDate cardDate;

    /** 补卡方向：1上班 2下班 */
    @Min(value = 1, message = "补卡方向取值 1~2")
    @Max(value = 2, message = "补卡方向取值 1~2")
    private Integer direction;

    private LocalDateTime cardTime;

    // ---------- 通用 ----------

    @Size(max = 500, message = "事由不能超过 500 字")
    private String reason;
}
