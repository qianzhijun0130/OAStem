package com.kellen.oa_system.workflow.dto;

import lombok.Data;

@Data
public class FlowDefinitionPageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    private String flowName;

    /** 精确匹配流程编码（审批详情按 flowCode 反查模板时用） */
    private String flowCode;

    /** 1启用 0停用，传 null 查全部 */
    private Integer status;

    /** 分类，精确匹配；取值来自 flow_category 字典 */
    private String category;
}
