package com.kellen.oa_system.workflow.dto;

import lombok.Data;

/**
 * 节点审批人规则项，对应 oa_flow_definition.node_config 的 JSON 数组元素。
 *
 * <p>assigneeType：0未配置 1指定人员 2指定角色 3部门主管（发起人部门主管）
 * 4申请人本人 5指定部门主管。<br>
 * assigneeValue：指定人员=用户 id、指定角色=角色 id，均支持逗号分隔多个；
 * 部门主管=审批层级（留空按 1 即直属主管，2=上一级主管，依此类推）；
 * 指定部门主管=部门 id；申请人本人留空。</p>
 */
@Data
public class NodeConfigItem {

    private String nodeId;

    private String nodeName;

    private Integer assigneeType;

    private String assigneeValue;

    /**
     * 节点超时（小时，阶段四）。
     *
     * <p>为空或 ≤0 时回落到全局 {@code approval.timeout.hours}；全局也为 0 表示不启用超时。
     * 建任务时据此写 Flowable 的 dueDate 与 {@code oa_approval_instance.due_time}。</p>
     */
    private Integer timeoutHours;

    /**
     * 到期自动动作（阶段四）。
     *
     * <p>0 或空 = 仅标记（默认）；1 = 提醒（写一条超时流转记录）；2 = 自动驳回。
     * 只有配置了 {@code timeoutHours > 0} 的节点才会到期，动作才有意义。</p>
     */
    private Integer timeoutAction;
}
