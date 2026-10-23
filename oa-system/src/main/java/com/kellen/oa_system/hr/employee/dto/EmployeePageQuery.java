package com.kellen.oa_system.hr.employee.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/**
 * 花名册查询条件。
 */
@Data
public class EmployeePageQuery {

    /** 关键词：姓名 / 工号 / 手机号 */
    private String keyword;

    private Long deptId;

    private Long positionId;

    /**
     * 任职状态：1待入职 2试用 3正式 4待离职 5已离职 6退休。
     * 为空时默认只查在职（2/3/4），要看离职员工需显式传 5。
     */
    private Integer employmentStatus;

    private LocalDate entryDateStart;

    private LocalDate entryDateEnd;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数至少 1")
    private Integer pageSize = 10;
}
