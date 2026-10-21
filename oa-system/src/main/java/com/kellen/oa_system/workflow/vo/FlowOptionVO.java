package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 可发起的流程模板选项（只读、最小字段集）。
 *
 * <p><b>存在的理由</b>：员工发起审批时需要「挑一个流程模板」，这个动作面向<b>全体员工</b>；
 * 而模板管理接口 {@code GET /workflow/flow-definition/page} 的鉴权码是
 * {@code system:flow:query}（属管理职能）。二者用户群体不匹配，导致专员/员工角色
 * 一打开「发起申请」就 403、模板下拉为空、根本无法发起。</p>
 *
 * <p><b>字段最小化</b>：只暴露渲染动态表单与下拉所需的字段，<b>不返回</b>
 * {@code bpmnKey}、{@code deploymentId}、{@code nodeConfig}、{@code remark}、
 * {@code createTime}/{@code updateTime} —— 这些属于模板管理内部信息，业务侧无需也不应看到。</p>
 */
@Data
public class FlowOptionVO {

    /** 模板ID，仅用于前端列表 key */
    private Long id;

    /** 流程编码：发起审批的业务标识 */
    private String flowCode;

    /** 流程名称：下拉展示 */
    private String flowName;

    /** 分类（对应 flow_category 字典） */
    private String category;

    /** 流程图标（Lucide 图标名）；发起页卡片直接渲染 */
    private String icon;

    /**
     * 表单字段配置 JSON 字符串。
     *
     * <p>发起申请页据此动态渲染表单控件，<b>属于业务必需</b>，不能省略。</p>
     */
    private String formFields;
}
