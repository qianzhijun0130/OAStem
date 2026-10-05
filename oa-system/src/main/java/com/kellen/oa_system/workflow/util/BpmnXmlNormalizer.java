package com.kellen.oa_system.workflow.util;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
public final class BpmnXmlNormalizer {

    private static final String NS_BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String NS_FLOWABLE = "http://flowable.org/bpmn";
    private static final String TAG_PROCESS = "process";
    private static final String TAG_BPMN_PLANE = "BPMNPlane";
    private static final String ATTR_ID = "id";
    private static final String ATTR_BPMN_ELEMENT = "bpmnElement";

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
}
