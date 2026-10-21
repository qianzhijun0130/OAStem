package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 流程节点→表单 的映射项，供「发起申请」「流程模板」页使用。
 *
 * <p>发起页只取 {@code isPrimary=1} 的那条渲染「申请内容」区块；
 * 流程模板页列出全部绑定供管理。</p>
 */
@Data
public class NodeFormVO {

    /**
     * 绑定记录主键（oa_flow_node_form.id）。
     *
     * <p>流程模板页的「解除绑定」按 id 调用删除接口，故必须返回；
     * 此前缺失导致前端只能退化为「按 flowDefId+nodeKey 猜」，无法精确解绑。</p>
     */
    private Long id;

    private Long flowDefId;

    /** 流程编码，便于前端按 flowCode 定位（flowDefId 在配置变更后可能变化） */
    private String flowCode;

    private String flowName;

    private String nodeKey;

    private String nodeName;

    private Long formId;

    private String formCode;

    private String formName;

    /** FormCreate 规则 JSON */
    private String ruleJson;

    private Integer isPrimary;

    private Integer sort;
}
