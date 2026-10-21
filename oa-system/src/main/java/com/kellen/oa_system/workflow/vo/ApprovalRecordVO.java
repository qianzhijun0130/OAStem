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

    /** 单据发起人所属部门（不是处理人的部门），查不到为 null */
    private String deptName;

    /** 单据发起人所属公司，查不到为 null */
    private String companyName;
}
