package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 抄送请求：把某张审批单抄送给若干人（仅告知，不产生待办）。
 */
@Data
public class ApprovalCcRequest {

    @NotNull(message = "审批单ID不能为空")
    private Long instanceId;

    @NotEmpty(message = "请至少选择一个抄送人")
    private List<Long> userIds;

    @Size(max = 200, message = "抄送说明不能超过 200 字")
    private String remark;
}
