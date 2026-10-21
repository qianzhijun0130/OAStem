package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 待办视图。currentNode 取自 Flowable 任务定义 key 或节点名。
 */
@Data
public class ApprovalTodoVO {

    private String taskId;
    private Long instanceId;
    private String title;
    private String flowName;
    private String applicantName;
    private String currentNode;
    private String createTime;

    /** 申请人所属部门（oa_department.dept_name），查不到为 null */
    private String deptName;

    /** 申请人所属公司（部门 company_id → oa_company.company_name），查不到为 null */
    private String companyName;
}
