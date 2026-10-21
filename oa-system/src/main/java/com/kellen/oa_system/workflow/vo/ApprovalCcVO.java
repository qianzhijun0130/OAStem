package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 抄送任务视图：被抄送人视角看到的单据概要 + 抄送元信息。
 */
@Data
public class ApprovalCcVO {

    /** 抄送记录 id（标记已读用） */
    private Long id;

    private Long instanceId;

    private String title;

    private String flowName;

    private String applicantName;

    /** 申请人所属部门 */
    private String deptName;

    /** 申请人所属公司 */
    private String companyName;

    private String currentNode;

    private Integer status;

    private String statusName;

    /** 抄送人姓名 */
    private String ccByName;

    /** 抄送说明 */
    private String remark;

    /** 0 未读、1 已读 */
    private Integer readFlag;

    private String readTime;

    private String createTime;
}
