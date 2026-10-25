package com.kellen.oa_system.hr.leave.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 地方假别政策保存入参。
 *
 * <p>{@code id} 为空表示新增，非空表示修改。业务唯一键是「城市 + 假别 + 年度」，
 * 三者相同视为同一条政策，不允许并存。</p>
 */
@Data
public class LeavePolicySaveRequest {

    private Long id;

    @NotBlank(message = "城市不能为空")
    @Size(max = 32, message = "城市长度不能超过 32")
    private String city;

    /** 假别：只允许 4婚假 / 5产假 / 6陪产假 / 7丧假 */
    @NotNull(message = "假别不能为空")
    @Min(value = 4, message = "该假别不适用地方政策配置")
    @Max(value = 7, message = "该假别不适用地方政策配置")
    private Integer leaveType;

    @NotNull(message = "法定天数不能为空")
    @DecimalMin(value = "0.5", message = "法定天数至少 0.5 天")
    @DecimalMax(value = "365.0", message = "法定天数过大，请核对")
    private BigDecimal days;

    @NotNull(message = "生效年度不能为空")
    @Min(value = 2000, message = "生效年度不合法")
    @Max(value = 2100, message = "生效年度不合法")
    private Integer effectiveYear;

    @Size(max = 255, message = "依据长度不能超过 255")
    private String remark;
}
