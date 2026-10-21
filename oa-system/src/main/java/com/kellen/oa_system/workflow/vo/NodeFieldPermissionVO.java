package com.kellen.oa_system.workflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 「节点表单字段权限」视图：某节点在某表单上的逐字段读写权限。
 *
 * <p>{@code fields} 返回的是<b>该表单的全部字段</b>，未显式配置的字段也会出现，
 * 其 {@code permission} 为 1（只读）。这样前端可直接渲染整张表，
 * 不必在前端补齐默认值，也避免「配置缺失」被误当成「字段不存在」。</p>
 */
@Data
public class NodeFieldPermissionVO {

    private Long flowDefId;

    private String nodeKey;

    private String nodeName;

    private Long formId;

    private String formCode;

    private String formName;

    private List<FieldItem> fields;

    /**
     * 单个字段的权限。
     */
    @Data
    public static class FieldItem {

        /** FormCreate 规则里的 prop */
        private String prop;

        /** 字段显示名（取自表单规则） */
        private String label;

        /** 权限：1只读 2可编辑 3隐藏 */
        private Integer permission;

        /** 是否为显式配置（false 表示走默认只读） */
        private Boolean configured;
    }
}
