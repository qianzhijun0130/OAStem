package com.kellen.oa_system.hr.training.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 培训爽约记录（累计 3 次限制报名 1 个月）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_train_absence")
public class TrainAbsence extends AuditEntity {

    /** 员工 */
    private Long userId;

    /** 期次 */
    private Long sessionId;

    /** 报名记录 */
    private Long enrollId;

    /** 爽约时间 */
    private LocalDateTime absenceTime;

    /** 限制报名截止日 */
    private LocalDate restrictedUntil;
}
