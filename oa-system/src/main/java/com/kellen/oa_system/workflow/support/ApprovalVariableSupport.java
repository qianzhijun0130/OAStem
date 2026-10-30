package com.kellen.oa_system.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程变量构造：把表单字段与发起人上下文平铺成 BPMN 条件表达式能引用的变量。
 *
 * <p>两条铁律：</p>
 * <ol>
 *   <li><b>表单字段不得覆盖内建变量</b>（{@code formData}/{@code applicantId}/{@code title} 等），
 *       否则标题错乱、条件表达式引用到错误值；</li>
 *   <li><b>查不到部门/角色时写空串而不是不写</b>：JUEL 里引用未定义变量会取值失败，
 *       空串至少能让「为空 / 不为空」这类判断稳定成立。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalVariableSupport {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;

    /**
     * 把申请表单的 JSON 平铺为「字段名 → 值」的可执行变量表。
     *
     * <p><b>用途</b>：BPMN 的条件表达式（如 {@code ${days > 3}}）只能引用流程变量，
     * 而表单数据原先整体存在 {@code formData} 字符串里。这里把每个顶层字段提升为独立变量，
     * 使设计器可以直接引用业务字段。</p>
     *
     * <p><b>类型处理</b>：Flowable 的表达式求值对数值运算敏感，故尽量还原为
     * {@code Integer}/{@code Double}/{@code Boolean}；无法解析为数字的字符串原样保留，
     * 可用于 {@code ${status == '已通过'}} 这类字符串比较。</p>
     *
     * <p><b>健壮性</b>：非法 JSON 或非对象结构一律返回空表 —— 表单是可选的，
     * 不应因为表单格式问题导致流程无法发起。</p>
     */
    public Map<String, Object> flattenFormData(String formDataJson) {
        Map<String, Object> flat = new HashMap<>();
        if (formDataJson == null || formDataJson.isBlank()) {
            return flat;
        }
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(formDataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("申请表单 JSON 解析失败，流程变量仅注入基础字段: {}", formDataJson);
            return flat;
        }
        if (parsed == null) {
            return flat;
        }
        parsed.forEach((key, value) -> {
            if (!isSafeVariableName(key) || ApprovalConstants.RESERVED_VARS.contains(key)) {
                return;
            }
            flat.put(key, normalizeVariableValue(value));
        });
        return flat;
    }

    /**
     * 构造发起人维度的系统内置变量，供分支条件引用。
     *
     * <p>三个变量各自解决一类路由需求：</p>
     * <ul>
     *   <li>{@code applicantDeptId}/{@code applicantDeptName}：按部门分流（如「技术部走 A 线」）；</li>
     *   <li>{@code applicantRoleCodes}：按角色分流。一个用户可有多个角色，故用逗号拼接
     *       的编码串，条件里用「包含」判断（如 {@code applicantRoleCodes 包含 HR}）。</li>
     * </ul>
     */
    public Map<String, Object> buildApplicantContextVars(Long applicantId) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("applicantDeptId", 0L);
        vars.put("applicantDeptName", "");
        vars.put("applicantRoleCodes", "");
        if (applicantId == null) {
            return vars;
        }
        User applicant = userMapper.selectById(applicantId);
        if (applicant != null && applicant.getDeptId() != null) {
            vars.put("applicantDeptId", applicant.getDeptId());
            Department dept = departmentMapper.selectById(applicant.getDeptId());
            if (dept != null && dept.getDeptName() != null) {
                vars.put("applicantDeptName", dept.getDeptName());
            }
        }
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, applicantId)).stream()
                .map(UserRole::getRoleId).distinct().toList();
        if (!roleIds.isEmpty()) {
            List<String> codes = roleMapper.selectBatchIds(roleIds).stream()
                    .map(r -> r.getRoleCode() == null ? "" : r.getRoleCode())
                    .filter(c -> !c.isBlank())
                    .distinct()
                    .toList();
            if (!codes.isEmpty()) {
                vars.put("applicantRoleCodes", String.join(",", codes));
            }
        }
        return vars;
    }

    /** Flowable 表达式只接受合法标识符，数字开头或含空格的键不可作为变量名 */
    private static boolean isSafeVariableName(String name) {
        return name != null && !name.isBlank() && Character.isJavaIdentifierStart(name.charAt(0));
    }

    /** 尽力还原为数值/布尔，便于表达式做比较与算术；其余原样保留 */
    private Object normalizeVariableValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return text;
            }
            try {
                // 优先整数，避免 3 被解析成 3.0 影响展示与比较
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException ignored) {
                // 继续尝试小数
            }
            try {
                return Double.valueOf(trimmed);
            } catch (NumberFormatException ignored) {
                return text;
            }
        }
        // 嵌套对象/数组等复杂值不提升为变量，保留在 formData 里由应用层自行解析
        return value instanceof String ? value : String.valueOf(value);
    }
}
