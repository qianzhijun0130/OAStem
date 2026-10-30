package com.kellen.oa_system.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 审批人解析：把 {@code node_config} 里的规则翻译成「当前节点的候选人 id 列表」。
 *
 * <p>五类策略各自的兜底都在这里，且只有这一份：</p>
 * <ul>
 *   <li>角色下无人 → 兜底 SUPER_ADMIN（修复 SMOKE-D01 的关键，避免节点无人处理单据卡死）；</li>
 *   <li>部门链无主管 → 同样兜底 SUPER_ADMIN；</li>
 *   <li>层级超过实际层级 → 取最顶层主管（不返回空）。</li>
 * </ul>
 *
 * <p>兜底逻辑一旦分叉，就会出现「A 类节点卡死、B 类节点自愈」的诡异现象，
 * 排查时几乎无从下手。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalAssigneeSupport {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    /** 按 node_config 解析某节点的候选人用户 id 列表 */
    public List<Long> resolveCandidates(FlowDefinition fd, Long applicantId, String nodeKey) {
        List<NodeConfigItem> items = parseNodeConfig(fd.getNodeConfig());
        NodeConfigItem match = items.stream()
                .filter(i -> nodeKey != null && nodeKey.equals(i.getNodeId()))
                .findFirst().orElse(null);
        if (match == null || match.getAssigneeType() == null) {
            log.warn("流程 {} 节点 {} 未配置审批人规则，任务将无人可处理", fd.getFlowCode(), nodeKey);
            return List.of();
        }
        return switch (match.getAssigneeType()) {
            case ApprovalConstants.ASSIGNEE_USER -> parseIds(match.getAssigneeValue());
            case ApprovalConstants.ASSIGNEE_ROLE -> {
                List<Long> roleIds = parseIds(match.getAssigneeValue());
                if (roleIds.isEmpty()) {
                    yield List.of();
                }
                List<Long> users = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                                .in(UserRole::getRoleId, roleIds)).stream()
                        .map(UserRole::getUserId).distinct().toList();
                if (users.isEmpty()) {
                    // 角色下暂无用户：兜底给 SUPER_ADMIN（role_code=SUPER_ADMIN）下所有用户。
                    // 业务约定：超管可处理所有审批任务，避免节点无人处理导致单据卡死。
                    // 这是修复 SMOKE-D01 的关键兜底——部署期预展开（BpmnXmlNormalizer.preExpandCandidateUsers）
                    // 在角色下无人时同样会跳过，必须依赖此处兜底。
                    List<Long> superAdmins = selectSuperAdminIds();
                    if (!superAdmins.isEmpty()) {
                        log.info("流程 {} 节点 {} 角色 {} 下无用户，已兜底为 SUPER_ADMIN（{} 人）",
                                fd.getFlowCode(), nodeKey, roleIds, superAdmins.size());
                        users = superAdmins;
                    } else {
                        log.warn("流程 {} 节点 {} 角色 {} 下无用户且 SUPER_ADMIN 也无可用账号，任务将无人处理",
                                fd.getFlowCode(), nodeKey, roleIds);
                    }
                }
                yield users;
            }
            case ApprovalConstants.ASSIGNEE_LEADER -> resolveLeaderByChain(fd, nodeKey,
                    applicantDeptId(applicantId), parseLevel(match.getAssigneeValue()));
            case ApprovalConstants.ASSIGNEE_DEPT_LEADER -> resolveLeaderByChain(fd, nodeKey,
                    parseFirstId(match.getAssigneeValue()), 1);
            // 申请人本人：典型场景是「提交后自己复核」。
            // 注意这会让申请人立刻在待办里看到自己提交的单子（任务按候选人派发），
            // 属已确认的产品行为，运行期不拦截。
            case ApprovalConstants.ASSIGNEE_APPLICANT ->
                    applicantId == null ? List.<Long>of() : List.of(applicantId);
            // ASSIGNEE_UNSET(0)：导入时未约定审批人、或设计器里尚未补配的节点
            default -> {
                log.warn("流程 {} 节点 {} 的审批人规则为「未配置」，任务将无人可处理", fd.getFlowCode(), nodeKey);
                yield List.of();
            }
        };
    }

    /**
     * 部门主管解析（统一入口）：从 startDept 起沿部门父链收集「各层级主管」，返回第 level 级主管。
     *
     * <p><b>两种用法</b>：</p>
     * <ul>
     *   <li>发起人部门主管（LEADER）：startDept = 申请人所在部门，层级由配置决定（默认 1 即直属主管）；</li>
     *   <li>指定部门主管（DEPT_LEADER）：startDept = 配置指定的部门 id，层级固定 1。</li>
     * </ul>
     * <p>level 超过实际层级时取最顶层主管（避免「高层部门没配主管 → 任务无人处理」的静默空转）。
     * 整条链都无主管时兜底超管（与 ROLE 策略一致口径），保证单据不卡死。</p>
     */
    public List<Long> resolveLeaderByChain(FlowDefinition fd, String nodeKey, Long startDept, int level) {
        List<Long> chain = new ArrayList<>();
        Long cur = startDept;
        Set<Long> guard = new HashSet<>();
        for (int depth = 0; depth < 20 && cur != null && guard.add(cur); depth++) {
            Department d = departmentMapper.selectById(cur);
            if (d == null) {
                break;
            }
            if (d.getLeaderId() != null) {
                chain.add(d.getLeaderId());
            }
            Long parent = d.getParentId();
            cur = (parent != null && parent != 0L) ? parent : null;
        }
        if (chain.isEmpty()) {
            List<Long> superAdmins = selectSuperAdminIds();
            if (!superAdmins.isEmpty()) {
                log.warn("流程 {} 节点 {} 部门链无主管，已兜底为 SUPER_ADMIN（{} 人）",
                        fd.getFlowCode(), nodeKey, superAdmins.size());
                return superAdmins;
            }
            log.warn("流程 {} 节点 {} 部门链无主管且 SUPER_ADMIN 也无可用账号，任务将无人处理",
                    fd.getFlowCode(), nodeKey);
            return List.of();
        }
        int idx = Math.min(Math.max(level, 1) - 1, chain.size() - 1);
        return List.of(chain.get(idx));
    }

    public List<NodeConfigItem> parseNodeConfig(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<NodeConfigItem>>() {
            });
        } catch (Exception e) {
            log.warn("解析 node_config 失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 解析逗号分隔的 id 串（支持一个节点配置多个审批人/角色），非法项忽略 */
    public List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> selectSuperAdminIds() {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, ApprovalConstants.SUPER_ADMIN_ROLE_ID)).stream()
                .map(UserRole::getUserId).distinct().toList();
    }

    private Long applicantDeptId(Long applicantId) {
        if (applicantId == null) {
            return null;
        }
        User u = userMapper.selectById(applicantId);
        return u != null ? u.getDeptId() : null;
    }

    /** 审批层级：LEADER 的 assigneeValue 解析为层级数，非法/缺省按 1（直属主管） */
    private int parseLevel(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Long parseFirstId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
