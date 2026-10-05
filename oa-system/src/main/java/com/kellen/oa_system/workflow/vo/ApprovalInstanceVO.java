package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 审批单视图。statusName / applicantName 由后端翻译，前端直接展示。
 */
@Data
public class ApprovalInstanceVO {

    private Long id;
    private String flowCode;
    private String flowName;
    private String title;
    private Long applicantId;
    private String applicantName;
    private Integer status;
    private String statusName;
    private String currentNode;
    private String formData;
    private String createTime;
}
