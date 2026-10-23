package com.kellen.oa_system.hr.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * 班次新增 / 修改入参。
 */
@Data
public class ShiftSaveRequest {

    private Long id;

    @NotBlank(message = "班次编码不能为空")
    @Size(max = 32, message = "班次编码长度不能超过 32")
    private String shiftCode;

    @NotBlank(message = "班次名称不能为空")
    @Size(max = 64, message = "班次名称长度不能超过 64")
    private String shiftName;

    @NotNull(message = "上班时间不能为空")
    private LocalTime workStart;

    @NotNull(message = "下班时间不能为空")
    private LocalTime workEnd;

    @Min(value = 0, message = "休息时长不能为负")
    private Integer restMinutes;

    @Min(value = 0, message = "迟到宽限不能为负")
    private Integer lateGrace;

    @Min(value = 0, message = "早退宽限不能为负")
    private Integer earlyGrace;

    /** 是否弹性：1 时不判迟到早退 */
    @Min(value = 0, message = "弹性标记只能是 0/1")
    @Max(value = 1, message = "弹性标记只能是 0/1")
    private Integer flexible;

    /** 是否免打卡 */
    @Min(value = 0, message = "免打卡标记只能是 0/1")
    @Max(value = 1, message = "免打卡标记只能是 0/1")
    private Integer freeClock;

    @Min(value = 0, message = "状态只能是 0/1")
    @Max(value = 1, message = "状态只能是 0/1")
    private Integer status;
}
