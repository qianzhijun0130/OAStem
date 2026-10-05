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
}
