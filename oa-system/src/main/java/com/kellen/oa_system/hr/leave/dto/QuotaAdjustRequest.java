package com.kellen.oa_system.hr.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 额度手工调整入参（HR 专用）。
 */
@Data
public class QuotaAdjustRequest {

    @NotNull(message = "员工不能为空")
    private Long userId;

    /** 假别：1事假 2病假 3年假 4婚假 5产假 6陪产假 7丧假 8调休 */
    @NotNull(message = "假别不能为空")
    @Min(value = 1, message = "假别取值 1~8")
    @Max(value = 8, message = "假别取值 1~8")
    private Integer leaveType;

    @NotNull(message = "年度不能为空")
    private Integer year;

    /** 调整后的年度总额度（覆盖写，不是增量） */
    @NotNull(message = "总额度不能为空")
    private BigDecimal totalDays;

    /** 上年结转天数，可空表示不变 */
    private BigDecimal carryOverDays;

    /** 调整说明，必填（写进台账备注，便于审计） */
    private String remark;
}
