package com.kellen.oa_system.hr.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报名记录 VO。
 */
@Data
public class TrainEnrollVO {

    private Long id;
    private Long sessionId;
    private String sessionName;
    private Long courseId;
    private String courseName;
    private Integer trainType;
    private Integer required;
    private String requiredLabel;
    private BigDecimal hours;
    private Long userId;
    private String userName;
    private String deptName;
    private Integer enrollWay;
    private String enrollWayLabel;
    private Integer status;
    private String statusLabel;
    private String assignReason;
    private LocalDateTime checkinTime;
    private BigDecimal realHours;
    private BigDecimal score;
    private Integer assessWay;
    private Integer passed;
    private Integer satisfied;
    private Long certFileId;
    private LocalDateTime cancelTime;
    private String remark;
    private LocalDateTime createTime;
}
