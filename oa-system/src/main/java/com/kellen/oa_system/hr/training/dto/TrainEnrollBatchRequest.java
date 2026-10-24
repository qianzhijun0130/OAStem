package com.kellen.oa_system.hr.training.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * HR 批量指派报名请求。
 */
@Data
public class TrainEnrollBatchRequest {

    /** 期次 */
    @NotNull(message = "期次不能为空")
    private Long sessionId;

    /** 员工ID列表 */
    @NotEmpty(message = "至少选择一名员工")
    private List<Long> userIds;

    /** 指派原因（HR 指派可超额，必须说明原因） */
    private String reason;
}
