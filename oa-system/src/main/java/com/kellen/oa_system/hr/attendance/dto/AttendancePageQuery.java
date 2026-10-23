package com.kellen.oa_system.hr.attendance.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 考勤明细查询条件。
 */
@Data
public class AttendancePageQuery {

    /** 月份 yyyy-MM；为空时默认当月 */
    private String month;

    private Long deptId;

    private Long userId;

    /** 考勤状态：1正常 2迟到 3早退 4缺勤 5假勤 */
    private Integer attendanceStatus;

    /** 关键词：姓名 / 工号 */
    private String keyword;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数至少 1")
    private Integer pageSize = 10;
}
