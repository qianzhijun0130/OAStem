package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 候选人阶段推进请求。
 *
 * <p>回退（{@code rollback=true}）需 HR 权限且必须填理由，
 * 且「已入职」不可回退——人已经入职了，退回「复试」在业务上说不通。</p>
 */
@Data
public class RecruitStageRequest {

    /** 目标阶段 */
    @NotNull(message = "目标阶段不能为空")
    private Integer stage;

    /** 淘汰原因类型（阶段=已淘汰时必填） */
    private Integer rejectType;

    /** 淘汰原因 */
    private String rejectReason;

    /** 是否回退（需 HR 权限 + 理由） */
    private Boolean rollback;

    /** 回退理由 */
    private String reason;
}
