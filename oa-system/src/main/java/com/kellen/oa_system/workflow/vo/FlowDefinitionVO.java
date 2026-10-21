package com.kellen.oa_system.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程模板视图。formFields / nodeConfig / flowConfig 透传 JSON 字符串，由前端解析渲染。
 */
@Data
public class FlowDefinitionVO {

    private Long id;
    private String flowCode;
    private String flowName;
    private String category;

    private String icon;
    private String bpmnKey;
    private String deploymentId;
    private String formFields;
    private String nodeConfig;
    /** 网关分支条件配置 JSON（结构化条件的事实源，前端「分支条件」标签页回显用） */
    private String flowConfig;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
