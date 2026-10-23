package com.kellen.oa_system.hr.leave.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 请假单查询条件。
 */
@Data
public class LeavePageQuery {

    /** 月份 yyyy-MM，按开始时间过滤；为空表示不限 */
    private String month;

    private Long userId;

    private Long deptId;

    /** 状态：0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer leaveStatus;

    private Integer leaveType;

    private String keyword;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数至少 1")
    private Integer pageSize = 10;
}
