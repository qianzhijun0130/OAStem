package com.kellen.oa_system.workflow.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部署期 BPMN 结构校验器：在部署到 Flowable 之前拦住「能解析、但跑起来会错位或卡死」的流程。
 *
 * <p><b>为什么需要它</b>：{@link BpmnXmlParser} 只保证「能读出节点」；Flowable 部署期只做 XSD 校验。
 * 这两道都拦不住「排他网关忘记配默认分支 / 用户任务没配审批人 / 画了孤立节点不连线」这类问题——
 * 它们保存时一切正常，运行起来却要么引擎直接报错、要么单据卡在某个节点永远没人处理。
 * 在设计器里当场拦住，比上线后排查成本低一个数量级（对标 RuoYi 流程设计的严格模式）。</p>
 *
 * <p><b>校验项</b>：
 * <ol>
 *   <li>开始/结束事件：至少各 1 个（允许多结束事件，如「通过 / 驳回」双出口）；</li>
 *   <li>排他网关：有默认分支时其余分支必须都带条件；无默认分支时全部分支必须带条件；</li>
 *   <li>孤立节点：从任一开始事件出发可达的流节点之外，其余用户任务 / 网关视为孤立（通常漏连了连线）。</li>
 * </ol>
 * <p>审批人配置校验不在此处：设计器在「节点审批人」表格里配置审批人并写入 {@code node_config}
 * （BPMN 的 userTask 不携带 oa:assignee，与手写种子 BPMN 不同），故「未配置审批人」这类校验由
 * {@code FlowDefinitionServiceImpl} 基于 node_config 完成，而不是读 BPMN——否则会误拒所有设计器保存。</p>
 */
public final class BpmnXmlValidator {

    /** 视为「流节点」的 BPMN 元素（参与可达性分析，孤立检测针对其中未被触达者） */
    private static final Set<String> FLOW_NODE_TAGS = Set.of(
            "startEvent", "endEvent", "userTask", "serviceTask", "task",
            "exclusiveGateway", "inclusiveGateway", "parallelGateway",
            "intermediateCatchEvent", "scriptTask", "businessRuleTask", "manualTask",
            "sendTask", "receiveTask");

    private BpmnXmlValidator() {
    }

    /** 单条结构问题（携带节点 id / 名称，便于前端高亮与回显） */
    public record StructureIssue(String nodeId, String nodeName, String message) {
    }

    /**
     * 校验 BPMN 结构（开始/结束事件、排他网关分支完整性、孤立节点）。
     *
     * <p>审批人缺失校验不在此处（见类注释）：设计器配置的审批人写入 {@code node_config} 而非 BPMN，
     * 应在 {@code FlowDefinitionServiceImpl} 基于 node_config 校验。</p>
     *
     * @param xml 已归一化（process id / 候选人）的 BPMN XML
     * @return 全部结构问题；为空表示通过
     */
    public static List<StructureIssue> validate(String xml) {
        List<StructureIssue> issues = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            issues.add(new StructureIssue(null, null, "BPMN 内容为空"));
            return issues;
        }
        Document doc = BpmnXmlParser.parseDocument(xml);

        int startCount = countTag(doc, "startEvent");
        int endCount = countTag(doc, "endEvent");
        if (startCount == 0) {
            issues.add(new StructureIssue(null, null, "流程缺少开始事件（startEvent），无法发起"));
        }
        if (endCount == 0) {
            issues.add(new StructureIssue(null, null, "流程缺少结束事件（endEvent），无法结束"));
        }

        issues.addAll(checkGateways(doc));
        issues.addAll(checkOrphans(doc));
        return issues;
    }

    private static int countTag(Document doc, String localName) {
        return doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, localName).getLength();
    }

    /**
     * 排他网关分支完整性：
     * <ul>
     *   <li>标记了 {@code default} 分支 → 其余每条出线都必须带条件；</li>
     *   <li>未标记 {@code default} → 全部出线都必须带条件（否则引擎运行到该网关会找不到可走分支而报错）。</li>
     * </ul>
     */
    private static List<StructureIssue> checkGateways(Document doc) {
        List<StructureIssue> issues = new ArrayList<>();
        Map<String, List<Element>> outgoingBySource = indexOutgoingFlows(doc);

        NodeList gateways = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, "exclusiveGateway");
        for (int i = 0; i < gateways.getLength(); i++) {
            Element g = (Element) gateways.item(i);
            String gid = trim(g.getAttribute("id"));
            String gatewayName = describe(g);
            String defaultFlow = trim(g.getAttribute("default"));
            List<Element> outgoing = outgoingBySource.getOrDefault(gid, List.of());
            if (outgoing.isEmpty()) {
                issues.add(new StructureIssue(gid, gatewayName, "排他网关没有任何出线，流程无法继续"));
                continue;
            }
            for (Element flow : outgoing) {
                String flowId = trim(flow.getAttribute("id"));
                boolean isDefault = flowId != null && flowId.equals(defaultFlow);
                boolean hasCondition = flow.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, "conditionExpression").getLength() > 0;
                if (!isDefault && !hasCondition) {
                    issues.add(new StructureIssue(flowId, null,
                            "排他网关「" + gatewayName + "」的分支「" + (trim(flow.getAttribute("name")) == null ? flowId : trim(flow.getAttribute("name")))
                                    + "」既非默认分支也未配置条件，运行时将无分支可走"));
                }
            }
        }
        return issues;
    }

    /**
     * 孤立节点检测：从全部开始事件出发做可达性遍历，任何未被触达的流节点视为孤立。
     *
     * <p>孤立通常意味着漏连了连线（画了节点却没接进主链路），运行期它永远不会被激活，
     * 还容易让人误以为流程已覆盖该环节。开始事件本身是可达起点，不计入孤立。</p>
     */
    private static List<StructureIssue> checkOrphans(Document doc) {
        List<StructureIssue> issues = new ArrayList<>();

        Map<String, Element> nodeById = new HashMap<>();
        for (String tag : FLOW_NODE_TAGS) {
            NodeList nodes = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String id = trim(e.getAttribute("id"));
                if (id != null) {
                    nodeById.put(id, e);
                }
            }
        }
        if (nodeById.isEmpty()) {
            return issues;
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        NodeList flows = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            String src = trim(f.getAttribute("sourceRef"));
            String tgt = trim(f.getAttribute("targetRef"));
            if (src != null && tgt != null) {
                adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            }
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        NodeList starts = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, "startEvent");
        for (int i = 0; i < starts.getLength(); i++) {
            String id = trim(((Element) starts.item(i)).getAttribute("id"));
            if (id != null && visited.add(id)) {
                queue.add(id);
            }
        }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : adjacency.getOrDefault(cur, List.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        for (Map.Entry<String, Element> entry : nodeById.entrySet()) {
            if (!visited.contains(entry.getKey())) {
                issues.add(new StructureIssue(entry.getKey(), describe(entry.getValue()),
                        "是孤立节点（没有任何可达路径连到它），请检查连线"));
            }
        }
        return issues;
    }

    private static Map<String, List<Element>> indexOutgoingFlows(Document doc) {
        Map<String, List<Element>> bySource = new HashMap<>();
        NodeList flows = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            String src = trim(f.getAttribute("sourceRef"));
            if (src != null) {
                bySource.computeIfAbsent(src, k -> new ArrayList<>()).add(f);
            }
        }
        return bySource;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String describe(Element e) {
        String name = trim(e.getAttribute("name"));
        String id = trim(e.getAttribute("id"));
        return name == null ? (id == null ? e.getTagName() : id) : name;
    }
}
