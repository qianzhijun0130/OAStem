package com.kellen.oa_system.hr.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 排班批量生成入参。
 *
 * <p>按「部门 + 月份 + 班次」一次生成整月排班：
 * 工作日套用班次，法定节假日与休息日按班次制跳过（无法定节假日数据时周六日按休息处理）。</p>
 */
@Data
public class ScheduleBatchRequest {

    @NotNull(message = "部门不能为空")
    private Long deptId;

    /** 月份 yyyy-MM */
    @NotBlank(message = "月份不能为空")
    private String month;

    /** 班次ID */
    @NotNull(message = "班次不能为空")
    private Long shiftId;

    /** 是否包含周末：0 不含（默认） 1 含（单休/轮班场景） */
    @Min(value = 0, message = "包含周末标记只能是 0/1")
    @Max(value = 1, message = "包含周末标记只能是 0/1")
    private Integer includeWeekend;

    /** 是否覆盖已存在排班（1 覆盖，0 跳过） */
    @Min(value = 0, message = "覆盖标记只能是 0/1")
    @Max(value = 1, message = "覆盖标记只能是 0/1")
    private Integer overwrite;

    /** 用于前端预览的起始日期，默认取月份第一天 */
    private LocalDate startDate;

    private LocalDate endDate;
}
