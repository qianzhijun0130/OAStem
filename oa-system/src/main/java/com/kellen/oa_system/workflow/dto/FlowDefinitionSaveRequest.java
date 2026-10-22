package com.kellen.oa_system.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程模板保存请求。
 *
 * <p>bpmnXml 由前端 bpmn-js modeler 产出；首次保存或需要调整流程结构时必填，
 * 后端据此部署到 Flowable 并把 deploymentId / bpmnKey 落库。formFields / nodeConfig
 * 为 JSON 字符串，驱动前端动态表单与运行时审批人解析。</p>
 */
@Data
public class FlowDefinitionSaveRequest {

    /** 更新时传原 id */
    private Long id;

    @NotBlank(message = "流程编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "流程编码只能包含字母、数字、下划线")
    private String flowCode;

    @NotBlank(message = "流程名称不能为空")
    @Size(max = 100, message = "流程名称长度不能超过 100")
    private String flowName;

    @Size(max = 32, message = "分类长度不能超过 32")
    private String category;

    /** 流程图标（Lucide 图标名，选填） */
    private String icon;

    /** 前端 modeler 产出的 BPMN XML（调整流程结构时必填） */
    private String bpmnXml;

    /** 表单字段配置 JSON */
    private String formFields;

    /** 节点审批人规则 JSON */
    private String nodeConfig;

    /**
     * 网关分支条件配置 JSON。
     *
     * <p>保存时与 bpmnXml 一同提交：后端据此在部署前注入 conditionExpression。</p>
     *
     * <p><b>清空语义（D1 修复后）</b>：传 null/空串表示清空全部可视化条件。清空时后端会
     * 同时删掉「上次由本配置注入到 BPMN 上的 conditionExpression」，避免出现「界面清空了、
     * 运行仍按旧条件分流」。两点边界：</p>
     * <ul>
     *   <li>不带 bpmnXml 时只清库里的配置，引擎侧旧版本仍按原条件运行，待下次重新部署生效；</li>
     *   <li>带着 bpmnXml 且画布上仍是从排他网关分出多条线时，清空条件会让「非默认分支既无条件
     *       也非默认」→ 部署被结构校验拒绝（3005）。这种图形本身无法运行，需先在画布上改掉网关，
     *       而不是期望后端猜一个路由。</li>
     * </ul>
     */
    private String flowConfig;

    /** 1启用 0停用，缺省 1 */
    @Min(value = 0, message = "状态只能是 0停用 1启用")
    @Max(value = 1, message = "状态只能是 0停用 1启用")
    private Integer status = 1;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
