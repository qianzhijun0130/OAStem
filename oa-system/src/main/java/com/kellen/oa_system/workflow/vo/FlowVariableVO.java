package com.kellen.oa_system.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分支条件可用变量清单。
 *
 * <p>分两组下发，前端下拉按组渲染：
 * <ul>
 *   <li>{@code formFields}：发起环节主表单的字段（FormCreate 的 fieldKey），带取值类型；</li>
 *   <li>{@code systemVars}：系统内置变量，与表单无关，任何流程都可用。</li>
 * </ul>
 * 未绑定表单的流程 {@code formFields} 为空数组——此时只能用系统变量配条件。</p>
 */
@Data
public class FlowVariableVO {

    private List<VarItem> formFields;

    private List<VarItem> systemVars;

    /** 一个可选变量 */
    @Data
    @AllArgsConstructor
    public static class VarItem {

        /** 变量名（直接写进 JUEL 表达式，必须是合法标识符） */
        private String key;

        /** 展示名（表单字段取中文标签，系统变量取说明文字） */
        private String label;

        /** 取值类型：number / string / date / boolean，决定比较值控件形态 */
        private String type;
    }
}
