package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.ApprovalService;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 审批运行时实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li>审批人统一以「候选人」方式挂到 Flowable 任务上（不设置 assignee），便于用
 *       taskCandidateUser 做「待办」检索；节点审批人由模板 node_config 解析得到。</li>
 *   <li>每次动作写一条 {@link ApprovalRecord}，同时驱动「我的已办」与详情时间轴，
 *       避免直接依赖 Flowable 历史表做按候选人检索。</li>
 *   <li>状态机遵循项目计划：1草稿 2审批中 3已通过 4已驳回 5已撤销；驳回即终止流程。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<Integer, String> STATUS_NAME = Map.of(
            1, "草稿", 2, "审批中", 3, "已通过", 4, "已驳回", 5, "已撤销");
    private static final Map<String, String> ACTION_NAME = Map.of(
            "submit", "提交", "approve", "同意", "reject", "驳回", "transfer", "转办", "withdraw", "撤销");

    /** 审批人规则类型：0未配置 1指定人员 2指定角色 3部门主管 */
    private static final int ASSIGNEE_UNSET = 0;
    private static final int ASSIGNEE_USER = 1;
    private static final int ASSIGNEE_ROLE = 2;
    private static final int ASSIGNEE_LEADER = 3;

    /** 超管角色 id（与 oa_role.id=1 / role_code='SUPER_ADMIN' 对齐）。审批节点角色下无用户时兜底给超管处理 */
    private static final long SUPER_ADMIN_ROLE_ID = 1L;

    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final FlowableEngineService engine;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        FlowDefinition fd = flowDefinitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getFlowCode, req.getFlowCode())
                .eq(FlowDefinition::getIsDeleted, 0));
        if (fd == null) {
            throw new BizException(ErrorCode.FLOW_DEF_NOT_FOUND);
        }
        if (fd.getStatus() != 1) {
            throw new BizException(ErrorCode.FLOW_DEF_DISABLED);
        }
        if (fd.getDeploymentId() == null || fd.getDeploymentId().isBlank()) {
            throw new BizException(ErrorCode.FLOW_NOT_DEPLOYED);
        }

        ApprovalInstance inst = new ApprovalInstance();
        inst.setFlowDefId(fd.getId());
        inst.setFlowCode(fd.getFlowCode());
        inst.setTitle(req.getTitle());
        inst.setApplicantId(applicantId);
        inst.setFormData(req.getFormData());
        inst.setStatus(2);
        instanceMapper.insert(inst);

        Map<String, Object> vars = new HashMap<>();
        vars.put("formData", req.getFormData());
        vars.put("applicantId", applicantId);
        vars.put("title", req.getTitle());
        String procInstId = engine.startProcessInstance(procKey(fd), String.valueOf(inst.getId()), vars);
        inst.setProcInstId(procInstId);
        instanceMapper.updateById(inst);

        ApprovalRecord submit = new ApprovalRecord();
        submit.setInstanceId(inst.getId());
        submit.setAction("submit");
        submit.setHandlerId(applicantId);
        submit.setNodeName("提交申请");
        submit.setOpinion("发起申请");
        recordMapper.insert(submit);

        assignCurrentTasks(inst, fd);
        return inst.getId();
    }

    @Override
    public PageResult<ApprovalTodoVO> todo(int pageNo, int pageSize) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        long total = engine.countCandidateTasks(String.valueOf(userId));
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        List<Task> tasks = engine.listCandidateTasks(String.valueOf(userId), (pageNo - 1) * pageSize, pageSize);
        List<ApprovalTodoVO> vos = new ArrayList<>();
        for (Task t : tasks) {
            ApprovalInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                    .eq(ApprovalInstance::getProcInstId, t.getProcessInstanceId())
                    .eq(ApprovalInstance::getIsDeleted, 0));
            if (inst == null) {
                continue;
            }
            ApprovalTodoVO vo = new ApprovalTodoVO();
            vo.setTaskId(t.getId());
            vo.setInstanceId(inst.getId());
            vo.setTitle(inst.getTitle());
            vo.setFlowName(flowNameOf(inst));
            vo.setApplicantName(userName(inst.getApplicantId()));
            vo.setCurrentNode(t.getName() != null ? t.getName() : t.getTaskDefinitionKey());
            vo.setCreateTime(fmt(t.getCreateTime()));
            vos.add(vo);
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    public PageResult<ApprovalRecordVO> done(int pageNo, int pageSize) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<ApprovalRecord> q = new LambdaQueryWrapper<>();
        q.eq(ApprovalRecord::getHandlerId, userId)
                .in(ApprovalRecord::getAction, "approve", "reject", "transfer")
                .orderByDesc(ApprovalRecord::getCreateTime);
        long total = recordMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalRecord> page = new Page<>(pageNo, pageSize);
        recordMapper.selectPage(page, q);
        List<ApprovalRecordVO> vos = page.getRecords().stream().map(this::toRecordVO).toList();
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    public PageResult<ApprovalInstanceVO> mine(Integer status, int pageNo, int pageSize) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<ApprovalInstance> q = new LambdaQueryWrapper<>();
        q.eq(ApprovalInstance::getApplicantId, userId).eq(ApprovalInstance::getIsDeleted, 0);
        if (status != null) {
            q.eq(ApprovalInstance::getStatus, status);
        }
        q.orderByDesc(ApprovalInstance::getId);
        long total = instanceMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalInstance> page = new Page<>(pageNo, pageSize);
        instanceMapper.selectPage(page, q);
        List<ApprovalInstanceVO> vos = page.getRecords().stream().map(this::toInstanceVO).toList();
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(ApprovalCompleteRequest req) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        Task task = engine.getTask(req.getTaskId());
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!engine.isCandidate(req.getTaskId(), String.valueOf(userId))) {
            throw new BizException(ErrorCode.TASK_NOT_CANDIDATE);
        }
        String outcome = req.getAction();
        if (!"approve".equals(outcome) && !"reject".equals(outcome)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "审批动作仅支持 approve / reject");
        }

        ApprovalInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getProcInstId, task.getProcessInstanceId())
                .eq(ApprovalInstance::getIsDeleted, 0));
        if (inst == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }

        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setTaskId(task.getId());
        rec.setNodeId(task.getTaskDefinitionKey());
        rec.setNodeName(task.getName());
        rec.setHandlerId(userId);
        rec.setAction(outcome);
        rec.setOpinion(req.getOpinion());
        recordMapper.insert(rec);

        // 流程变量约定：outcome=approve/reject（字符串，推荐），同时提供布尔 approved 供设计器选用。
        // 网关条件可写 ${outcome == 'approve'} 或 ${approved}；驳回由本方法直接终止流程。
        Map<String, Object> vars = new HashMap<>();
        vars.put("outcome", outcome);
        vars.put("approved", "approve".equals(outcome));
        if (req.getOpinion() != null) {
            vars.put("opinion", req.getOpinion());
        }
        engine.complete(task.getId(), vars);

        if ("reject".equals(outcome)) {
            // 驳回即终止流程。若 BPMN 已把驳回分支连到结束事件，流程可能在 complete 时就已自然结束，
            // 此时实例已不存在，再删会抛 FlowableObjectNotFoundException，故先判断是否仍在运行。
            if (engine.isProcessActive(inst.getProcInstId())) {
                engine.deleteProcessInstance(inst.getProcInstId(), "驳回");
            }
            inst.setStatus(4);
            inst.setCurrentNode(null);
            instanceMapper.updateById(inst);
            return;
        }
        // 同意
        if (engine.isProcessActive(inst.getProcInstId())) {
            assignCurrentTasks(inst, getFlowDef(inst));
        } else {
            inst.setStatus(3);
            inst.setCurrentNode(null);
            instanceMapper.updateById(inst);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (!inst.getApplicantId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (inst.getStatus() != 2) {
            throw new BizException(ErrorCode.INSTANCE_CANNOT_WITHDRAW);
        }
        engine.deleteProcessInstance(inst.getProcInstId(), "撤销");
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setAction("withdraw");
        rec.setHandlerId(userId);
        rec.setNodeName("撤销");
        rec.setOpinion("发起人撤销");
        recordMapper.insert(rec);
        inst.setStatus(5);
        inst.setCurrentNode(null);
        instanceMapper.updateById(inst);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(ApprovalTransferRequest req) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        Task task = engine.getTask(req.getTaskId());
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!engine.isCandidate(req.getTaskId(), String.valueOf(userId))) {
            throw new BizException(ErrorCode.TASK_NOT_CANDIDATE);
        }
        engine.deleteCandidateUser(req.getTaskId(), userId);
        engine.addCandidateUsers(req.getTaskId(), List.of(req.getTargetUserId()));

        ApprovalInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getProcInstId, task.getProcessInstanceId())
                .eq(ApprovalInstance::getIsDeleted, 0));
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setTaskId(task.getId());
        rec.setNodeId(task.getTaskDefinitionKey());
        rec.setNodeName(task.getName());
        rec.setHandlerId(userId);
        rec.setAction("transfer");
        rec.setOpinion("转办给 " + userName(req.getTargetUserId()));
        recordMapper.insert(rec);
    }

    @Override
    public ApprovalDetailVO detail(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (!inst.getApplicantId().equals(userId)) {
            boolean candidate = false;
            if (inst.getStatus() == 2 && inst.getProcInstId() != null) {
                candidate = engine.listTasks(inst.getProcInstId()).stream()
                        .anyMatch(t -> engine.isCandidate(t.getId(), String.valueOf(userId)));
            }
            boolean handled = recordMapper.selectCount(new LambdaQueryWrapper<ApprovalRecord>()
                    .eq(ApprovalRecord::getInstanceId, inst.getId())
                    .eq(ApprovalRecord::getHandlerId, userId)) > 0;
            if (!candidate && !handled) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }

        ApprovalInstanceVO ivo = toInstanceVO(inst);
        List<ApprovalRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, inst.getId())
                .orderByAsc(ApprovalRecord::getCreateTime));
        List<ApprovalRecordVO> rvo = recs.stream().map(this::toRecordVO).toList();

        ApprovalTodoVO current = null;
        if (inst.getStatus() == 2 && inst.getProcInstId() != null) {
            List<Task> ts = engine.listTasks(inst.getProcInstId());
            if (!ts.isEmpty()) {
                Task t = ts.get(0);
                current = new ApprovalTodoVO();
                current.setTaskId(t.getId());
                current.setInstanceId(inst.getId());
                current.setTitle(inst.getTitle());
                current.setFlowName(flowNameOf(inst));
                current.setApplicantName(userName(inst.getApplicantId()));
                current.setCurrentNode(t.getName() != null ? t.getName() : t.getTaskDefinitionKey());
                current.setCreateTime(fmt(t.getCreateTime()));
            }
        }
        ApprovalDetailVO detail = new ApprovalDetailVO();
        detail.setInstance(ivo);
        detail.setRecords(rvo);
        detail.setCurrentTask(current);
        return detail;
    }

    // ------------------------- 内部辅助 -------------------------

    /** 把当前流程实例的所有活动任务挂上候选人，并更新审批单当前节点 */
    private void assignCurrentTasks(ApprovalInstance inst, FlowDefinition fd) {
        List<Task> tasks = engine.listTasks(inst.getProcInstId());
        if (tasks.isEmpty()) {
            return;
        }
        for (Task task : tasks) {
            List<Long> candidates = resolveCandidates(fd, inst.getApplicantId(), task.getTaskDefinitionKey());
            // 审批人以 node_config 为准，BPMN 里的 assignee 一律清空，避免任务被锁定后待办检索不到
            engine.clearAssignee(task.getId());
            engine.addCandidateUsers(task.getId(), candidates);
        }
        Task first = tasks.get(0);
        inst.setCurrentNode(first.getName() != null ? first.getName() : first.getTaskDefinitionKey());
        instanceMapper.updateById(inst);
    }

    /** 按 node_config 解析某节点的候选人用户 id 列表 */
    private List<Long> resolveCandidates(FlowDefinition fd, Long applicantId, String nodeKey) {
        List<NodeConfigItem> items = parseNodeConfig(fd.getNodeConfig());
        NodeConfigItem match = items.stream()
                .filter(i -> nodeKey != null && nodeKey.equals(i.getNodeId()))
                .findFirst().orElse(null);
        if (match == null || match.getAssigneeType() == null) {
            log.warn("流程 {} 节点 {} 未配置审批人规则，任务将无人可处理", fd.getFlowCode(), nodeKey);
            return List.of();
        }
        return switch (match.getAssigneeType()) {
            case ASSIGNEE_USER -> parseIds(match.getAssigneeValue());
            case ASSIGNEE_ROLE -> {
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
                    List<Long> superAdmins = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                                    .eq(UserRole::getRoleId, SUPER_ADMIN_ROLE_ID)).stream()
                            .map(UserRole::getUserId).distinct().toList();
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
            case ASSIGNEE_LEADER -> {
                User u = userMapper.selectById(applicantId);
                if (u != null && u.getDeptId() != null) {
                    Department d = departmentMapper.selectById(u.getDeptId());
                    if (d != null && d.getLeaderId() != null) {
                        yield List.of(d.getLeaderId());
                    }
                }
                yield List.of();
            }
            // ASSIGNEE_UNSET(0)：导入时未约定审批人、或设计器里尚未补配的节点
            default -> {
                log.warn("流程 {} 节点 {} 的审批人规则为「未配置」，任务将无人可处理", fd.getFlowCode(), nodeKey);
                yield List.of();
            }
        };
    }

    private List<NodeConfigItem> parseNodeConfig(String json) {
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

    private FlowDefinition getFlowDef(ApprovalInstance inst) {
        FlowDefinition fd = flowDefinitionMapper.selectById(inst.getFlowDefId());
        if (fd == null) {
            throw new BizException(ErrorCode.FLOW_DEF_NOT_FOUND);
        }
        return fd;
    }

    private ApprovalInstance getInst(Long id) {
        ApprovalInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, id)
                .eq(ApprovalInstance::getIsDeleted, 0));
        if (inst == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        return inst;
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        return u != null ? u.getRealName() : null;
    }

    private String flowNameOf(ApprovalInstance inst) {
        if (inst.getFlowDefId() != null) {
            FlowDefinition fd = flowDefinitionMapper.selectById(inst.getFlowDefId());
            if (fd != null) {
                return fd.getFlowName();
            }
        }
        return inst.getFlowCode();
    }

    private ApprovalInstanceVO toInstanceVO(ApprovalInstance inst) {
        ApprovalInstanceVO vo = new ApprovalInstanceVO();
        vo.setId(inst.getId());
        vo.setFlowCode(inst.getFlowCode());
        vo.setFlowName(flowNameOf(inst));
        vo.setTitle(inst.getTitle());
        vo.setApplicantId(inst.getApplicantId());
        vo.setApplicantName(userName(inst.getApplicantId()));
        vo.setStatus(inst.getStatus());
        vo.setStatusName(STATUS_NAME.get(inst.getStatus()));
        vo.setCurrentNode(inst.getCurrentNode());
        vo.setFormData(inst.getFormData());
        vo.setCreateTime(fmt(inst.getCreateTime()));
        return vo;
    }

    private ApprovalRecordVO toRecordVO(ApprovalRecord r) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setInstanceId(r.getInstanceId());
        ApprovalInstance inst = instanceMapper.selectById(r.getInstanceId());
        vo.setTitle(inst != null ? inst.getTitle() : null);
        vo.setFlowName(flowNameOf(inst));
        vo.setNodeName(r.getNodeName());
        vo.setHandlerName(userName(r.getHandlerId()));
        vo.setAction(r.getAction());
        vo.setActionName(ACTION_NAME.get(r.getAction()));
        vo.setOpinion(r.getOpinion());
        vo.setCreateTime(fmt(r.getCreateTime()));
        return vo;
    }

    private String fmt(LocalDateTime ldt) {
        return ldt == null ? null : FMT.format(ldt);
    }

    private String fmt(Date date) {
        return date == null ? null : FMT.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    /** 解析逗号分隔的 id 串（支持一个节点配置多个审批人/角色），非法项忽略 */
    private List<Long> parseIds(String csv) {
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

    /** 启动/查询流程统一用 BPMN 的 process id（bpmnKey），历史数据缺失时回退 flowCode */
    private String procKey(FlowDefinition fd) {
        return StringUtils.hasText(fd.getBpmnKey()) ? fd.getBpmnKey() : fd.getFlowCode();
    }
}
