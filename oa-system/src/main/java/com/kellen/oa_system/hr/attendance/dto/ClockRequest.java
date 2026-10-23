package com.kellen.oa_system.hr.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 打卡入参。
 */
@Data
public class ClockRequest {

    /** 方向：1 上班 2 下班 */
    @NotNull(message = "打卡方向不能为空")
    @Min(value = 1, message = "打卡方向只能是 1上班 2下班")
    @Max(value = 2, message = "打卡方向只能是 1上班 2下班")
    private Integer direction;

    /** 打卡地点（可选，仅记录，不做地理围栏拦截） */
    @Size(max = 200, message = "打卡地点长度不能超过 200")
    private String location;
}
