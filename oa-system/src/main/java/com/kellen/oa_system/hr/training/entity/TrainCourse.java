package com.kellen.oa_system.hr.training.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 培训课程。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_train_course")
public class TrainCourse extends AuditEntity {

    /** 课程编码 */
    private String courseCode;

    /** 课程名称 */
    private String courseName;

    /** 1新员工 2岗位技能 3管理能力 4合规安全 5通用素养 */
    private Integer trainType;

    /** 1内部讲师 2外部讲师 */
    private Integer teacherType;

    /** 内部讲师 */
    private Long teacherId;

    /** 外部讲师姓名 */
    private String teacherName;

    /** 外部讲师机构 */
    private String teacherOrg;

    /** 学时（小时） */
    private BigDecimal hours;

    /** 适用部门（空=全部） */
    private String deptIds;

    /** 适用岗位序列（空=全部） */
    private String series;

    /** 1必修 0选修 */
    private Integer required;

    /** 及格线 */
    private BigDecimal passScore;

    /** 教材附件 */
    private Long fileId;

    /** 培训成本 */
    private BigDecimal cost;

    /** 1启用 2停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
