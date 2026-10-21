package com.kellen.oa_system.system.vo;

import lombok.Data;

/**
 * 表单列表 / 详情返回对象。
 *
 * <p>ruleJson 与 fieldSchema 原样返回给前端：设计器与运行时渲染都需要完整规则，
 * 不做字段裁剪。</p>
 */
@Data
public class FormVO {

    private Long id;

    private String formCode;

    private String formName;

    private String category;

    /** FormCreate 规则 JSON（option 数组） */
    private String ruleJson;

    /** 字段清单 JSON */
    private String fieldSchema;

    private Integer status;

    private String remark;

    private String createTime;

    private String updateTime;

    /** 被多少个流程节点引用（列表页展示，避免用户误删仍在用的表单） */
    private Integer bindCount;
}
