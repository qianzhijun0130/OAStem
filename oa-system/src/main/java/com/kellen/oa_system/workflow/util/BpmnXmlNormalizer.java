package com.kellen.oa_system.workflow.util;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.workflow.dto.FlowConditionItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

/**
 * BPMN XML 归一化器：把流程标识（BPMN 的 {@code <process id>}）对齐到业务流程编码。
 *
 * <p><b>为什么必须归一化</b>：Flowable 的流程定义 key 取自 {@code <process id>}，而「发起申请」
 * 走的是 {@code startProcessInstanceByKey}——按 key 启动且取<b>最新版本</b>。若两份模板沿用同一个
 * process id（bpmn-js 新建画布默认就是 {@code process_1}），先部署的会被后部署的顶成旧版本，
 * 表现为「选了 A 模板，实际执行的是 B 模板的流程」，审批链与表单字段双双错位。</p>
 *
 * <p>归一化后 process id == 流程编码（{@code oa_flow_definition.flow_code} 上有唯一约束），
 * 引擎侧 key 与业务编码一一对应，问题从根上消失。</p>
 *
 * <p>只改两处引用：{@code <process id>} 本身，以及 {@code <bpmndi:BPMNPlane bpmnElement>}
 * 对它的引用——后者不改会导致流程图与流程定义对不上，bpmn-js 重新打开时画布空白。</p>
 */
@Slf4j
public final class BpmnXmlNormalizer {

    private static final String NS_BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String NS_FLOWABLE = "http://flowable.org/bpmn";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String TAG_PROCESS = "process";
    private static final String TAG_BPMN_PLANE = "BPMNPlane";
    private static final String TAG_SEQUENCE_FLOW = "sequenceFlow";
    private static final String TAG_CONDITION_EXPRESSION = "conditionExpression";
    private static final String TAG_EXCLUSIVE_GATEWAY = "exclusiveGateway";
    private static final String ATTR_ID = "id";
    private static final String ATTR_BPMN_ELEMENT = "bpmnElement";
    private static final String ATTR_SOURCE_REF = "sourceRef";
    private static final String ATTR_DEFAULT = "default";
    private static final String ATTR_XSI_TYPE = "xsi:type";
    private static final String FORMAL_EXPRESSION_TYPE = "tFormalExpression";

    /** 合法标识符：JUEL 变量名只接受这类字符，数字开头会让 Flowable 在运行期才抛解析异常 */
    private static final String IDENTIFIER_PATTERN = "[A-Za-z_$][A-Za-z0-9_$]*";

    /** 运算符白名单（与前端「分支条件」标签页的下拉一一对应） */
    private static final Set<String> SUPPORTED_OPS = Set.of(
            "eq", "ne", "gt", "ge", "lt", "le", "contains", "notcontains", "empty", "notEmpty");

    /** 无需比较值的运算符 */
    private static final Set<String> VALUELESS_OPS = Set.of("empty", "notEmpty");

    /**
     * 条件组嵌套层数上限（根组算第 1 层）。
     *
     * <p>2 层即「根组 → 子组 → 条件」，可表达 {@code (A 且 B) 或 (C 且 D)}。
     * 再深下去配置界面难以阅读、生成的表达式也会逼近长度上限，收益为负。</p>
     */
    private static final int MAX_GROUP_DEPTH = 2;

    /** 单条分支上的条件总数上限：超限通常是误操作，也防止表达式无限膨胀 */
    private static final int MAX_RULES_PER_FLOW = 20;

    /**
     * 表达式危险语法黑名单。
     *
     * <p>纵深防御的一层：结构化条件在生成前已做字段/运算符/值三重校验，这里再对最终表达式
     * 做一次全文扫描，拦截「通过比较值夹带反射调用」的注入尝试（如 {@code ''.class.forName(...)}）。
     * Flowable 的 JUEL 并不暴露 {@code Class} 对象，但表达式求值失败会让流程在发起后卡死，
     * 代价远高于保存时拒绝。</p>
     *
     * <p><b>为什么不拦 {@code ${}：它是 JUEL 表达式的法定外壳，{@code buildExpression} 产出的
     * 每条表达式都以它开头，拦它等于拦掉全部结构化条件。真正的危险路径是「比较值里夹带
     * 嵌套表达式」递归求值，那已由 {@link #stringLiteral} 在值层面直接拒绝（值中含
     * {@code ${} 即抛错），无需在此重复。</p>
     */
    private static final List<String> EXPRESSION_BLOCKLIST = List.of(
            ".class", "forName", "getClass", "Runtime", "ProcessBuilder",
            "Thread", "reflect", ".exec(");

    /**
     * 单条表达式长度上限。
     *
     * <p>早期定 500 是按「单条简单条件」估的；支持条件组嵌套后，一条分支可能展开成
     * {@code ((${a > 1} && ${b == 'x'}) || ...)} 这类较长文本，故放宽到 2000。
     * 正常配置仍在几十到两百字符量级，超长几乎必然是误操作或注入。</p>
     */
    private static final int EXPRESSION_MAX_LENGTH = 2000;

    private BpmnXmlNormalizer() {
    }

    /**
     * 把 BPMN 的流程标识改写为指定编码。
     *
     * <p>幂等：process id 已等于目标值、或找不到 {@code <process>} 节点时，原样返回入参，
     * 不做无意义的重写（重写会丢失原文档的缩进风格）。找不到 process 时不在此处报错，
     * 交由后续的 {@link BpmnXmlParser#parse} 抛出语义更明确的异常。</p>
     *
     * @param xml       原始 BPMN XML
     * @param processId 目标流程标识（调用方保证非空，即业务流程编码）
     * @return 改写后的 XML；无需改写时返回原字符串
     * @throws BizException XML 非法或序列化失败
     */
    public static String overrideProcessId(String xml, String processId) {
        if (xml == null || xml.isBlank() || processId == null || processId.isBlank()) {
            return xml;
        }
        Document doc = BpmnXmlParser.parseDocument(xml);
        NodeList processes = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, TAG_PROCESS);
        if (processes.getLength() == 0) {
            return xml;
        }
        Element process = (Element) processes.item(0);
        String oldId = process.getAttribute(ATTR_ID);
        if (processId.equals(oldId)) {
            return xml;
        }

        process.setAttribute(ATTR_ID, processId);
        rebindDiagramPlane(doc, oldId, processId);
        return serialize(doc);
    }

    /** 同步改写流程图对流程节点的引用，否则画布与流程定义脱节 */
    private static void rebindDiagramPlane(Document doc, String oldId, String newId) {
        NodeList planes = doc.getElementsByTagNameNS(NS_BPMNDI, TAG_BPMN_PLANE);
        for (int i = 0; i < planes.getLength(); i++) {
            Element plane = (Element) planes.item(i);
            if (oldId.equals(plane.getAttribute(ATTR_BPMN_ELEMENT))) {
                plane.setAttribute(ATTR_BPMN_ELEMENT, newId);
            }
        }
    }

    private static String serialize(Document doc) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // 部分实现不支持这两个属性，置空失败不影响生成，仅关闭外部访问开关
            setAttributeQuietly(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
            setAttributeQuietly(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET);

            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "BPMN XML 规范化失败：" + e.getMessage());
        }
    }

    private static void setAttributeQuietly(TransformerFactory factory, String name) {
        try {
            factory.setAttribute(name, "");
        } catch (IllegalArgumentException ignored) {
            // 该实现不认识此属性，忽略即可（安全处理已由 FEATURE_SECURE_PROCESSING 兜底）
        }
    }

    /**
     * 部署期预展开 {@code role:X} → {@code candidateUsers}，消除对 Flowable IDM 的依赖。
     *
     * <p><b>为什么必须做这一步</b>：本项目未启用 Flowable IDM（无 {@code ACT_ID_*} 表），待办查询统一
     * 走 {@code taskCandidateUser(userId)}，只匹配<b>用户</b>身份链接、不解析<b>候选组</b>。当 BPMN 写的是
     * {@code flowable:candidateGroups="role:4"} 时，Flowable 只会把 {@code group=role:4} 写入身份链接，
     * 任何人都不可能从待办里看到这条任务，必须靠运行期
     * {@code ApprovalServiceImpl#resolveCandidates} 二次追加 {@code candidate user}。但该追加发生在
     * 任务创建之后，期间存在窗口期；若角色下压根没用户，任务永远隐形——这正是 {@code SMOKE-D01} 的根因。</p>
     *
     * <p><b>做法</b>：解析 BPMN，对每个 userTask：
     * <ul>
     *   <li>从 {@code flowable:candidateGroups}（含 {@code role:X}）与 {@code <oa:assignee type="role">}
     *       中收集角色 id；</li>
     *   <li>通过 {@code roleResolver} 查角色下用户 id 列表；</li>
     *   <li>追加到 {@code flowable:candidateUsers}（与已有值合并去重），不删除原 {@code candidateGroups}。部署后 Flowable
     *       会同时写「组 + 用户」身份链接，待办可正常查询；运行期 {@code assignCurrentTasks} 仍会再追加一次，双保险。</li>
     * </ul>
     * 角色下无用户时打印警告、行为不变（保持原 {@code candidateGroups}，需在设计器补配审批人）。</p>
     *
     * @param xml          原始 BPMN XML
     * @param roleResolver 角色 id → 该角色下用户 id 列表的解析函数；调用方负责查库，过滤无效 id
     * @return 改写后的 XML；无需改写时返回原字符串
     */
    public static String preExpandCandidateUsers(String xml, Function<Long, List<Long>> roleResolver) {
        if (xml == null || xml.isBlank() || roleResolver == null) {
            return xml;
        }
        Document doc = BpmnXmlParser.parseDocument(xml);
        NodeList userTasks = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, "userTask");
        boolean changed = false;
        for (int i = 0; i < userTasks.getLength(); i++) {
            Element task = (Element) userTasks.item(i);
            Set<Long> roleIds = collectRoleIds(task);
            if (roleIds.isEmpty()) {
                continue;
            }
            Set<Long> expanded = new LinkedHashSet<>();
            for (Long rid : roleIds) {
                try {
                    List<Long> users = roleResolver.apply(rid);
                    if (users != null) {
                        expanded.addAll(users);
                    }
                } catch (RuntimeException e) {
                    // 单个角色解析失败不影响其它角色与整体部署
                }
            }
            if (expanded.isEmpty()) {
                // 角色下暂无用户，保留原 candidateGroups，由运行期 addCandidateUsers 再尝试
                continue;
            }
            String existing = task.getAttributeNS(NS_FLOWABLE, "candidateUsers");
            Set<Long> merged = parseCsvToSet(existing);
            // 保留原值（兼容设计器里手工指定的固定审批人），再追加预展开的用户
            for (Long uid : expanded) {
                merged.add(uid);
            }
            String newVal = merged.stream().map(String::valueOf)
                    .reduce((a, b) -> a + "," + b).orElse("");
            task.setAttributeNS(NS_FLOWABLE, "flowable:candidateUsers", newVal);
            changed = true;
        }
        if (!changed) {
            return xml;
        }
        return serialize(doc);
    }

    /** 收集该 userTask 上引用到的角色 id：flowable:candidateGroups 中的 role:X 与 oa:assignee type=role value=X */
    private static Set<Long> collectRoleIds(Element task) {
        Set<Long> ids = new LinkedHashSet<>();
        String cg = task.getAttributeNS(NS_FLOWABLE, "candidateGroups");
        if (cg != null && !cg.isBlank()) {
            for (String token : cg.split(",")) {
                String t = token.trim();
                if (t.length() > 5 && t.substring(0, 5).equalsIgnoreCase("role:")) {
                    parseLongQuiet(ids, t.substring(5));
                }
            }
        }
        NodeList ext = task.getElementsByTagNameNS(BpmnXmlParser.NS_OA, "assignee");
        for (int i = 0; i < ext.getLength(); i++) {
            Element e = (Element) ext.item(i);
            if ("role".equalsIgnoreCase(e.getAttribute("type"))) {
                String val = e.getAttribute("value");
                if (val != null && !val.isBlank()) {
                    for (String token : val.split(",")) {
                        parseLongQuiet(ids, token.trim());
                    }
                }
            }
        }
        return ids;
    }

    private static Set<Long> parseCsvToSet(String csv) {
        Set<Long> set = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return set;
        }
        for (String token : csv.split(",")) {
            parseLongQuiet(set, token.trim());
        }
        return set;
    }

    private static void parseLongQuiet(Set<Long> sink, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            sink.add(Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            // 非数字 id 静默忽略，与 ApprovalServiceImpl#parseIds 保持一致
        }
    }

    /**
     * 把「分支条件」结构化配置注入 BPMN：为每条连线写 {@code <conditionExpression>}，
     * 为标记默认分支的连线所在网关写 {@code default} 属性。
     *
     * <p><b>为什么注入而不是让前端直接画</b>：前端配置以 JSON 形式存在
     * {@code oa_flow_definition.flow_config}，重新打开设计器时按 JSON 回显，
     * 不需要从 JUEL 文本逆向解析（词法脆弱且易错）；引擎运行期只认注入后的标准元素，
     * 与内置模板手写的 {@code ${outcome == 'approve'}} 走同一条链路。</p>
     *
     * <p><b>覆盖语义</b>：只处理 flow_config 里出现过的连线——注入即覆盖该连线上原有的
     * conditionExpression（用户在属性面板里手改的原文会被结构化配置顶掉，这是已确认的产品决策）；
     * 未出现过的连线一律保持原样，内置模板的审批结论分支因此不受影响。</p>
     *
     * <p><b>三道校验</b>（保存时 fail-fast，绝不把错配置放进引擎）：
     * <ol>
     *   <li>结构校验：flowId 必须存在、其源必须是排他网关；conditions 为空时必须标记默认分支；</li>
     *   <li>取值校验：字段为合法标识符、运算符白名单、number 型取值必须为数值、string 型取值转义
     *       且拒绝内含 {@code ${}} 的嵌套表达式；</li>
     *   <li>安全校验：最终表达式过危险语法黑名单（反射调用类）与长度上限；JUEL 外壳 {@code ${}}
     *       是表达式的法定组成部分，不在黑名单内（详见 {@link #EXPRESSION_BLOCKLIST}）。</li>
     * </ol>
     * </p>
     *
     * @param xml   已归一化（process id / 候选人）的 BPMN XML
     * @param items 分支条件配置；为空或 null 时原样返回
     * @return 注入后的 XML；无需注入时返回原字符串
     * @throws BizException 2508 配置不合法 / 2509 表达式含危险语法
     */
    public static String injectConditions(String xml, List<FlowConditionItem> items) {
        return injectConditions(xml, items, null);
    }

    /**
     * 注入分支条件，并**清理上次由本系统注入、本次已不存在的条件**。
     *
     * <p>为什么需要清理：设计器画布是从「已部署的 BPMN」加载的，其中已包含上一次注入的
     * {@code <conditionExpression>}。用户在「分支条件」表格里删掉条件后保存，画布 XML 里
     * 的旧表达式并不会自己消失——只改 {@code flow_config} 而不清理 XML，运行期仍按旧条件路由，
     * 即「界面上清空了、流程行为没变」（D1）。</p>
     *
     * <p><b>只清自己注入过的</b>：{@code previouslyInjectedFlowIds} 来自上一次的 flow_config，
     * 因此内置模板手写的 {@code ${outcome == 'approve'}} 这类表达式（从未出现在 flow_config 中）
     * 不会被误删。清理时保留网关的 {@code default} 属性——默认分支本身可以没有条件，
     * 保留它才能通过「排他网关必须有默认分支」的结构校验。</p>
     *
     * @param previouslyInjectedFlowIds 上一次 flow_config 里涉及的连线 id；null/空表示无历史
     */
    public static String injectConditions(String xml, List<FlowConditionItem> items,
                                         Collection<String> previouslyInjectedFlowIds) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        boolean hasNew = items != null && !items.isEmpty();
        boolean hasHistory = previouslyInjectedFlowIds != null && !previouslyInjectedFlowIds.isEmpty();
        if (!hasNew && !hasHistory) {
            return xml;
        }
        Document doc = BpmnXmlParser.parseDocument(xml);
        Map<String, Element> flows = indexSequenceFlows(doc);
        Map<String, Element> gateways = indexGateways(doc);
        boolean changed = false;

        Set<String> configured = new LinkedHashSet<>();
        if (hasNew) {
            for (FlowConditionItem item : items) {
                if (item != null && trimToNull(item.getFlowId()) != null) {
                    configured.add(item.getFlowId().trim());
                }
            }
        }
        if (hasHistory) {
            for (String flowId : new LinkedHashSet<>(previouslyInjectedFlowIds)) {
                String id = trimToNull(flowId);
                if (id == null || configured.contains(id)) {
                    continue;
                }
                Element flow = flows.get(id);
                if (flow == null) {
                    continue;
                }
                if (removeConditionExpression(flow)) {
                    changed = true;
                }
            }
        }
        if (!hasNew) {
            // 只剩清理动作：没有新条件要注入，直接返回（可能已删除旧表达式）
            return changed ? serialize(doc) : xml;
        }
        items = items.stream().filter(java.util.Objects::nonNull).toList();
        // 每个网关只允许一条默认分支：注入期内先记账，配置里出现第二条即拒绝
        Set<String> defaultAssigned = new LinkedHashSet<>();
        Set<String> seenFlows = new LinkedHashSet<>();

        for (FlowConditionItem item : items) {
            if (item == null) {
                continue;
            }
            String flowId = requireText(item.getFlowId(), "条件配置缺少连线 id");
            if (!seenFlows.add(flowId)) {
                throw conditionInvalid("连线 " + flowId + " 在条件配置中重复出现");
            }
            Element flow = flows.get(flowId);
            if (flow == null) {
                throw conditionInvalid("条件配置引用了流程图中不存在的连线：" + flowId);
            }
            Element gateway = resolveGateway(gateways, flow, item);

            boolean isDefault = Boolean.TRUE.equals(item.getIsDefault());
            // 手写表达式模式与可视化条件组模式统一在此解析；返回 null 表示该分支没有条件
            String expression = resolveExpression(item);
            if (expression == null && !isDefault) {
                throw conditionInvalid("连线 " + flowId + " 既没有配置条件，也不是默认分支");
            }

            if (expression != null) {
                setConditionExpression(doc, flow, expression);
                changed = true;
            }
            if (isDefault) {
                if (!defaultAssigned.add(gateway.getAttribute(ATTR_ID))) {
                    throw conditionInvalid("网关「" + describe(gateway) + "」被多条连线标记为默认分支");
                }
                gateway.setAttribute(ATTR_DEFAULT, flowId);
                changed = true;
            }
        }
        if (!changed) {
            return xml;
        }
        return serialize(doc);
    }

    /** 按 id 索引全部 sequenceFlow，注入时按 id 定位 */
    private static Map<String, Element> indexSequenceFlows(Document doc) {
        Map<String, Element> flows = new HashMap<>();
        NodeList nodes = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, TAG_SEQUENCE_FLOW);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element flow = (Element) nodes.item(i);
            String id = trimToNull(flow.getAttribute(ATTR_ID));
            if (id != null) {
                flows.put(id, flow);
            }
        }
        return flows;
    }

    /**
     * 按 id 索引全部排他网关。
     *
     * <p>刻意不用 {@code doc.getElementById}：安全解析禁用了 DTD，未声明 ID 类型的属性
     * 不会被解析器注册，{@code getElementById} 会静默返回 null——那会让「上游不是排他网关」
     * 的校验把合法配置误判为非法。</p>
     */
    private static Map<String, Element> indexGateways(Document doc) {
        Map<String, Element> gateways = new HashMap<>();
        NodeList nodes = doc.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, TAG_EXCLUSIVE_GATEWAY);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element gateway = (Element) nodes.item(i);
            String id = trimToNull(gateway.getAttribute(ATTR_ID));
            if (id != null) {
                gateways.put(id, gateway);
            }
        }
        return gateways;
    }

    /**
     * 校验并取出连线上游的排他网关。
     *
     * <p>配置里的 gatewayId 与 BPMN 里连线的 sourceRef 必须一致：前端回显的网关信息来自
     * 画布快照，若用户在画布上删了旧网关重画（id 变化）而 flow_config 没同步，注入时就会错位到
     * 别的节点上——这类「静默挂到错误网关」比报错危险得多。</p>
     */
    private static Element resolveGateway(Map<String, Element> gateways, Element flow, FlowConditionItem item) {
        String sourceRef = trimToNull(flow.getAttribute(ATTR_SOURCE_REF));
        if (sourceRef == null) {
            throw conditionInvalid("连线 " + flow.getAttribute(ATTR_ID) + " 没有 sourceRef，无法确定所属网关");
        }
        String gatewayId = trimToNull(item.getGatewayId());
        if (gatewayId != null && !gatewayId.equals(sourceRef)) {
            throw conditionInvalid("连线 " + flow.getAttribute(ATTR_ID)
                    + " 的上游节点是 " + sourceRef + "，与配置中的网关 " + gatewayId + " 不一致");
        }
        Element source = gateways.get(sourceRef);
        if (source == null) {
            throw conditionInvalid("连线 " + flow.getAttribute(ATTR_ID)
                    + " 的上游节点不是排他网关（exclusiveGateway），条件分支暂只支持排他网关");
        }
        return source;
    }

    /**
     * 由分支条件配置生成最终写入 BPMN 的 JUEL 表达式。
     *
     * <p><b>整体必须包在单个 {@code ${...}} 内</b>。早期实现为每条条件各生成一个
     * {@code ${}}、再用 {@code &&} 在外面连接，产出形如
     * {@code ${days > 3} && ${type == '出差'}} 的文本。JUEL 遇到「多个表达式 + 中间夹普通文字」
     * 会按字符串拼接求值，得到 {@code "true && false"} 这样的文本而非布尔值，
     * Flowable 判定网关分支时要么抛"非布尔值"异常卡住流程、要么静默当成假值走错分支。
     * 递归生成 + 单一外壳从根上消除了该问题。</p>
     *
     * <p>两种模式：
     * <ul>
     *   <li>{@code raw}：直接用配置里的表达式原文（补 {@code ${}} 外壳后过安全校验）；</li>
     *   <li>{@code visual}：由条件树递归生成，子组带括号保证运算优先级。</li>
     * </ul>
     * </p>
     *
     * <p><b>为什么对外公开</b>：分支命中日志要记录「生效的条件表达式」用于追溯，而可视化
     * 模式只存了条件树、没存表达式。让日志侧复用本方法生成，避免同一套规则在两处各写一遍
     * 而逐渐跑偏（日志里显示的表达式与实际求值的不一致，比不显示更有害）。</p>
     *
     * @return 表达式文本；该分支未配置任何条件时返回 null（调用方据此判断是否注入）
     * @throws BizException 2508 条件结构不合法 / 2509 表达式含危险语法
     */
    public static String resolveExpression(FlowConditionItem item) {
        String flowId = item.getFlowId();
        if (item.isRawMode()) {
            String raw = requireText(item.getExpression(), "连线 " + flowId + " 为手写表达式模式，但未填写表达式");
            String expression = raw.contains("${") ? raw : "${" + raw + "}";
            assertExpressionSafe(flowId, expression);
            return expression;
        }
        FlowConditionItem.ConditionGroup root = item.normalizedCondition();
        if (root == null || (isBlank(root.getRules()) && isBlank(root.getGroups()))) {
            return null;
        }
        int[] ruleCount = new int[1];
        String body = buildGroup(flowId, root, 1, ruleCount);
        if (ruleCount[0] > MAX_RULES_PER_FLOW) {
            throw conditionInvalid("连线 " + flowId + " 的条件条数超过上限（最多 " + MAX_RULES_PER_FLOW + " 条）");
        }
        String expression = "${" + body + "}";
        assertExpressionSafe(flowId, expression);
        return expression;
    }

    /**
     * 递归生成条件组的表达式片段（不含 {@code ${}} 外壳）。
     *
     * <p>组内各条目（直接条件 + 子组）用 {@code &&} / {@code ||} 连接，条目数大于 1 时整体加括号，
     * 使子组参与上层运算时优先级与配置意图一致。</p>
     *
     * @param depth 当前层数（根组为 1），超过 {@link #MAX_GROUP_DEPTH} 即拒绝
     * @param ruleCount 累加器：统计全树条件条数（数组包装以便在递归中累加）
     */
    private static String buildGroup(String flowId, FlowConditionItem.ConditionGroup group,
                                     int depth, int[] ruleCount) {
        if (group == null) {
            throw conditionInvalid("连线 " + flowId + " 的条件组为空");
        }
        if (depth > MAX_GROUP_DEPTH) {
            throw conditionInvalid("连线 " + flowId + " 的条件组嵌套超过上限（最多 " + MAX_GROUP_DEPTH + " 层）");
        }
        String joiner = "or".equalsIgnoreCase(trimToNull(group.getLogic())) ? " || " : " && ";
        List<String> parts = new ArrayList<>();
        if (!isBlank(group.getRules())) {
            for (FlowConditionItem.ConditionRule rule : group.getRules()) {
                parts.add(buildRule(flowId, rule));
                ruleCount[0]++;
            }
        }
        if (!isBlank(group.getGroups())) {
            for (FlowConditionItem.ConditionGroup sub : group.getGroups()) {
                parts.add(buildGroup(flowId, sub, depth + 1, ruleCount));
            }
        }
        if (parts.isEmpty()) {
            throw conditionInvalid("连线 " + flowId + " 的条件组为空：至少要有一条条件");
        }
        return parts.size() == 1 ? parts.get(0) : "(" + String.join(joiner, parts) + ")";
    }

    /** 生成单条条件的表达式片段（自带括号，避免与相邻条件混淆优先级） */
    private static String buildRule(String flowId, FlowConditionItem.ConditionRule rule) {
        if (rule == null) {
            throw conditionInvalid("连线 " + flowId + " 的条件组中存在空条件");
        }
        String field = requireText(rule.getField(), "连线 " + flowId + " 的条件缺少变量名");
        if (!field.matches(IDENTIFIER_PATTERN)) {
            throw conditionInvalid("条件变量名「" + field + "」不是合法标识符（字母/下划线开头，仅含字母数字下划线）");
        }
        String op = trimToNull(rule.getOp());
        if (op == null || !SUPPORTED_OPS.contains(op.toLowerCase(Locale.ROOT))) {
            throw conditionInvalid("条件运算符「" + op + "」不受支持");
        }
        return switch (op.toLowerCase(Locale.ROOT)) {
            case "empty" -> "(" + field + " == null || " + field + " == '')";
            case "notEmpty" -> "(" + field + " != null && " + field + " != '')";
            case "contains" -> "(" + field + " != null && " + field + ".contains(" + textLiteral(field, rule) + "))";
            case "notcontains" -> "(" + field + " == null || !" + field + ".contains(" + textLiteral(field, rule) + "))";
            default -> "(" + field + " " + comparison(field, rule) + ")";
        };
    }

    /** 取文本型字面量（contains / notContains 专用：这两个运算符不接受数值） */
    private static String textLiteral(String field, FlowConditionItem.ConditionRule rule) {
        String value = requireText(rule.getValue(), "条件「" + field + "」缺少比较值");
        return stringLiteral(field, value);
    }

    /** 拼出单条比较的中间部分（不含变量与括号），string / date 型 relational 走 compareTo */
    private static String comparison(String field, FlowConditionItem.ConditionRule rule) {
        String op = rule.getOp().toLowerCase(Locale.ROOT);
        if (VALUELESS_OPS.contains(op)) {
            throw conditionInvalid("运算符 " + op + " 不需要比较值");
        }
        String value = requireText(rule.getValue(), "条件「" + field + "」缺少比较值");
        String valueType = trimToNull(rule.getValueType());
        boolean numeric = "number".equalsIgnoreCase(valueType);
        boolean bool = "boolean".equalsIgnoreCase(valueType);
        // date 型与 string 型同路径：ISO 日期按字典序比较等价于按时间比较
        String literal = numeric ? numericLiteral(field, value)
                : bool ? booleanLiteral(field, value)
                : stringLiteral(field, value);
        if (bool && !"eq".equals(op) && !"ne".equals(op)) {
            throw conditionInvalid("布尔类型字段仅支持「等于 / 不等于」，当前运算符为 " + op);
        }
        return switch (op) {
            case "eq" -> "== " + literal;
            case "ne" -> "!= " + literal;
            case "gt" -> numeric ? "> " + literal : ".compareTo(" + literal + ") > 0";
            case "ge" -> numeric ? ">= " + literal : ".compareTo(" + literal + ") >= 0";
            case "lt" -> numeric ? "< " + literal : ".compareTo(" + literal + ") < 0";
            case "le" -> numeric ? "<= " + literal : ".compareTo(" + literal + ") <= 0";
            default -> throw conditionInvalid("条件运算符「" + op + "」不受支持");
        };
    }

    /** boolean 型取值：只能是 true / false，裸写进表达式（JUEL 能识别布尔字面量） */
    private static String booleanLiteral(String field, String value) {
        String normalized = value.trim();
        if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
            throw conditionInvalid("条件「" + field + "」选择了布尔类型，但比较值「" + value + "」不是 true / false");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(List<?> list) {
        return list == null || list.isEmpty();
    }

    /** number 型取值：必须是数值，避免把 'abc' 裸写进表达式让 JUEL 运行期才报错 */
    private static String numericLiteral(String field, String value) {
        if (!value.matches("-?\\d+(\\.\\d+)?")) {
            throw conditionInvalid("条件「" + field + "」选择了数值类型，但比较值「" + value + "」不是数值");
        }
        return value;
    }

    /** string 型取值：转义反斜线与单引号，拒绝控制符与嵌套表达式 */
    private static String stringLiteral(String field, String value) {
        if (value.contains("${")) {
            throw conditionInvalid("条件「" + field + "」的比较值不允许包含 ${...} 嵌套表达式");
        }
        if (value.chars().anyMatch(c -> c == '\n' || c == '\r')) {
            throw conditionInvalid("条件「" + field + "」的比较值不允许包含换行");
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** 表达式安全校验：黑名单 + 长度（字段名与比较值同样过检，注入从任一环节来都拦得住） */
    private static void assertExpressionSafe(String flowId, String expression) {
        if (expression.length() > EXPRESSION_MAX_LENGTH) {
            throw new BizException(ErrorCode.FLOW_CONDITION_EXPR_UNSAFE,
                    "连线 " + flowId + " 的条件表达式过长（上限 " + EXPRESSION_MAX_LENGTH + " 字符）");
        }
        for (String blocked : EXPRESSION_BLOCKLIST) {
            if (expression.contains(blocked)) {
                log.warn("条件表达式命中安全黑名单 flowId={} pattern={}", flowId, blocked);
                throw new BizException(ErrorCode.FLOW_CONDITION_EXPR_UNSAFE,
                        "连线 " + flowId + " 的条件表达式包含不允许的语法：" + blocked);
            }
        }
    }

    /**
     * 删除连线上的 conditionExpression（清空分支条件时使用）。
     *
     * @return 是否真的删掉了东西（没删说明该连线本来就没有条件，用于判断 XML 是否需要重新序列化）
     */
    private static boolean removeConditionExpression(Element flow) {
        NodeList existing = flow.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, TAG_CONDITION_EXPRESSION);
        boolean removed = false;
        while (existing.getLength() > 0) {
            Node old = existing.item(0);
            old.getParentNode().removeChild(old);
            removed = true;
        }
        return removed;
    }

    /** 写入（或覆盖）连线上的 conditionExpression：先删旧的再追加，保证覆盖语义且不重复 */
    private static void setConditionExpression(Document doc, Element flow, String expression) {
        NodeList existing = flow.getElementsByTagNameNS(BpmnXmlParser.NS_BPMN, TAG_CONDITION_EXPRESSION);
        while (existing.getLength() > 0) {
            Node old = existing.item(0);
            old.getParentNode().removeChild(old);
        }
        Element condition = doc.createElementNS(BpmnXmlParser.NS_BPMN, TAG_CONDITION_EXPRESSION);
        condition.setAttributeNS(NS_XSI, ATTR_XSI_TYPE, FORMAL_EXPRESSION_TYPE);
        condition.setTextContent(expression);
        // sequenceFlow 的内容模型是 documentation*, extensionElements?, conditionExpression?，
        // 追加到末尾即满足 XSD 顺序；Flowable 求值时只按元素名查找，与顺序无关。
        flow.appendChild(condition);
    }

    private static BizException conditionInvalid(String detail) {
        return new BizException(ErrorCode.FLOW_CONDITION_INVALID, detail);
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw conditionInvalid(message);
        }
        return trimmed;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String describe(Element gateway) {
        String name = trimToNull(gateway.getAttribute("name"));
        return name == null ? gateway.getAttribute(ATTR_ID) : name;
    }
}
