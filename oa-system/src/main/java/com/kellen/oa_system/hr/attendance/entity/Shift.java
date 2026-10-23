package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;


/**
 * 班次，对应表 oa_shift。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_shift")
public class Shift extends AuditEntity {

    /** 上班时间 */
    private LocalTime workStart;

    /** 下班时间（小于上班时间表示跨天） */
    private LocalTime workEnd;

    /** 班次编码 */
    private String shiftCode;

    /** 班次名称 */
    private String shiftName;

    /** 休息时长（分钟） */
    private Integer restMinutes;

    /** 迟到宽限（分钟） */
    private Integer lateGrace;

    /** 早退宽限（分钟） */
    private Integer earlyGrace;

    /** 是否弹性：0否 1是 */
    private Integer flexible;

    /** 是否免打卡：0否 1是 */
    private Integer freeClock;

    /** 状态：0停用 1启用 */
    private Integer status;
}
