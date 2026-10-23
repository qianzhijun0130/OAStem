package com.kellen.oa_system.hr.leave.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假提交入参。
 */
@Data
public class LeaveSubmitRequest {

    /** 假别：1事假 2病假 3年假 4婚假 5产假 6陪产假 7丧假 8调休 */
    @NotNull(message = "请假类型不能为空")
    @Min(value = 1, message = "假别取值 1~8")
    @Max(value = 8, message = "假别取值 1~8")
    private Integer leaveType;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 请假天数；留空由后端按起止时间计算（以服务端结果为准，前端可篡改） */
    @DecimalMin(value = "0.5", message = "请假天数至少 0.5 天")
    private BigDecimal leaveDays;

    @Size(max = 1000, message = "请假原因不能超过 1000 字")
    private String reason;

    /** 证明材料 file_id，逗号分隔（病假 ≥3 天必填） */
    @Size(max = 255, message = "附件长度不能超过 255")
    private String attachmentIds;
}
