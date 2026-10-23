package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;


/**
 * 排班，对应表 oa_shift_schedule。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_shift_schedule")
public class ShiftSchedule extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 排班日期 */
    private LocalDate scheduleDate;

    /** 班次ID，NULL=休息/免打卡 */
    private Long shiftId;

    /** 日期类型：1工作日 2休息日 3法定节假日 4调休上班 */
    private Integer dayType;

    /** 是否已日结：0否 1是 */
    private Integer settled;
}
