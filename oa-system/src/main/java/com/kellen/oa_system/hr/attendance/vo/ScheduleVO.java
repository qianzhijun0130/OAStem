package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 排班视图（按「员工 × 日期」一条，供排班日历渲染）。
 */
@Data
public class ScheduleVO {

    private Long id;

    private Long userId;

    private String realName;

    private String workNumber;

    private LocalDate scheduleDate;

    private Long shiftId;

    private String shiftName;

    /** 日期类型：1工作日 2休息日 3法定节假日 4调休上班 */
    private Integer dayType;

    private Integer settled;
}
