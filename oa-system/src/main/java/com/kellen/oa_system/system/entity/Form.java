package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码表单定义实体，对应表 oa_form。
 *
 * <p>一条记录 = 一个可复用的表单模板。ruleJson 存 FormCreate 渲染器规则（option 数组），
 * 由「表单管理」页的可视化设计器产出；fieldSchema 是从 ruleJson 派生的字段清单，
 * 供后端复核提交数据、前端做必填预校验与详情页表头回显。</p>
 *
 * <p>之所以独立成表而不塞进 {@code oa_flow_definition.form_fields}：表单需要被多个流程/节点
 * 复用，独立成表才能避免同一份规则在多个流程里各存一份、改一处漏三处。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_form")
public class Form extends AuditEntity {

    /** 表单编码（如 leave_apply），全局唯一；逻辑删除后原编码可立即复用 */
    private String formCode;

    /** 表单名称 */
    private String formName;

    /** 分类 */
    private String category;

    /** FormCreate 规则 JSON（option 数组），非空 */
    private String ruleJson;

    /** 字段清单 JSON（从 ruleJson 派生），可空（由后端在保存时补齐） */
    private String fieldSchema;

    /** 1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
