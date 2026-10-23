package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.time.LocalTime;

/**
 * 班次视图。
 */
@Data
public class ShiftVO {

    private Long id;

    private String shiftCode;

    private String shiftName;

    private LocalTime workStart;

    private LocalTime workEnd;

    private Integer restMinutes;

    private Integer lateGrace;

    private Integer earlyGrace;

    private Integer flexible;

    private Integer freeClock;

    private Integer status;

    /** 标准工时（小时）= 下班 − 上班 − 休息；跨天按次日补偿 24 小时 */
    private String standardHours;

    /** 当前排班人数（仅列表展示，便于判断班次是否在用） */
    private Integer scheduleCount;
}
