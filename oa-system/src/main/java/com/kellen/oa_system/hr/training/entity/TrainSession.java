package com.kellen.oa_system.hr.training.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 培训开课期次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_train_session")
public class TrainSession extends AuditEntity {

    /** 课程 */
    private Long courseId;

    /** 期次名（如 2026 第 3 期） */
    private String sessionName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 地点 */
    private String location;

    /** 线上链接 */
    private String onlineLink;

    /** 名额（0=不限） */
    private Integer capacity;

    /** 已报名数（不含候补） */
    private Integer enrolledCount;

    /** 候补数 */
    private Integer waitCount;

    /** 报名截止 */
    private LocalDateTime enrollDeadline;

    /** 1报名中 2进行中 3已结束 4已取消 */
    private Integer status;

    /** 1二维码 2手工点名 */
    private Integer checkinWay;

    /** 备注 */
    private String remark;
}
