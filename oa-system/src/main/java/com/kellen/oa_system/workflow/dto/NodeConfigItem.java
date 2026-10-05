package com.kellen.oa_system.workflow.dto;

import lombok.Data;

/**
 * 节点审批人规则项，对应 oa_flow_definition.node_config 的 JSON 数组元素。
 *
 * <p>assigneeType：0未配置 1指定人员 2指定角色 3部门主管；
 * assigneeValue：指定人员=用户 id、指定角色=角色 id，均支持逗号分隔多个；部门主管留空。</p>
 */
@Data
public class NodeConfigItem {

    private String nodeId;

    private String nodeName;

    private Integer assigneeType;

    private String assigneeValue;
}
