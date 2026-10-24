package com.kellen.oa_system.hr.training.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程保存请求。
 */
@Data
public class TrainCourseSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 课程编码 */
    @NotBlank(message = "课程编码不能为空")
    private String courseCode;

    /** 课程名称 */
    @NotBlank(message = "课程名称不能为空")
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

    /** 学时 */
    @NotNull(message = "学时不能为空")
    @DecimalMin(value = "0", message = "学时不能为负")
    private BigDecimal hours;

    /** 适用部门（逗号分隔，空=全部） */
    private String deptIds;

    /** 适用岗位序列 */
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
