package com.kellen.oa_system.workflow.support;

import com.kellen.oa_system.workflow.dto.FlowConditionItem;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.util.BpmnXmlNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分支命中日志：把「网关走了哪条分支、当时变量是什么」写进审批记录表。
 *
 * <p><b>为什么用业务侧反推而不是引擎监听器</b>：给每条连线注入 executionListener
 * 需要改动 BPMN 注入逻辑且要求引擎能加载监听器类，成本高、出错面大；
 * 而审批流转的落点（下一个任务节点 id）在 {@code flow_config} 里有明确记录，
 * 反推即可定位命中的分支，且完全可控。代价是「网关→网关」连续跳转
 * （中间不产生任务）只能记到最终落脚的分支——本项目当前不依赖该结构。</p>
 *
 * <p><b>落库位置</b>：复用 {@code oa_approval_record}，用 {@code action='branch'} 与真实审批动作区分，
 * 详情时间轴按需过滤。<b>不新增表</b>。</p>
 *
 * <p>写日志这件事本身不该阻断审批流转：解析失败、表达式生成失败都只警告并降级，
 * 绝不抛异常。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalBranchSupport {

    private final ApprovalRecordMapper recordMapper;
    private final FlowableEngineService engine;
    private final ObjectMapper objectMapper;

    /**
     * @param nextNodeId 流转后落到的任务节点 id（BPMN 的 taskDefinitionKey），为空表示流程已结束
     */
    public void recordBranchHit(ApprovalInstance inst, FlowDefinition fd, String nextNodeId) {
        if (nextNodeId == null || nextNodeId.isBlank() || fd == null) {
            return;
        }
        List<FlowConditionItem> items = parseConditions(fd.getFlowConfig());
        if (items.isEmpty()) {
            return;
        }
        FlowConditionItem hit = null;
        for (FlowConditionItem item : items) {
            if (nextNodeId.equals(item.getTargetNodeId())) {
                hit = item;
                break;
            }
        }
        if (hit == null) {
            // 落点不在任何已配置的分支上：可能没配条件，或走了内置模板的分支，不记录
            return;
        }
        Map<String, Object> snapshot = engine.getVariables(inst.getProcInstId());
        String branchName = firstNonBlank(hit.getName(), hit.getTargetNodeName(), hit.getFlowId());
        String detail = buildBranchDetail(hit, branchName, snapshot);

        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setNodeId(hit.getFlowId());
        rec.setNodeName(truncate(branchName, 100));
        rec.setAction(ApprovalConstants.ACTION_BRANCH);
        rec.setOpinion(truncate(detail, ApprovalConstants.BRANCH_DETAIL_MAX));
        recordMapper.insert(rec);
    }

    /**
     * 拼分支命中明细：分支名 + 当时参与判断的变量值 + 生效的表达式。
     *
     * <p>变量只挑「该分支条件里引用到的」——全量快照会把整包 formData 也写进来，
     * 既超长又无追溯价值。</p>
     */
    private String buildBranchDetail(FlowConditionItem hit, String branchName, Map<String, Object> snapshot) {
        StringBuilder sb = new StringBuilder("分支命中：").append(branchName);
        Set<String> referenced = hit.referencedFields();
        if (!referenced.isEmpty()) {
            sb.append("｜变量：");
            boolean first = true;
            for (String field : referenced) {
                if (!first) {
                    sb.append("，");
                }
                first = false;
                Object value = snapshot.get(field);
                sb.append(field).append('=').append(value == null ? "空" : truncate(String.valueOf(value), 60));
            }
        }
        String expr = resolveLoggedExpression(hit);
        if (expr != null && !expr.isBlank()) {
            sb.append("｜表达式：").append(expr);
        }
        return sb.toString();
    }

    /**
     * 取要写进命中日志的条件表达式。
     *
     * <p>可视化模式只落了条件树、没落表达式文本，这里复用注入 BPMN 时同一套生成规则现算一次，
     * 保证「日志里看到的」与「网关实际求值的」是同一串字符——两者一旦跑偏，追溯日志反而会误导排查。</p>
     */
    private String resolveLoggedExpression(FlowConditionItem hit) {
        String expr = hit.getExpression();
        if (expr != null && !expr.isBlank()) {
            return expr;
        }
        try {
            return BpmnXmlNormalizer.resolveExpression(hit);
        } catch (RuntimeException e) {
            log.warn("生成分支条件表达式失败，命中日志将不含表达式：flowId={}", hit.getFlowId(), e);
            return null;
        }
    }

    /** 解析 flow_config（分支条件配置）；解析失败返回空表，不让日志写入阻断审批 */
    private List<FlowConditionItem> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FlowConditionItem>>() {
            });
        } catch (Exception e) {
            log.warn("解析 flow_config 失败，跳过分支命中日志：{}", e.getMessage());
            return List.of();
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return "";
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
