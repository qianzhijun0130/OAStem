package com.kellen.oa_system.workflow.support;

import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.service.NodeFieldPermissionService;
import com.kellen.oa_system.workflow.service.NodeFormService;
import com.kellen.oa_system.workflow.vo.NodeFormVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批表单：节点可编辑字段、主表单规则下发、审批时的字段补丁合并。
 *
 * <p>核心是<b>「审批人改不了申请人填的内容」</b>：补丁里混入未授权字段会被静默丢弃并记日志，
 * 而不是报错——前端可能因状态过期提交了旧的权限配置，用报错阻断审批会很难排查。
 * 这是最后一道防线，别为了「报错更明确」而改成抛异常。</p>
 *
 * <p>表单相关能力一律「取不到就降级」：字段权限取不到 → 全部只读；
 * 主表单取不到 → 前端降级为键值对展示。审批流程本身不能被表单配置的脏数据卡住。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalFormSupport {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalInstanceSupport instanceSupport;
    private final FlowableEngineService engine;
    private final NodeFormService nodeFormService;
    private final NodeFieldPermissionService fieldPermissionService;
    private final ObjectMapper objectMapper;

    /**
     * 把本节点可编辑字段的补丁合并进 {@code form_data}。
     *
     * <p>同时同步更新流程变量，否则条件表达式读到的仍是旧值。</p>
     */
    public void applyFormDataPatch(ApprovalInstance inst, Task task, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        FlowDefinition fd = instanceSupport.fdOf(inst);
        if (fd == null) {
            return;
        }
        Long formId = primaryFormId(fd.getFlowCode());
        List<String> editable = fieldPermissionService.listEditableProps(
                fd.getId(), task.getTaskDefinitionKey(), formId);
        if (editable.isEmpty()) {
            log.warn("节点 {} 提交了表单补丁但无可编辑字段，已忽略: {}",
                    task.getTaskDefinitionKey(), patch.keySet());
            return;
        }
        Set<String> allow = new HashSet<>(editable);

        // 读出原数据 → 只挑授权字段合并 → 整体覆盖
        Map<String, Object> merged = new LinkedHashMap<>(parseFormData(inst.getFormData()));
        Map<String, Object> accepted = new LinkedHashMap<>();
        patch.forEach((k, v) -> {
            if (allow.contains(k)) {
                merged.put(k, v);
                accepted.put(k, v);
            }
        });
        if (accepted.isEmpty()) {
            log.warn("节点 {} 提交的字段均未授权可编辑，已忽略: {}", task.getTaskDefinitionKey(), patch.keySet());
            return;
        }
        if (patch.size() > accepted.size()) {
            log.warn("节点 {} 提交了未授权字段，已剔除: {}", task.getTaskDefinitionKey(),
                    patch.keySet().stream().filter(k -> !allow.contains(k)).toList());
        }

        String mergedJson = writeJson(merged);
        // 部分实体：只 set 变更字段，保证 update_time 走 MySQL ON UPDATE
        ApprovalInstance patchInst = new ApprovalInstance();
        patchInst.setId(inst.getId());
        patchInst.setFormData(mergedJson);
        instanceMapper.updateById(patchInst);
        inst.setFormData(mergedJson);

        // 同步流程变量，保证条件表达式（如 ${days > 3}）读到最新值
        engine.setVariables(inst.getProcInstId(), accepted);
    }

    /**
     * 取当前任务所在节点的可编辑字段集合。
     *
     * <p>流程已结束（无当前任务）或流程定义缺失时返回空集合。</p>
     */
    public List<String> editablePropsOf(ApprovalInstance inst, String taskId) {
        FlowDefinition fd = instanceSupport.fdOf(inst);
        if (fd == null || taskId == null || inst.getProcInstId() == null) {
            return List.of();
        }
        try {
            List<Task> tasks = engine.listTasks(inst.getProcInstId());
            Task task = tasks.stream().filter(t -> taskId.equals(t.getId())).findFirst().orElse(null);
            if (task == null) {
                return List.of();
            }
            return fieldPermissionService.listEditableProps(
                    fd.getId(), task.getTaskDefinitionKey(), primaryFormId(fd.getFlowCode()));
        } catch (Exception e) {
            // 字段权限是附加能力，取不到时退化为「全部只读」而非报错
            log.warn("取节点可编辑字段失败，按全部只读处理: taskId={}", taskId, e);
            return List.of();
        }
    }

    /**
     * 取该流程发起环节的主表单规则 JSON。
     *
     * <p>取不到时返回 null（未绑定表单 / 绑定已解绑），前端会降级为键值对展示。</p>
     */
    public String primaryRuleJson(String flowCode) {
        if (flowCode == null || flowCode.isBlank()) {
            return null;
        }
        try {
            NodeFormVO primary = nodeFormService.getPrimaryFormByFlowCode(flowCode);
            return primary == null ? null : primary.getRuleJson();
        } catch (Exception e) {
            // 表单是附加信息，取不到不应让整个详情页失败
            log.warn("取流程主表单规则失败，详情页将降级为键值对展示: flowCode={}", flowCode, e);
            return null;
        }
    }

    /** 该流程发起环节主表单的 id；未绑定时返回 null */
    private Long primaryFormId(String flowCode) {
        if (flowCode == null || flowCode.isBlank()) {
            return null;
        }
        try {
            NodeFormVO primary = nodeFormService.getPrimaryFormByFlowCode(flowCode);
            return primary == null ? null : primary.getFormId();
        } catch (Exception e) {
            log.warn("取流程主表单失败，flowCode={}", flowCode, e);
            return null;
        }
    }

    /** 解析已存的 form_data；非法 JSON 按空对象处理 */
    private Map<String, Object> parseFormData(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (Exception e) {
            log.warn("审批单的 form_data 解析失败，将以空对象继续", e);
            return new LinkedHashMap<>();
        }
    }

    /** 序列化为 JSON；失败返回空对象字符串，不抛异常阻断审批 */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("表单数据序列化失败", e);
            return "{}";
        }
    }
}
