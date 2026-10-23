package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;


/**
 * 节假日与调休日，对应表 oa_holiday。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_holiday")
public class Holiday extends AuditEntity {

    /** 日期 */
    private LocalDate holidayDate;

    /** 类型：2休息日 3法定节假日 4调休上班 */
    private Integer dayType;

    /** 名称 */
    private String holidayName;
}
