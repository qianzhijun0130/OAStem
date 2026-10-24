package com.kellen.oa_system.hr.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开课期次 VO。
 */
@Data
public class TrainSessionVO {

    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private Integer trainType;
    private String trainTypeLabel;
    private Integer required;
    private String requiredLabel;
    private BigDecimal hours;
    private String sessionName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String onlineLink;
    private Integer capacity;
    private Integer enrolledCount;
    private Integer waitCount;
    private LocalDateTime enrollDeadline;
    private Integer status;
    private String statusLabel;
    private Integer checkinWay;
    private String remark;

    /** 剩余名额（-1 表示不限） */
    private Integer remainQuota;

    /** 是否已过报名截止 */
    private Boolean enrollClosed;

    /** 是否已开始（可签到） */
    private Boolean started;

    /** 是否可报名 */
    private Boolean enrollable;

    /** 当前登录人的报名状态（null=未报名） */
    private Integer myEnrollStatus;

    private LocalDateTime createTime;
}
