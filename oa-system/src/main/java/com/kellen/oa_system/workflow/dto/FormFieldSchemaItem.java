package com.kellen.oa_system.workflow.dto;

import lombok.Data;

/**
 * oa_form.field_schema 的单个元素（表单字段清单）。
 *
 * <p>与 {@code FormServiceImpl#deriveFieldSchema} 写出的结构一一对应：
 * {@code {"prop":"days","label":"天数","type":"inputNumber","required":true}}。
 * 反序列化只为读取，不做校验——这份数据是系统自己写出的，
 * 若将来有历史脏数据，取不到字段时按默认值回退即可，不该让设计器打不开。</p>
 */
@Data
public class FormFieldSchemaItem {

    /** 字段键（FormCreate 的 fieldKey），同时作为流程变量名 */
    private String prop;

    /** 中文标签 */
    private String label;

    /** FormCreate 控件类型，如 input / inputNumber / datePicker / select / switch */
    private String type;

    /** 是否必填（仅展示用，不参与条件判断） */
    private Boolean required;
}
