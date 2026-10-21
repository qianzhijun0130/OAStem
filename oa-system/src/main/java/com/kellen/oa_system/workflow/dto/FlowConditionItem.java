package com.kellen.oa_system.workflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 网关分支条件配置项，对应 oa_flow_definition.flow_config 的 JSON 数组元素。
 *
 * <p><b>为什么按「连线」而不是按「节点」配</b>：BPMN 2.0 的条件表达式挂在
 * {@code <sequenceFlow>} 上（{@code conditionExpression} 子元素），网关本身只持有一个
 * {@code default} 属性指向兜底连线。按连线配置与引擎语义一一对应，注入时无需做任何映射。</p>
 *
 * <p><b>两套配置模式</b>（{@code mode}）：
 * <ul>
 *   <li>{@code visual}：可视化条件组（{@link ConditionGroup}），支持 AND/OR 嵌套，
 *       由后端递归生成 JUEL 表达式；</li>
 *   <li>{@code raw}：直接手写 EL 表达式（{@code expression}），原样注入。
 *       仅对持有 {@code system:flow:condition-raw} 权限的角色开放（前端控制入口）。</li>
 * </ul>
 * 二者互斥：切到 raw 会作废可视化配置，反之亦然（由前端在切换时清空另一侧的旧值）。</p>
 *
 * <p><b>存量兼容</b>：早期版本的配置是扁平结构（{@code logic} + {@code conditions}）。
 * 这里保留这两个字段并经由 {@link #normalizedCondition()} 自动升级为「根组 + 条件」，
 * 历史模板无需人工迁移。</p>
 */
@Data
public class FlowConditionItem {

    /** 条件配置模式：visual=可视化条件组（缺省），raw=手写 EL 表达式 */
    private String mode;

    /** BPMN sequenceFlow 的 id（画布连线的唯一标识） */
    private String flowId;

    /** 源网关 id（conditionExpression 的宿主节点，注入时校验其确为排他网关） */
    private String gatewayId;

    /** 源网关名称（冗余，便于配置页展示与错误提示） */
    private String gatewayName;

    /** 目标节点 id（冗余，便于配置页展示，也用于分支命中日志反推） */
    private String targetNodeId;

    /** 目标节点名称（冗余，便于配置页展示） */
    private String targetNodeName;

    /** 分支名称（业务语义，如「大额审批」，用于配置页与命中日志展示） */
    private String name;

    /** 分支描述（配置说明，不参与运行） */
    private String description;

    /** 是否默认（兜底）分支：注入时写入网关的 default 属性 */
    private Boolean isDefault;

    /** 可视化条件树（mode=visual 时生效） */
    private ConditionGroup condition;

    /** 手写的 EL 表达式原文（mode=raw 时生效） */
    private String expression;

    /** 【存量字段】多条件组合方式：and / or。新配置请用 {@code condition.logic} */
    @Deprecated
    private String logic;

    /** 【存量字段】扁平条件列表。新配置请用 {@code condition.rules} */
    @Deprecated
    private List<ConditionRule> conditions;

    /**
     * 条件组：可递归嵌套，实现「(A 且 B) 或 C」这类复合规则。
     *
     * <p>{@code logic} 决定本组内部各条目（条件 + 子组）的连接方式；
     * 渲染时子组会带上括号，保证运算优先级与配置意图一致。</p>
     */
    @Data
    public static class ConditionGroup {

        /** 组内连接逻辑：and（缺省）/ or */
        private String logic;

        /** 本组直接持有的条件 */
        private List<ConditionRule> rules;

        /** 子条件组（产品层限制 1 层，即「根组 → 子组 → 条件」，由后端校验兜底） */
        private List<ConditionGroup> groups;
    }

    /**
     * 单条条件规则。
     *
     * <p>field 必须是合法标识符（字母/下划线/$开头，仅含字母数字下划线$）——
     * 它会被原样拼进 JUEL 表达式，非法标识符会让 Flowable 在运行期才抛解析异常，
     * 那时流程已经发起，成本远高于保存时拒绝。</p>
     */
    @Data
    public static class ConditionRule {

        /** 变量名（申请表单字段 prop 或系统内置变量，发起时已平铺为流程变量） */
        private String field;

        /** 运算符：eq ne gt ge lt le contains notContains empty notEmpty */
        private String op;

        /** 值类型：number 裸值比较 / string 加单引号字面量 / boolean 布尔字面量 */
        private String valueType;

        /** 比较值；empty / notEmpty 运算符下无需值 */
        private String value;
    }

    /** 是否手写表达式模式 */
    public boolean isRawMode() {
        return "raw".equalsIgnoreCase(mode);
    }

    /**
     * 取规范化后的可视化条件树；存量扁平结构自动升级为「根组 + 条件」。
     *
     * @return 条件树；未配置任何条件时返回 null
     */
    public ConditionGroup normalizedCondition() {
        if (condition != null) {
            return condition;
        }
        // 存量扁平结构：logic + conditions → 根组
        List<ConditionRule> legacy = conditions;
        if (legacy == null || legacy.isEmpty()) {
            return null;
        }
        ConditionGroup root = new ConditionGroup();
        root.setLogic(logic);
        root.setRules(legacy);
        root.setGroups(new ArrayList<>());
        return root;
    }

    /**
     * 该分支条件引用到的全部变量名，用于分支命中日志挑取变量快照。
     *
     * <p>手写表达式模式下不做解析（正则抽变量名会误伤字符串字面量），
     * 日志退化为只记分支名与表达式全文。</p>
     *
     * @return 变量名集合（去重、保持出现顺序）
     */
    public Set<String> referencedFields() {
        Set<String> fields = new LinkedHashSet<>();
        ConditionGroup root = normalizedCondition();
        if (root == null) {
            return fields;
        }
        collectFields(root, fields);
        return fields;
    }

    private static void collectFields(ConditionGroup group, Set<String> sink) {
        if (group == null) {
            return;
        }
        if (group.getRules() != null) {
            for (ConditionRule rule : group.getRules()) {
                if (rule != null && rule.getField() != null && !rule.getField().isBlank()) {
                    sink.add(rule.getField().trim());
                }
            }
        }
        if (group.getGroups() != null) {
            for (ConditionGroup sub : group.getGroups()) {
                collectFields(sub, sink);
            }
        }
    }
}
