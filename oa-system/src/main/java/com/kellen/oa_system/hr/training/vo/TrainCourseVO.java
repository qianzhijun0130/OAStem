package com.kellen.oa_system.hr.training.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程 VO。
 */
@Data
public class TrainCourseVO {

    private Long id;
    private String courseCode;
    private String courseName;
    private Integer trainType;
    private String trainTypeLabel;
    private Integer teacherType;
    private String teacherTypeLabel;
    private Long teacherId;
    private String teacherName;
    private String teacherOrg;
    private BigDecimal hours;
    private String deptIds;
    private String series;
    private Integer required;
    private String requiredLabel;
    private BigDecimal passScore;
    private Long fileId;
    private BigDecimal cost;
    private Integer status;
    private String statusLabel;
    private String remark;

    /** 已开期次数 */
    private Integer sessionCount;

    /** 已结业人次 */
    private Integer completedCount;
}
