package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 审批单视图。statusName / applicantName 由后端翻译，前端直接展示。
 */
@Data
public class ApprovalInstanceVO {

    private Long id;
    private String flowCode;

    /** 单据编号（系统生成） */
    private String billCode;
    private String flowName;
    private String title;
    private Long applicantId;
    private String applicantName;
    private Integer status;
    private String statusName;
    private String currentNode;
    private String formData;
    private String createTime;

    /** 发起人所属部门 */
    private String deptName;

    /** 发起人所属公司 */
    private String companyName;

    /** 当前节点应处理截止时间（yyyy-MM-dd HH:mm:ss）；未配置超时时为 null */
    private String dueTime;

    /** 是否已逾期：审批中且 dueTime 已过。由后端计算，避免前后端时钟不一致 */
    private Boolean overdue;
}
