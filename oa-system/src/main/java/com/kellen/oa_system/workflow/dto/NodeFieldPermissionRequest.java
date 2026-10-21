package com.kellen.oa_system.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.util.List;

/**
 * 保存「节点表单字段权限」入参。
 *
 * <p>采用<b>整节点全量提交</b>而非逐条增删改：设计器里一次调整通常涉及多个字段，
 * 全量提交可让前端逻辑更简单，也避免「删除某条权限」这类操作需要单独接口。</p>
 */
@Data
public class NodeFieldPermissionRequest {

    /** 流程模板 id */
    @NotNull(message = "流程模板不能为空")
    private Long flowDefId;

    /** BPMN taskDefinitionKey；发起环节为 init */
    @NotBlank(message = "节点标识不能为空")
    @Size(max = 100, message = "节点标识长度不能超过 100")
    private String nodeKey;

    /** 节点名称（冗余，便于列表展示） */
    @Size(max = 100, message = "节点名称长度不能超过 100")
    private String nodeName;

    /** 表单 id */
    @NotNull(message = "表单不能为空")
    private Long formId;

    /**
     * 字段权限明细。
     *
     * <p><b>{@code @Valid} 不能少</b>：Jakarta Validation 默认不递归校验集合元素，
     * 只在 Controller 上加 {@code @Valid} 时，{@code Item} 上的
     * {@code @NotBlank}/{@code @Min}/{@code @Max} 全部不会生效 ——
     * 实测 permission=9 这类越界值会被静默接受并落库。</p>
     */
    @NotNull(message = "字段权限明细不能为空")
    @Valid
    private List<Item> fields;

    /**
     * 单个字段的权限配置。
     */
    @Data
    public static class Item {

        /** FormCreate 规则里的 prop */
        @NotBlank(message = "字段标识不能为空")
        @Size(max = 100, message = "字段标识长度不能超过 100")
        private String prop;

        /** 字段显示名（冗余，便于列表展示） */
        @Size(max = 100, message = "字段显示名长度不能超过 100")
        private String fieldLabel;

        /** 权限：1只读 2可编辑 3隐藏 */
        @NotNull(message = "字段权限不能为空")
        @Min(value = 1, message = "字段权限只能为 1只读 2可编辑 3隐藏")
        @Max(value = 3, message = "字段权限只能为 1只读 2可编辑 3隐藏")
        private Integer permission;
    }
}
