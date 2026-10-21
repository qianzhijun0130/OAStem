package com.kellen.oa_system.workflow.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分支条件可用变量的目录。
 *
 * <p>集中定义两件事，供「设计器变量下拉」与「保存时变量合法性校验」共用同一份口径：
 * <ol>
 *   <li><b>系统内置变量</b>：由 {@code ApprovalServiceImpl} 在发起 / 审批时写入流程变量，
 *       与业务表单无关；</li>
 *   <li><b>FormCreate 控件类型 → 取值类型</b>的映射：决定前端比较值控件形态
 *       （数字输入框 / 日期选择器 / 开关 / 文本框）与后端字面量生成方式。</li>
 * </ol>
 * 定义放工具类而非散落在前后端两边，是为了避免「前端能选、后端校验拒收」的割裂。</p>
 */
public final class FlowVariables {

    /**
     * 条件取值类型。
     *
     * <p>{@code DATE} 按 ISO 字符串字典序比较即可等价于按时间比较，
     * 故后端仍走字符串字面量 + {@code compareTo}，无需专门的日期运算。</p>
     */
    public enum ValueType {
        NUMBER("number", "数字"),
        STRING("string", "文本"),
        DATE("date", "日期"),
        BOOLEAN("boolean", "布尔");

        private final String code;
        private final String label;

        ValueType(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        /** 按编码取类型；无法识别时回退为文本（最保守的取值方式） */
        public static ValueType ofCode(String code) {
            if (code == null) {
                return STRING;
            }
            for (ValueType type : values()) {
                if (type.code.equalsIgnoreCase(code.trim())) {
                    return type;
                }
            }
            return STRING;
        }
    }

    /** 一个系统内置变量的定义 */
    @Data
    @AllArgsConstructor
    public static class SystemVar {
        private String key;
        private String label;
        private ValueType type;
    }

    /**
     * 系统内置变量目录。
     *
     * <p>{@code applicantRoleCodes} 用逗号拼接多个角色编码（一个用户可有多个角色），
     * 条件里用「包含」判断，如 {@code applicantRoleCodes 包含 HR}。</p>
     *
     * <p>{@code lastOutcome} / {@code lastApproved} 与 {@code outcome} / {@code approved} 同值：
     * 从紧随其后的网关视角看，「上节点审批结果」就是刚刚写入的结果。单独提供别名，
     * 只为让设计器下拉里能出现语义明确的「上节点审批结果」选项。</p>
     */
    public static final List<SystemVar> SYSTEM_VARS = List.of(
            new SystemVar("applicantId", "发起人ID", ValueType.NUMBER),
            new SystemVar("applicantDeptId", "发起人部门ID", ValueType.NUMBER),
            new SystemVar("applicantDeptName", "发起人部门名称", ValueType.STRING),
            new SystemVar("applicantRoleCodes", "发起人角色编码（多个用逗号拼接，建议用「包含」判断）", ValueType.STRING),
            new SystemVar("outcome", "本节点审批结果（approve / reject）", ValueType.STRING),
            new SystemVar("approved", "本节点是否同意", ValueType.BOOLEAN),
            new SystemVar("lastOutcome", "上节点审批结果（approve / reject）", ValueType.STRING),
            new SystemVar("lastApproved", "上节点是否同意", ValueType.BOOLEAN),
            new SystemVar("title", "申请标题", ValueType.STRING)
    );

    private static final Set<String> SYSTEM_VAR_KEYS = SYSTEM_VARS.stream()
            .map(SystemVar::getKey)
            .collect(Collectors.toUnmodifiableSet());

    /** FormCreate 控件类型 → 取值类型；未列出的控件一律按文本处理 */
    private static final Map<String, ValueType> CONTROL_TYPE_MAP = Map.ofEntries(
            Map.entry("input", ValueType.STRING),
            Map.entry("password", ValueType.STRING),
            Map.entry("textarea", ValueType.STRING),
            Map.entry("inputnumber", ValueType.NUMBER),
            Map.entry("number", ValueType.NUMBER),
            Map.entry("slider", ValueType.NUMBER),
            Map.entry("rate", ValueType.NUMBER),
            Map.entry("datepicker", ValueType.DATE),
            Map.entry("date", ValueType.DATE),
            Map.entry("timepicker", ValueType.DATE),
            Map.entry("select", ValueType.STRING),
            Map.entry("radio", ValueType.STRING),
            Map.entry("cascader", ValueType.STRING),
            Map.entry("checkbox", ValueType.STRING),
            Map.entry("switch", ValueType.BOOLEAN)
    );

    private FlowVariables() {
    }

    /** 是否为系统内置变量（与表单字段区分：系统变量无需表单绑定即可使用） */
    public static boolean isSystemVar(String key) {
        return key != null && SYSTEM_VAR_KEYS.contains(key.trim());
    }

    /** FormCreate 控件类型 → 条件取值类型 */
    public static ValueType typeOfFormControl(String controlType) {
        if (controlType == null || controlType.isBlank()) {
            return ValueType.STRING;
        }
        return CONTROL_TYPE_MAP.getOrDefault(controlType.trim().toLowerCase(Locale.ROOT), ValueType.STRING);
    }
}
