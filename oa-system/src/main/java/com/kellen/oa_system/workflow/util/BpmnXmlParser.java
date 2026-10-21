package com.kellen.oa_system.workflow.util;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BPMN 元数据解析器：把一份 BPMN XML 解析成「流程模板」所需的元数据。
 *
 * <p>为什么不用 Flowable 的 {@code BpmnXMLConverter}：导入只需要读取结构（流程 key/名称、
 * 用户任务清单、审批人约定），用 JDK 自带的命名空间感知 DOM 即可完成，既避免引依赖，
 * 也不受 Flowable 转换器版本差异影响。</p>
 *
 * <p>审批人约定（两种写法，优先级从高到低）：</p>
 * <ol>
 *   <li>本项目自定义扩展：{@code <oa:assignee type="user|role|leader" value="12,15"/>}
 *       （命名空间 {@value #NS_OA}，放在 userTask 的 extensionElements 内）</li>
 *   <li>Flowable 标准属性：
 *     <ul>
 *       <li>{@code flowable:candidateUsers="12,15"} → 指定人员</li>
 *       <li>{@code flowable:candidateGroups="role:4,role:5"} → 指定角色</li>
 *       <li>{@code flowable:candidateGroups="leader"} → 部门主管</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>两种都没写时该节点标记为「未配置」（{@link #ASSIGNEE_UNSET}），导入后在设计器里人工补配。</p>
 */
public final class BpmnXmlParser {

    /** BPMN 2.0 模型命名空间（{@link BpmnXmlNormalizer} 复用同一常量） */
    static final String NS_BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    static final String NS_FLOWABLE = "http://flowable.org/bpmn";
    /** 本项目自定义扩展命名空间 */
    public static final String NS_OA = "http://oastem.com/bpmn";

    /** 审批人规则类型（与前端 NodeConfigItem.assigneeType 对齐） */
    public static final int ASSIGNEE_UNSET = 0;
    public static final int ASSIGNEE_USER = 1;
    public static final int ASSIGNEE_ROLE = 2;
    public static final int ASSIGNEE_LEADER = 3;
    /** 指定部门主管：取某个固定部门的主管（部门 id 写在 value 上），与「发起人部门主管」区分 */
    public static final int ASSIGNEE_DEPT_LEADER = 5;

    private static final String ROLE_PREFIX = "role:";
    private static final String DEPT_PREFIX = "dept:";
    private static final List<String> LEADER_TOKENS =
            List.of("leader", "dept_leader", "deptleader", "department_leader");

    private BpmnXmlParser() {
    }

    /** 解析出的单个审批节点 */
    public record BpmnNode(String nodeId, String nodeName, int assigneeType, String assigneeValue) {
    }

    /** 解析结果：流程 key、流程名称、审批节点列表 */
    public record ParsedBpmn(String key, String name, List<BpmnNode> nodes) {
    }

    /**
     * 解析 BPMN XML。
     *
     * @throws BizException 内容为空、XML 非法、缺少 process 或没有用户任务时抛出
     */
    public static ParsedBpmn parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "BPMN 内容为空");
        }
        Document doc = parseDocument(xml);
        NodeList processes = doc.getElementsByTagNameNS(NS_BPMN, "process");
        if (processes.getLength() == 0) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "未找到 <process> 节点，不是合法的 BPMN 文件");
        }
        Element process = (Element) processes.item(0);
        String key = trimToNull(process.getAttribute("id"));
        if (key == null) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "<process> 缺少 id 属性，无法确定流程 key");
        }
        String name = trimToNull(process.getAttribute("name"));

        List<BpmnNode> nodes = new ArrayList<>();
        NodeList userTasks = process.getElementsByTagNameNS(NS_BPMN, "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            Element task = (Element) userTasks.item(i);
            String nodeId = trimToNull(task.getAttribute("id"));
            if (nodeId == null) {
                continue;
            }
            String nodeName = trimToNull(task.getAttribute("name"));
            AssigneeRule rule = resolveRule(task);
            nodes.add(new BpmnNode(nodeId, nodeName == null ? nodeId : nodeName, rule.type(), rule.value()));
        }
        if (nodes.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "流程中未找到用户任务(userTask)，无法生成审批节点");
        }
        return new ParsedBpmn(key, name == null ? key : name, nodes);
    }

    /**
     * 安全解析：禁用 DTD 与外部实体，避免 XXE。
     *
     * <p>包级可见是为了让 {@link BpmnXmlNormalizer} 复用同一套安全解析配置——
     * 两处若各自 new 一个 {@code DocumentBuilderFactory}，很容易漏掉其中一项加固。</p>
     */
    static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "BPMN XML 解析失败：" + e.getMessage());
        }
    }

    /** 解析单个用户任务的审批人规则，按「自定义扩展 → Flowable 标准属性 → 未配置」依次尝试 */
    private static AssigneeRule resolveRule(Element task) {
        NodeList extensions = task.getElementsByTagNameNS(NS_OA, "assignee");
        if (extensions.getLength() > 0) {
            Element ext = (Element) extensions.item(0);
            int type = toType(trimToNull(ext.getAttribute("type")));
            if (type != ASSIGNEE_UNSET) {
                return new AssigneeRule(type, nullToEmpty(trimToNull(ext.getAttribute("value"))));
            }
        }
        String candidateUsers = trimToNull(task.getAttributeNS(NS_FLOWABLE, "candidateUsers"));
        if (candidateUsers != null) {
            return new AssigneeRule(ASSIGNEE_USER, candidateUsers);
        }
        String candidateGroups = trimToNull(task.getAttributeNS(NS_FLOWABLE, "candidateGroups"));
        if (candidateGroups != null) {
            return parseCandidateGroups(candidateGroups);
        }
        return new AssigneeRule(ASSIGNEE_UNSET, "");
    }

    private static int toType(String raw) {
        if (raw == null) {
            return ASSIGNEE_UNSET;
        }
        return switch (raw.toLowerCase()) {
            case "user", "people", "assignee" -> ASSIGNEE_USER;
            case "role", "group" -> ASSIGNEE_ROLE;
            case "leader" -> ASSIGNEE_LEADER;
            case "deptleader" -> ASSIGNEE_DEPT_LEADER;
            default -> ASSIGNEE_UNSET;
        };
    }

    /**
     * 解析 candidateGroups：{@code leader} → 部门主管；{@code role:4,role:5} → 角色 4、5。
     * 部门主管优先（一个节点只能有一种规则）。
     */
    private static AssigneeRule parseCandidateGroups(String groups) {
        List<String> tokens = split(groups);
        // 指定部门主管：dept:X（X 为部门 id），取该部门的主管
        List<String> deptIds = tokens.stream()
                .filter(t -> t.toLowerCase().startsWith(DEPT_PREFIX))
                .map(t -> t.substring(DEPT_PREFIX.length()).trim())
                .filter(s -> !s.isEmpty())
                .toList();
        if (!deptIds.isEmpty()) {
            return new AssigneeRule(ASSIGNEE_DEPT_LEADER, deptIds.get(0));
        }
        boolean isLeader = tokens.stream().anyMatch(t -> LEADER_TOKENS.contains(t.toLowerCase()));
        if (isLeader) {
            return new AssigneeRule(ASSIGNEE_LEADER, "");
        }
        List<String> roleIds = tokens.stream()
                .filter(t -> t.toLowerCase().startsWith(ROLE_PREFIX))
                .map(t -> t.substring(ROLE_PREFIX.length()).trim())
                .filter(s -> !s.isEmpty())
                .toList();
        if (!roleIds.isEmpty()) {
            return new AssigneeRule(ASSIGNEE_ROLE, String.join(",", roleIds));
        }
        return new AssigneeRule(ASSIGNEE_UNSET, "");
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record AssigneeRule(int type, String value) {
    }
}
