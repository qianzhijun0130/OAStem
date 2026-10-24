package com.kellen.oa_system.hr.training.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训报名（含签到与成绩）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_train_enroll")
public class TrainEnroll extends AuditEntity {

    /** 期次 */
    private Long sessionId;

    /** 员工 */
    private Long userId;

    /** 1自助报名 2HR指派 */
    private Integer enrollWay;

    /** 1已报名 2候补 3已取消 4已签到 5缺勤 6已完成 7爽约 */
    private Integer status;

    /** HR 指派原因 */
    private String assignReason;

    /** 签到时间 */
    private LocalDateTime checkinTime;

    /** 实际学时（按签到计算） */
    private BigDecimal realHours;

    /** 考核成绩 */
    private BigDecimal score;

    /** 1考试 2作业 3讲师评估 4免考 */
    private Integer assessWay;

    /** 1及格 0不及格 */
    private Integer passed;

    /** 满意度 1~5 */
    private Integer satisfied;

    /** 证书附件 */
    private Long certFileId;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 备注 */
    private String remark;

    /**
     * 所属课程ID（<b>非持久化</b>，只在内存里做统计用）。
     *
     * <p>报名表只存 session_id，课程要再查一次期次表。统计时按课程分组
     * 反复查库太慢，所以在装配时把课程ID塞进这个临时字段。</p>
     */
    @TableField(exist = false)
    private Long sessionCourseId;
}
