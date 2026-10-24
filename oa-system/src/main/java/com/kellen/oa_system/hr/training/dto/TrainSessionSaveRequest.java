package com.kellen.oa_system.hr.training.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开课期次保存请求。
 */
@Data
public class TrainSessionSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 课程 */
    @NotNull(message = "课程不能为空")
    private Long courseId;

    /** 期次名 */
    @NotNull(message = "期次名不能为空")
    private String sessionName;

    /** 开始时间 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 地点 */
    private String location;

    /** 线上链接 */
    private String onlineLink;

    /** 名额（0=不限） */
    private Integer capacity;

    /** 报名截止 */
    private LocalDateTime enrollDeadline;

    /** 1二维码 2手工点名 */
    private Integer checkinWay;

    /** 备注 */
    private String remark;
}
