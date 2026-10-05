package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 审批操作记录视图，用于「我的已办」与详情时间轴。
 */
@Data
public class ApprovalRecordVO {

    private Long instanceId;
    private String title;
    private String flowName;
    private String nodeName;
    private String handlerName;
    private String action;
    private String actionName;
    private String opinion;
    private String createTime;
}
