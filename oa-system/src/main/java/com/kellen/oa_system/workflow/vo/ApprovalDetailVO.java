package com.kellen.oa_system.workflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 审批详情视图：申请单 + 历史时间轴 + 当前待办（流程结束后 currentTask 为 null）。
 */
@Data
public class ApprovalDetailVO {

    private ApprovalInstanceVO instance;
    private List<ApprovalRecordVO> records;
    private ApprovalTodoVO currentTask;

    /**
     * 当前用户是否可对本单据的当前节点执行审批（同意 / 驳回）。
     *
     * <p>刻意与 {@code currentTask} 解耦：{@code currentTask} 表示「流程当前停在哪个节点」（申请人、
     * 实例总览用户都能看到），而 {@code canApprove} 才表示「我能不能审批」。前端据此决定是否显示审批区，
     * 避免出现「看得见同意/驳回按钮、点了却报 3104（非候选人）」。</p>
     *
     * <p>为真的条件：当前节点存在，且当前用户是<b>候选人</b>或是<b>超级管理员</b>（超管可处理任意节点）。</p>
     */
    private Boolean canApprove;

    /**
     * 当前流程实例对应的 FormCreate 表单规则 JSON。
     *
     * <p><b>需求来源</b>：审批人打开待办时要看到申请人填写的内容，但不应自己再配一份表单 ——
     * 所有节点复用发起时那份。故此处直接下发<b>发起环节的主表单规则</b>，
     * 前端用 {@code FormRender} 只读渲染即可，无需再调一次表单接口。</p>
     *
     * <p>未绑定表单时为 null，前端降级为键值对展示历史 JSON。</p>
     */
    private String formRuleJson;

    /**
     * 当前节点下允许编辑的字段标识集合（FormCreate rule 的 prop）。
     *
     * <p>未配置字段权限时为空集合 —— 即<b>全部只读</b>，与「默认只读」约定一致。
     * 典型场景：销假节点把「销假日期」「实际返岗日期」设为可编辑，
     * 员工在待办里填这两项，保存后合并回同一份 {@code form_data}。</p>
     */
    private List<String> editableProps;
}
