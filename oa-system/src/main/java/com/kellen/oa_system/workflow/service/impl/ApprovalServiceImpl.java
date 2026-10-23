package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Company;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.CompanyMapper;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import com.kellen.oa_system.workflow.dto.ApprovalCcRequest;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.dto.FlowConditionItem;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.ApprovalCc;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalCcMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.ApprovalService;
import com.kellen.oa_system.workflow.callback.ApprovalCallbackRegistry;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.util.BpmnXmlNormalizer;
import com.kellen.oa_system.workflow.service.NodeFieldPermissionService;
import com.kellen.oa_system.workflow.service.NodeFormService;
import com.kellen.oa_system.workflow.vo.ApprovalCcVO;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.CcUserVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;
import com.kellen.oa_system.workflow.vo.NodeFormVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
            1, "草稿", 2, "审批中", 3, "已通过", 4, "已驳回", 5, "已撤销", 6, "已终止", 7, "已挂起");
    private static final Map<String, String> ACTION_NAME = Map.of(
            "submit", "提交", "approve", "同意", "reject", "驳回", "transfer", "转办",
            "withdraw", "撤销", "terminate", "终止", "suspend", "挂起", "resume", "恢复", "cc", "抄送");

    /**
     * 审批实例状态机：1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回 / 5 已撤销。
     *
     * <p>合法迁移仅四条：{@code 2 →(approve 未结束) 2}、{@code 2 →(approve 结束) 3}、
     * {@code 2 →(reject) 4}、{@code 2 →(withdraw) 5}；其余组合一律被状态机拒绝
     * （撤回见 {@link #withdraw}，重复审批见 {@link #complete}）。</p>
     */
    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_APPROVING = 2;
    private static final int STATUS_PASSED = 3;
    private static final int STATUS_REJECTED = 4;
    private static final int STATUS_WITHDRAWN = 5;
    /** 已终止：由管理员/拥有实例权限者在审批中强制终止（区别于「已撤销」=发起人主动撤销） */
    private static final int STATUS_TERMINATED = 6;
    /**
     * 已挂起：流程实例在引擎侧被 suspend，任务暂时退出候选人待办。
     *
     * <p>与「已终止」不同：挂起是可逆的暂停，恢复后回到 {@link #STATUS_APPROVING}；
     * 引擎里的任务、候选人、流程变量都原样保留。</p>
     */
    private static final int STATUS_SUSPENDED = 7;

    /** 到期动作：仅标记（默认） */
    private static final int TIMEOUT_ACTION_MARK = 0;
    /** 到期动作：提醒（写一条超时流转记录） */
    private static final int TIMEOUT_ACTION_REMIND = 1;
    /** 到期动作：自动驳回 */
    private static final int TIMEOUT_ACTION_REJECT = 2;

    /** 审批人规则类型：0未配置 1指定人员 2指定角色 3部门主管 4申请人 */
    private static final int ASSIGNEE_UNSET = 0;
    private static final int ASSIGNEE_USER = 1;
    private static final int ASSIGNEE_ROLE = 2;
    private static final int ASSIGNEE_LEADER = 3;
    /** 指定部门主管：取某个固定部门（assigneeValue 为部门 id）的主管，与「发起人部门主管」区分 */
    private static final int ASSIGNEE_DEPT_LEADER = 5;
    /**
     * 申请人本人。
     *
     * <p>典型场景是「提交后自己复核」「自我申报类流程」。<b>副作用已确认保留</b>：
     * 任务按候选人机制派发，申请人会立刻在自己的待办里看到这条单子（即"自己审自己"）。
     * 是否使用由业务在设计器里自行把控，运行期不做拦截。</p>
     */
    private static final int ASSIGNEE_APPLICANT = 4;

    /** 分支命中日志的动作标识：与 submit/approve/reject 等真实审批动作区分，详情展示时按此过滤 */
    private static final String ACTION_BRANCH = "branch";

    /** oa_approval_record.opinion 为 VARCHAR(500)，留 20 字符余量避免插入被截断报错 */
    private static final int BRANCH_DETAIL_MAX = 480;

    /**
     * 流程内建变量名，禁止被表单字段覆盖。
     *
     * <p>表单字段会被平铺成流程变量，若表单里恰好有同名字段（如 {@code title}），
     * 覆盖后会导致标题错乱或条件表达式引用到错误值。</p>
     *
     * <p>此处同时纳入「分支条件」用到的系统内置变量：发起人部门/角色、上节点审批结果。
     * 它们由本类在发起与审批时写入，必须优先于表单字段。</p>
     */
    private static final Set<String> RESERVED_VARS = Set.of(
            "formData", "applicantId", "title",
            "applicantDeptId", "applicantDeptName", "applicantRoleCodes",
            "outcome", "approved", "opinion", "lastOutcome", "lastApproved");

    /** 超管角色 id（与 oa_role.id=1 / role_code='SUPER_ADMIN' 对齐）。审批节点角色下无用户时兜底给超管处理 */
    private static final long SUPER_ADMIN_ROLE_ID = 1L;

    /** 超管角色编码（与 oa_role.role_code 对齐） */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    /** 「流程实例」总览页权限码（菜单 218）：持有者可在该页查看任意单据详情 */
    private static final String PERM_INSTANCE = "system:approval:instance";

    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalCcMapper ccMapper;
    private final FlowableEngineService engine;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    /** 仅用于把部门上的 companyId 翻译成公司名（审批列表「所属公司」列） */
    private final CompanyMapper companyMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;

    /**
     * 节点未单独配置超时时的默认小时数（阶段四）。
     *
     * <p>0 表示不启用超时。单节点可用 {@code node_config.timeoutHours} 覆盖。</p>
     */
    @Value("${approval.timeout.hours:0}")
    private int defaultTimeoutHours;
    /** 用于下发发起环节的主表单规则，供审批人在详情页只读渲染 */
    private final NodeFormService nodeFormService;

    /** 审批终态回调分发：业务表（请假/加班/离职…）由各自实现回写，引擎不感知业务 */
    private final ApprovalCallbackRegistry approvalCallbackRegistry;
    /** 用于下发当前节点的可编辑字段 */
    private final NodeFieldPermissionService fieldPermissionService;

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
     *
     * <p><b>变量名安全</b>：跳过与流程内建变量同名或非法标识符的键（如以数字开头、
     * 含空格），避免覆盖 {@code formData}/{@code applicantId}/{@code title}
     * 或造成表达式解析失败。</p>
     */
    private Map<String, Object> flattenFormData(String formDataJson) {
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
            if (!isSafeVariableName(key) || RESERVED_VARS.contains(key)) {
                return;
            }
            flat.put(key, normalizeVariableValue(value));
        });
        return flat;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        FlowDefinition fd = requireEnabledFlow(req.getFlowCode());
        ApprovalInstance inst = new ApprovalInstance();
        inst.setFlowDefId(fd.getId());
        inst.setFlowCode(fd.getFlowCode());
        inst.setTitle(req.getTitle());
        inst.setApplicantId(applicantId);
        inst.setFormData(req.getFormData());
        inst.setStatus(STATUS_APPROVING);
        instanceMapper.insert(inst);
        applyBillCode(inst);
        launch(inst, fd);
        return inst.getId();
    }

    /**
     * 保存草稿：只落审批单，不启动流程引擎（不产生待办）。
     *
     * <p>单据编号此时就生成，保证「保存 → 稍后提交」看到的是同一个编号。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        FlowDefinition fd = requireEnabledFlow(req.getFlowCode());
        ApprovalInstance inst = new ApprovalInstance();
        inst.setFlowDefId(fd.getId());
        inst.setFlowCode(fd.getFlowCode());
        inst.setTitle(req.getTitle());
        inst.setApplicantId(applicantId);
        inst.setFormData(req.getFormData());
        inst.setStatus(STATUS_DRAFT);
        instanceMapper.insert(inst);
        applyBillCode(inst);
        return inst.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long instanceId, ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (!Objects.equals(inst.getApplicantId(), applicantId)) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        if (!Objects.equals(inst.getStatus(), STATUS_DRAFT)) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_DONE, "仅草稿状态的申请可以修改");
        }
        // 显式 SET：只改标题与表单内容，不动编号/流程编码/申请人
        instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, instanceId)
                .set(ApprovalInstance::getTitle, req.getTitle())
                .set(ApprovalInstance::getFormData, req.getFormData())
                .set(ApprovalInstance::getUpdateTime, LocalDateTime.now()));
    }

    /** 提交草稿：把已存在的草稿送进流程（仅本人、仅草稿状态）。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitDraft(Long instanceId) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (!Objects.equals(inst.getApplicantId(), applicantId)) {
            // 不暴露「别人的草稿是否存在」
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        if (!Objects.equals(inst.getStatus(), STATUS_DRAFT)) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_DONE, "仅草稿状态的申请可以提交");
        }
        FlowDefinition fd = requireEnabledFlow(inst.getFlowCode());
        inst.setStatus(STATUS_APPROVING);
        instanceMapper.updateById(inst);
        launch(inst, fd);
    }

    /** 取「启用且已部署」的流程模板：发起申请与提交草稿共用同一套校验 */
    private FlowDefinition requireEnabledFlow(String flowCode) {
        FlowDefinition fd = flowDefinitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getFlowCode, flowCode)
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
        return fd;
    }

    /**
     * 生成并回写单据编号：{流程编码}-{yyyyMMdd}-{四位实例号}。
     *
     * <p>用自增主键做序号：无需额外序列表、天然唯一且不复用；四位左补零，超过 9999 自然进位。</p>
     */
    private void applyBillCode(ApprovalInstance inst) {
        String code = String.format("%s-%s-%04d",
                inst.getFlowCode() == null ? "OA" : inst.getFlowCode().toUpperCase(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                inst.getId());
        inst.setBillCode(code);
        instanceMapper.updateById(inst);
    }

    /** 启动引擎并把首个节点挂上候选人：发起申请与提交草稿共用 */
    private void launch(ApprovalInstance inst, FlowDefinition fd) {
        Long applicantId = inst.getApplicantId();
        Map<String, Object> vars = new HashMap<>();
        vars.put("formData", inst.getFormData());
        vars.put("applicantId", applicantId);
        vars.put("title", inst.getTitle());
        // 系统内置变量（分支条件用）：先写内置变量，flattenFormData 会跳过保留键，二者不互相覆盖
        vars.putAll(buildApplicantContextVars(applicantId));
        vars.putAll(flattenFormData(inst.getFormData()));
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

        Task firstTask = assignCurrentTasks(inst, fd);
        // 首个节点也可能经网关路由而来（发起即分流），一并记录命中分支
        recordBranchHit(inst, fd, firstTask == null ? null : firstTask.getTaskDefinitionKey());
    }

    /**
     * 构造发起人维度的系统内置变量，供分支条件引用。
     *
     * <p>三个变量各自解决一类路由需求：
     * <ul>
     *   <li>{@code applicantDeptId}/{@code applicantDeptName}：按部门分流（如「技术部走 A 线」）；</li>
     *   <li>{@code applicantRoleCodes}：按角色分流。一个用户可有多个角色，故用逗号拼接
     *       的编码串，条件里用「包含」判断（如 {@code applicantRoleCodes 包含 HR}）。</li>
     * </ul>
     * 查不到部门/角色时写空串而不是不写：JUEL 里引用未定义变量会取值失败，
     * 空串至少能让「为空 / 不为空」这类判断稳定成立。</p>
     */
    private Map<String, Object> buildApplicantContextVars(Long applicantId) {
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

    @Override
    public PageResult<ApprovalTodoVO> todo(int pageNo, int pageSize) {
        pageNo = safePageNo(pageNo);
        pageSize = safePageSize(pageSize);
        Long userId = SecurityContextUtil.getCurrentUserId();
        long total = engine.countCandidateTasks(String.valueOf(userId));
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        List<Task> tasks = engine.listCandidateTasks(String.valueOf(userId), (pageNo - 1) * pageSize, pageSize);
        List<ApprovalTodoVO> vos = new ArrayList<>();
        // instanceId → applicantId：循环结束后一次性解析部门/公司，避免逐行查库
        Map<Long, Long> applicantOfInstance = new HashMap<>();
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
            applicantOfInstance.put(inst.getId(), inst.getApplicantId());
        }
        Map<Long, String[]> org = resolveOrg(new ArrayList<>(applicantOfInstance.values()));
        for (ApprovalTodoVO vo : vos) {
            String[] names = org.get(applicantOfInstance.get(vo.getInstanceId()));
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    public PageResult<ApprovalRecordVO> done(int pageNo, int pageSize) {
        pageNo = safePageNo(pageNo);
        pageSize = safePageSize(pageSize);
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
        // 批量取单据（原本 toRecordVO 内部逐条查，这里改成一次批量，顺带拿到申请人用于解析部门/公司）
        Set<Long> instIds = new HashSet<>();
        for (ApprovalRecord r : page.getRecords()) {
            instIds.add(r.getInstanceId());
        }
        Map<Long, ApprovalInstance> instMap = new HashMap<>();
        if (!instIds.isEmpty()) {
            for (ApprovalInstance inst : instanceMapper.selectBatchIds(instIds)) {
                instMap.put(inst.getId(), inst);
            }
        }
        List<Long> applicants = new ArrayList<>();
        for (ApprovalInstance inst : instMap.values()) {
            applicants.add(inst.getApplicantId());
        }
        Map<Long, String[]> org = resolveOrg(applicants);
        List<ApprovalRecordVO> vos = new ArrayList<>();
        for (ApprovalRecord r : page.getRecords()) {
            ApprovalInstance inst = instMap.get(r.getInstanceId());
            ApprovalRecordVO vo = toRecordVO(r, inst);
            String[] names = inst == null ? null : org.get(inst.getApplicantId());
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
            vos.add(vo);
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    public PageResult<ApprovalInstanceVO> mine(Integer status, int pageNo, int pageSize) {
        pageNo = safePageNo(pageNo);
        pageSize = safePageSize(pageSize);
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
        List<ApprovalInstanceVO> vos = toInstanceVOs(page.getRecords());
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    public PageResult<ApprovalInstanceVO> page(String title, String applicantName, Integer status,
            String flowCode, String beginTime, String endTime, int pageNo, int pageSize) {
        pageNo = safePageNo(pageNo);
        pageSize = safePageSize(pageSize);
        LambdaQueryWrapper<ApprovalInstance> q = new LambdaQueryWrapper<>();
        q.eq(ApprovalInstance::getIsDeleted, 0);
        if (status != null) {
            q.eq(ApprovalInstance::getStatus, status);
        }
        if (StringUtils.hasText(flowCode)) {
            q.eq(ApprovalInstance::getFlowCode, flowCode);
        }
        if (StringUtils.hasText(title)) {
            q.like(ApprovalInstance::getTitle, escapeLike(title));
        }
        if (StringUtils.hasText(applicantName)) {
            // 发起人姓名模糊：先按 real_name 命中用户，再用其 id 集合过滤实例。
            // 没有任何用户命中时直接返回空页，避免 in(空) 误查全表。
            // 转义 LIKE 通配符（\ % _）：避免用户输入的 % / _ 被当作通配符（如 applicantName=% 会命中全部）。
            List<Long> applicantIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getRealName, escapeLike(applicantName)))
                    .stream().map(User::getId).toList();
            if (applicantIds.isEmpty()) {
                return PageResult.empty((long) pageNo, (long) pageSize);
            }
            q.in(ApprovalInstance::getApplicantId, applicantIds);
        }
        LocalDateTime begin = parseBeginTime(beginTime);
        LocalDateTime end = parseEndTime(endTime);
        if (begin != null) {
            q.ge(ApprovalInstance::getCreateTime, begin);
        }
        if (end != null) {
            q.le(ApprovalInstance::getCreateTime, end);
        }
        q.orderByDesc(ApprovalInstance::getId);
        long total = instanceMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalInstance> page = new Page<>(pageNo, pageSize);
        instanceMapper.selectPage(page, q);
        List<ApprovalInstanceVO> vos = toInstanceVOs(page.getRecords());
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    /**
     * 管理员/拥有实例权限者强制终止一个审批中的流程。
     *
     * <p>与「撤销」区别：撤销是发起人本人撤回自己的单（{@link #withdraw}），
     * 此处是具备实例管理权限的人，对任意审批中单据执行强停，状态记为 {@code 6 已终止}。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        // 挂起态也允许终止：挂起只是暂停，若不先恢复就无法处置会让单据长期悬空
        if (inst.getStatus() != STATUS_APPROVING && inst.getStatus() != STATUS_SUSPENDED) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_DONE, "仅审批中或已挂起的流程可以终止");
        }
        if (inst.getProcInstId() != null && engine.isProcessActive(inst.getProcInstId())) {
            engine.deleteProcessInstance(inst.getProcInstId(), "管理员终止");
        }
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setAction("terminate");
        rec.setHandlerId(userId);
        rec.setNodeName("终止");
        rec.setOpinion("管理员终止流程");
        recordMapper.insert(rec);
        finishInstance(inst, STATUS_TERMINATED);
    }

    /**
     * 挂起审批中的流程（阶段四）。
     *
     * <p>引擎侧 suspend 后，该实例的任务不再出现在候选人待办里；实例状态置 7 已挂起，
     * 列表/详情据此展示「已挂起」而非「审批中」，避免「列表说审批中、待办却查不到」的困惑。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspend(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (inst.getStatus() == STATUS_SUSPENDED) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_SUSPENDED);
        }
        if (inst.getStatus() != STATUS_APPROVING) {
            throw new BizException(ErrorCode.INSTANCE_CANNOT_SUSPEND);
        }
        if (inst.getProcInstId() != null) {
            // 顺序很关键：必须**先摘候选人、再 suspend**——引擎会拒绝修改已挂起任务的 identity link
            // （Cannot execute operation for a suspended Task）。摘除后待办自然消失，
            // 因为 Flowable 的候选任务查询不会排除挂起实例下的任务。
            engine.listTasks(inst.getProcInstId()).forEach(t -> engine.clearCandidateUsers(t.getId()));
            engine.suspendProcessInstance(inst.getProcInstId());
        }
        saveStatus(inst, STATUS_SUSPENDED);
        insertRecord(inst, "suspend", userId, "挂起", "管理员挂起流程");
    }

    /**
     * 恢复已挂起的流程（阶段四）。
     *
     * <p>引擎侧 activate 后，原任务重新回到候选人待办；不重建任务、不重算审批人，
     * 因此挂起期间的人员/变量变化不会影响恢复结果。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (inst.getStatus() != STATUS_SUSPENDED) {
            throw new BizException(ErrorCode.INSTANCE_NOT_SUSPENDED);
        }
        if (inst.getProcInstId() != null) {
            engine.activateProcessInstance(inst.getProcInstId());
            // 恢复时按 node_config 重新解析候选人（挂起时被摘除），同时重置当前节点与超时计时
            assignCurrentTasks(inst, getFlowDef(inst));
        }
        saveStatus(inst, STATUS_APPROVING);
        insertRecord(inst, "resume", userId, "恢复", "管理员恢复流程");
    }

    /**
     * 扫描并处置到期审批（阶段四·到期自动动作）。
     *
     * <p><b>幂等</b>：只捞 {@code due_time <= now && timeout_handled_at IS NULL} 的记录，
     * 处理后写 {@code timeout_handled_at}；进入下一节点时该字段被重置，故每个节点最多触发一次。</p>
     *
     * <p><b>事务</b>：整批一个事务。量级极小（只含已到期未处置），单条异常整批回滚、
     * 下一分钟重试，比「部分成功」更容易推理。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalInstance> due = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, STATUS_APPROVING)
                .isNotNull(ApprovalInstance::getDueTime)
                .le(ApprovalInstance::getDueTime, now)
                .isNull(ApprovalInstance::getTimeoutHandledAt));
        if (due.isEmpty()) {
            return 0;
        }
        for (ApprovalInstance inst : due) {
            // currentNode 存的是节点「名称」，动作配置挂在节点 id 上，故从引擎任务取 taskDefinitionKey
            String nodeId = inst.getProcInstId() == null ? null
                    : engine.listTasks(inst.getProcInstId()).stream()
                            .findFirst().map(Task::getTaskDefinitionKey).orElse(null);
            int action = timeoutActionOf(getFlowDef(inst), nodeId);
            if (action == TIMEOUT_ACTION_REJECT) {
                insertRecord(inst, "timeout", null, "超时", "已超时，系统自动驳回");
                if (inst.getProcInstId() != null) {
                    engine.deleteProcessInstance(inst.getProcInstId(), "超时自动驳回");
                }
                finishInstance(inst, STATUS_REJECTED);
                continue;
            }
            insertRecord(inst, "timeout", null, "超时",
                    action == TIMEOUT_ACTION_REMIND ? "已超时，已提醒当前审批人" : "已超时");
            instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                    .eq(ApprovalInstance::getId, inst.getId())
                    .set(ApprovalInstance::getTimeoutHandledAt, now)
                    .set(ApprovalInstance::getUpdateTime, now));
        }
        log.info("审批超时任务：本轮处置 {} 条", due.size());
        return due.size();
    }

    /** 取某节点的到期动作：{@code node_config.timeoutAction}，缺省 0（仅标记） */
    private int timeoutActionOf(FlowDefinition fd, String nodeId) {
        if (fd != null && nodeId != null) {
            for (NodeConfigItem item : parseNodeConfig(fd.getNodeConfig())) {
                if (nodeId.equals(item.getNodeId()) && item.getTimeoutAction() != null) {
                    return Math.max(item.getTimeoutAction(), 0);
                }
            }
        }
        return TIMEOUT_ACTION_MARK;
    }

    /**
     * 抄送：把审批单抄送给若干人。
     *
     * <p>抄送只用于告知：不产待办、不参与流转、不影响候选人。同一单据对同一人只抄送一次
     * （已抄送的自动跳过），事务内写一条 {@code action='cc'} 的流转记录留痕。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cc(ApprovalCcRequest req) {
        Long operatorId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(req.getInstanceId());
        List<Long> targets = req.getUserIds().stream().filter(Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return;
        }
        // 只保留真实存在的用户，避免脏 id 落库后详情查不到人
        List<Long> existing = userMapper.selectBatchIds(targets).stream().map(User::getId).toList();
        if (existing.isEmpty()) {
            return;
        }
        Set<Long> already = new HashSet<>();
        for (ApprovalCc c : ccMapper.selectList(new LambdaQueryWrapper<ApprovalCc>()
                .eq(ApprovalCc::getInstanceId, inst.getId())
                .in(ApprovalCc::getUserId, existing))) {
            already.add(c.getUserId());
        }
        int added = 0;
        for (Long uid : existing) {
            if (already.contains(uid)) {
                continue;
            }
            ApprovalCc cc = new ApprovalCc();
            cc.setInstanceId(inst.getId());
            cc.setUserId(uid);
            cc.setCcBy(operatorId);
            cc.setRemark(req.getRemark());
            cc.setReadFlag(0);
            ccMapper.insert(cc);
            added++;
        }
        if (added > 0) {
            insertRecord(inst, "cc", operatorId, "抄送",
                    StringUtils.hasText(req.getRemark()) ? req.getRemark() : "抄送 " + added + " 人");
        }
    }

    @Override
    public PageResult<ApprovalCcVO> ccPage(Integer readFlag, int pageNo, int pageSize) {
        pageNo = safePageNo(pageNo);
        pageSize = safePageSize(pageSize);
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<ApprovalCc> q = new LambdaQueryWrapper<ApprovalCc>()
                .eq(ApprovalCc::getUserId, userId)
                .orderByDesc(ApprovalCc::getId);
        if (readFlag != null) {
            q.eq(ApprovalCc::getReadFlag, readFlag);
        }
        long total = ccMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalCc> page = new Page<>(pageNo, pageSize);
        ccMapper.selectPage(page, q);
        // 批量取单据、申请人组织信息、抄送人姓名，避免逐行查库
        Set<Long> instIds = new HashSet<>();
        Set<Long> byIds = new HashSet<>();
        for (ApprovalCc c : page.getRecords()) {
            instIds.add(c.getInstanceId());
            if (c.getCcBy() != null) {
                byIds.add(c.getCcBy());
            }
        }
        Map<Long, ApprovalInstance> instMap = new HashMap<>();
        for (ApprovalInstance inst : instanceMapper.selectBatchIds(instIds)) {
            instMap.put(inst.getId(), inst);
        }
        List<Long> applicants = new ArrayList<>();
        for (ApprovalInstance inst : instMap.values()) {
            applicants.add(inst.getApplicantId());
        }
        Map<Long, String[]> org = resolveOrg(applicants);
        Map<Long, String> byNames = new HashMap<>();
        if (!byIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(byIds)) {
                byNames.put(u.getId(), u.getRealName());
            }
        }
        List<ApprovalCcVO> vos = new ArrayList<>();
        for (ApprovalCc c : page.getRecords()) {
            ApprovalInstance inst = instMap.get(c.getInstanceId());
            if (inst == null) {
                continue;
            }
            ApprovalCcVO vo = new ApprovalCcVO();
            vo.setId(c.getId());
            vo.setInstanceId(inst.getId());
            vo.setTitle(inst.getTitle());
            vo.setFlowName(flowNameOf(inst));
            vo.setApplicantName(userName(inst.getApplicantId()));
            String[] names = org.get(inst.getApplicantId());
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
            vo.setCurrentNode(inst.getCurrentNode());
            vo.setStatus(inst.getStatus());
            vo.setStatusName(STATUS_NAME.get(inst.getStatus()));
            vo.setCcByName(byNames.get(c.getCcBy()));
            vo.setRemark(c.getRemark());
            vo.setReadFlag(c.getReadFlag());
            vo.setReadTime(c.getReadTime() == null ? null : FMT.format(c.getReadTime()));
            vo.setCreateTime(fmt(c.getCreateTime()));
            vos.add(vo);
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readCc(Long ccId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalCc cc = ccMapper.selectById(ccId);
        // 只允许标记自己的抄送：越权访问按「不存在」处理，避免暴露他人数据存在性
        if (cc == null || !Objects.equals(cc.getUserId(), userId)) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "抄送记录不存在");
        }
        if (Objects.equals(cc.getReadFlag(), 1)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ccMapper.update(null, new LambdaUpdateWrapper<ApprovalCc>()
                .eq(ApprovalCc::getId, ccId)
                .set(ApprovalCc::getReadFlag, 1)
                .set(ApprovalCc::getReadTime, now)
                .set(ApprovalCc::getUpdateTime, now));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAllCc() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        ccMapper.update(null, new LambdaUpdateWrapper<ApprovalCc>()
                .eq(ApprovalCc::getUserId, userId)
                .eq(ApprovalCc::getReadFlag, 0)
                .set(ApprovalCc::getReadFlag, 1)
                .set(ApprovalCc::getReadTime, now)
                .set(ApprovalCc::getUpdateTime, now));
    }

    @Override
    public List<CcUserVO> ccUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId));
        Set<Long> deptIds = new HashSet<>();
        for (User u : users) {
            if (u.getDeptId() != null) {
                deptIds.add(u.getDeptId());
            }
        }
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department d : departmentMapper.selectBatchIds(deptIds)) {
                deptNames.put(d.getId(), d.getDeptName());
            }
        }
        List<CcUserVO> list = new ArrayList<>(users.size());
        for (User u : users) {
            CcUserVO vo = new CcUserVO();
            vo.setId(u.getId());
            vo.setRealName(u.getRealName());
            vo.setUsername(u.getUsername());
            vo.setDeptName(deptNames.get(u.getDeptId()));
            list.add(vo);
        }
        return list;
    }

    /**
     * 仅更新实例状态。
     *
     * <p>显式 SET + 显式刷新 {@code update_time}：流程系列表该列没有 ON UPDATE，
     * 且 MP 的 NOT_NULL 策略下用实体回写会漏字段（D2 的教训）。</p>
     */
    private void saveStatus(ApprovalInstance inst, int status) {
        instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, inst.getId())
                .set(ApprovalInstance::getStatus, status)
                .set(ApprovalInstance::getUpdateTime, LocalDateTime.now()));
    }

    /** 追加一条流转记录（挂起/恢复这类不挂在具体任务节点上的动作） */
    private void insertRecord(ApprovalInstance inst, String action, Long handlerId,
            String nodeName, String opinion) {
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setAction(action);
        rec.setHandlerId(handlerId);
        rec.setNodeName(nodeName);
        rec.setOpinion(opinion);
        recordMapper.insert(rec);
    }

    /**
     * 删除流程实例（逻辑删除）。
     *
     * <p>若实例仍在运行（审批中且 Flowable 运行态存在），先清理引擎运行态再删库，
     * 避免遗留「幽灵 ACT_RU_*」导致待办/引擎查询出现脏数据。删除走 {@code deleteById}，
     * 受 {@code AuditEntity} 的逻辑删除拦截，仅标记 {@code is_deleted=1}，不物理抹除。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInstance(Long instanceId) {
        ApprovalInstance inst = getInst(instanceId);
        if (inst.getStatus() == STATUS_APPROVING
                && inst.getProcInstId() != null
                && engine.isProcessActive(inst.getProcInstId())) {
            engine.deleteProcessInstance(inst.getProcInstId(), "删除实例");
        }
        instanceMapper.deleteById(inst.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(ApprovalCompleteRequest req) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        Task task = engine.getTask(req.getTaskId());
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        // 超管可处理任意节点（业务约定：超管兜底所有审批任务，避免节点无人处理导致单据卡死）
        if (!engine.isCandidate(req.getTaskId(), String.valueOf(userId)) && !isSuperAdmin()) {
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
        // 挂起态不允许审批：候选人已被摘除，且引擎也会拒绝，提前给出可读提示
        if (Objects.equals(inst.getStatus(), STATUS_SUSPENDED)) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_SUSPENDED, "审批单已挂起，请先恢复后再审批");
        }

        // 合并本节点可编辑字段的补丁（销假节点填销假信息等场景）。
        // 必须在 engine.complete 之前做：complete 之后流程可能已结束，实例不再是运行态。
        applyFormDataPatch(inst, task, req.getFormDataPatch());

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
        //
        // lastOutcome / lastApproved 与 outcome / approved 同值：从紧随其后的网关视角看，
        // 「上节点审批结果」就是本节点刚写入的结果，二者恒等。单独提供一份别名，
        // 是为了让设计器的变量下拉里能出现「上节点审批结果」这一项，配置时语义更直观。
        Map<String, Object> vars = new HashMap<>();
        vars.put("outcome", outcome);
        vars.put("approved", "approve".equals(outcome));
        vars.put("lastOutcome", outcome);
        vars.put("lastApproved", "approve".equals(outcome));
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
            finishInstance(inst, STATUS_REJECTED);
            return;
        }
        // 同意
        FlowDefinition fd = getFlowDef(inst);
        if (engine.isProcessActive(inst.getProcInstId())) {
            Task next = assignCurrentTasks(inst, fd);
            // 记录本次流转经过网关时命中的分支（变量快照 + 表达式 + 分支名）
            recordBranchHit(inst, fd, next == null ? null : next.getTaskDefinitionKey());
        } else {
            finishInstance(inst, STATUS_PASSED);
        }
    }

    /**
     * 把审批实例置为终结态（已通过 / 已驳回 / 已撤销），并清空当前节点。
     *
     * <p><b>为什么必须用 UpdateWrapper 而不能 {@code setCurrentNode(null) + updateById}</b>：
     * MyBatis-Plus 的 {@code updateById} 采用 NOT_NULL 更新策略——实体里为 null 的字段
     * 不会进 UPDATE 的 SET 段，{@code setCurrentNode(null)} 会被静默跳过，
     * 终结实例的 {@code current_node} 因而残留旧节点名（D2 缺陷，前端「我的申请 /
     * 详情」对已结束单据仍显示当前节点）。只有 UpdateWrapper 的显式 {@code SET} 才能写 NULL。</p>
     *
     * <p>同时显式刷新 {@code update_time}：MP 的自动填充只在传入实体时生效，
     * {@code update(null, wrapper)} 不触发填充，而流程系列表的该列没有
     * {@code ON UPDATE CURRENT_TIMESTAMP}，不显式写会导致审计时间停滞。</p>
     */
    private void finishInstance(ApprovalInstance inst, int finalStatus) {
        instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, inst.getId())
                .set(ApprovalInstance::getStatus, finalStatus)
                .set(ApprovalInstance::getCurrentNode, null)
                // 流程结束不再有「当前节点」，超时计时一并清空
                .set(ApprovalInstance::getDueTime, null)
                .set(ApprovalInstance::getUpdateTime, LocalDateTime.now()));
        // 业务回写：在同一事务内分发，回调失败会让审批动作一起回滚，
        // 避免「审批通过了但业务表还是审批中」；实例对象上的状态同步更新供回调判断
        inst.setStatus(finalStatus);
        inst.setCurrentNode(null);
        approvalCallbackRegistry.dispatch(inst, finalStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        if (!inst.getApplicantId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (inst.getStatus() != STATUS_APPROVING) {
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
        finishInstance(inst, STATUS_WITHDRAWN);
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

    /** 当前登录用户是否超级管理员（role_code=SUPER_ADMIN） */
    private boolean isSuperAdmin() {
        return SecurityContextUtil.getCurrentUser().getRoles().contains(SUPER_ADMIN_CODE);
    }

    /**
     * 当前用户能否查看任意流程实例（不受「参与人」限制）。
     *
     * <p>持有 {@code system:approval:instance}（流程实例总览页权限码）或为超管即可：该页面本身
     * 就是管理员总览，点开任意单据的详情是预期能力。</p>
     */
    private boolean canViewAnyInstance() {
        LoginUser u = SecurityContextUtil.getCurrentUser();
        return u.getPermissions().contains(PERM_INSTANCE) || u.getRoles().contains(SUPER_ADMIN_CODE);
    }

    @Override
    public ApprovalDetailVO detail(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = getInst(instanceId);
        // 流程实例总览权限 / 超管：可查看任意单据详情，不受「参与人」可见性限制（修复 403 误判）
        boolean viewAnyInstance = canViewAnyInstance();
        // 运行中实例的首个任务：既用于「候选人可见性」判定，也用于详情里的当前待办与「可审批」标记
        Task runningTask = null;
        if (inst.getStatus() == STATUS_APPROVING && inst.getProcInstId() != null) {
            List<Task> ts = engine.listTasks(inst.getProcInstId());
            if (!ts.isEmpty()) {
                runningTask = ts.get(0);
            }
        }
        boolean candidate = runningTask != null && engine.isCandidate(runningTask.getId(), String.valueOf(userId));
        if (!inst.getApplicantId().equals(userId) && !viewAnyInstance) {
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
        if (runningTask != null) {
            current = new ApprovalTodoVO();
            current.setTaskId(runningTask.getId());
            current.setInstanceId(inst.getId());
            current.setTitle(inst.getTitle());
            current.setFlowName(flowNameOf(inst));
            current.setApplicantName(userName(inst.getApplicantId()));
            current.setCurrentNode(runningTask.getName() != null ? runningTask.getName() : runningTask.getTaskDefinitionKey());
            current.setCreateTime(fmt(runningTask.getCreateTime()));
        }
        ApprovalDetailVO detail = new ApprovalDetailVO();
        detail.setInstance(ivo);
        detail.setRecords(rvo);
        detail.setCurrentTask(current);
        // 下发发起环节的主表单规则：审批人打开待办时用同一份表单只读渲染，
        // 避免每个节点各绑一份造成内容与排版漂移。
        detail.setFormRuleJson(primaryRuleJson(inst.getFlowCode()));
        // 当前节点的可编辑字段：未配置时为空集合（全部只读）。
        // 销假场景据此把「销假日期」等字段切成可编辑，员工填写后合并回同一份 form_data。
        detail.setEditableProps(current == null ? List.of() : editablePropsOf(inst, current.getTaskId()));
        // 是否可审批：候选人或超管（超管可处理任意节点）。申请人 / 实例总览用户只读详情，
        // 前端据此隐藏审批区，避免「看得见按钮、点了却 3104」。
        detail.setCanApprove(current != null && (candidate || isSuperAdmin()));
        return detail;
    }

    /** 取该审批单所属的流程定义；找不到返回 null（数据异常不应让详情页失败） */
    private FlowDefinition fdOf(ApprovalInstance inst) {
        return flowDefinitionMapper.selectById(inst.getFlowDefId());
    }

    /**
     * 把本节点可编辑字段的补丁合并进 {@code form_data}。
     *
     * <p><b>只接受授权字段</b>：补丁里混入未授权字段会被<b>静默丢弃</b>并记日志，
     * 而不是报错 —— 前端可能因状态过期提交了旧的权限配置，用报错阻断审批会很难排查。
     * 这条也是「审批人改不了申请人填的内容」的最后一道防线。</p>
     *
     * <p>同时同步更新流程变量，否则阶段三的条件表达式读到的仍是旧值。</p>
     */
    private void applyFormDataPatch(ApprovalInstance inst, Task task, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        FlowDefinition fd = fdOf(inst);
        if (fd == null) {
            return;
        }
        Long formId = primaryFormId(fd.getFlowCode());
        List<String> editable = fieldPermissionService.listEditableProps(
                fd.getId(), task.getTaskDefinitionKey(), formId);
        if (editable.isEmpty()) {
            log.warn("节点 {} 提交了表单补丁但无可编辑字段，已忽略: {}", task.getTaskDefinitionKey(), patch.keySet());
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
            log.warn("审批单 {} 的 form_data 解析失败，将以空对象继续", json, e);
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

    /**
     * 取当前任务所在节点的可编辑字段集合。
     *
     * <p>流程已结束（无当前任务）或流程定义缺失时返回空集合。</p>
     */
    private List<String> editablePropsOf(ApprovalInstance inst, String taskId) {
        FlowDefinition fd = fdOf(inst);
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

    /**
     * 取该流程发起环节的主表单规则 JSON。
     *
     * <p>取不到时返回 null（未绑定表单 / 绑定已解绑），前端会降级为键值对展示。</p>
     */
    private String primaryRuleJson(String flowCode) {
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

    // ------------------------- 内部辅助 -------------------------

    /**
     * 把当前流程实例的所有活动任务挂上候选人，并更新审批单当前节点。
     *
     * @return 首个活动任务（供分支命中日志反推落点）；无任务时返回 null
     */
    private Task assignCurrentTasks(ApprovalInstance inst, FlowDefinition fd) {
        List<Task> tasks = engine.listTasks(inst.getProcInstId());
        if (tasks.isEmpty()) {
            return null;
        }
        LocalDateTime dueTime = null;
        for (Task task : tasks) {
            List<Long> candidates = resolveCandidates(fd, inst.getApplicantId(), task.getTaskDefinitionKey());
            // 审批人以 node_config 为准，BPMN 里的 assignee 一律清空，避免任务被锁定后待办检索不到
            engine.clearAssignee(task.getId());
            engine.addCandidateUsers(task.getId(), candidates);
            // 阶段四·超时：节点配置优先，缺省回落全局默认；0 表示不启用
            int hours = timeoutHoursOf(fd, task.getTaskDefinitionKey());
            if (hours > 0) {
                LocalDateTime due = LocalDateTime.now().plusHours(hours);
                engine.setTaskDueDate(task.getId(), due);
                if (dueTime == null) {
                    dueTime = due;
                }
            }
        }
        Task first = tasks.get(0);
        inst.setCurrentNode(first.getName() != null ? first.getName() : first.getTaskDefinitionKey());
        // 显式 SET：无超时配置时必须把上一节点的 due_time 清空，updateById 的 NOT_NULL 策略写不进 null
        instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, inst.getId())
                .set(ApprovalInstance::getCurrentNode, inst.getCurrentNode())
                .set(ApprovalInstance::getDueTime, dueTime)
                // 进入新节点即重新计时：清空上一节点的超时处置标记，否则本节点到期会被当成「已处置」
                .set(ApprovalInstance::getTimeoutHandledAt, null)
                .set(ApprovalInstance::getUpdateTime, LocalDateTime.now()));
        return first;
    }

    /**
     * 取某节点的超时小时数：{@code node_config.timeoutHours} 优先，缺省回落全局
     * {@code approval.timeout.hours}；返回值 ≤0 表示不启用超时。
     */
    private int timeoutHoursOf(FlowDefinition fd, String nodeId) {
        if (fd != null && nodeId != null) {
            for (NodeConfigItem item : parseNodeConfig(fd.getNodeConfig())) {
                if (nodeId.equals(item.getNodeId()) && item.getTimeoutHours() != null) {
                    return Math.max(item.getTimeoutHours(), 0);
                }
            }
        }
        return Math.max(defaultTimeoutHours, 0);
    }

    /**
     * 分支命中日志：把「网关走了哪条分支、当时变量是什么」写进审批记录表。
     *
     * <p><b>为什么用业务侧反推而不是引擎监听器</b>：给每条连线注入 executionListener
     * 需要改动 BPMN 注入逻辑且要求引擎能加载监听器类，成本高、出错面大；
     * 而审批流转的落点（下一个任务节点 id）在 {@code flow_config} 里有明确记录，
     * 反推即可定位命中的分支，且完全可控。代价是「网关→网关」连续跳转
     * （中间不产生任务）只能记到最终落脚的分支——本项目当前不依赖该结构。</p>
     *
     * <p><b>落库位置</b>：复用 {@code oa_approval_record}，用 action='branch' 与真实审批动作区分，
     * 详情时间轴按需过滤。<b>不新增表</b>。</p>
     *
     * @param inst        审批单
     * @param fd          流程模板（取其 flow_config）
     * @param nextNodeId  流转后落到的任务节点 id（BPMN 的 taskDefinitionKey）
     */
    private void recordBranchHit(ApprovalInstance inst, FlowDefinition fd, String nextNodeId) {
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
        rec.setAction(ACTION_BRANCH);
        rec.setOpinion(truncate(detail, BRANCH_DETAIL_MAX));
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
     *
     * <p>生成失败（如存量脏数据里条件结构残缺）时返回 null 并记日志：<b>写日志这件事本身
     * 不该阻断审批流转</b>，最多是这条分支记录里少一段表达式。</p>
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
            case ASSIGNEE_LEADER -> resolveLeaderByChain(fd, nodeKey, applicantDeptId(applicantId),
                    parseLevel(match.getAssigneeValue()));
            case ASSIGNEE_DEPT_LEADER -> resolveLeaderByChain(fd, nodeKey,
                    parseFirstId(match.getAssigneeValue()), 1);
            // 申请人本人：典型场景是「提交后自己复核」。
            // 注意这会让申请人立刻在待办里看到自己提交的单子（任务按候选人派发），
            // 属已确认的产品行为，运行期不拦截。
            case ASSIGNEE_APPLICANT -> applicantId == null ? List.<Long>of() : List.of(applicantId);
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
     * <p><b>两种用法</b>：
     * <ul>
     *   <li>发起人部门主管（LEADER）：startDept = 申请人所在部门，层级由配置决定（默认 1 即直属主管）；</li>
     *   <li>指定部门主管（DEPT_LEADER）：startDept = 配置指定的部门 id，层级固定 1。</li>
     * </ul>
     * level 超过实际层级时取最顶层主管（避免「高层部门没配主管 → 任务无人处理」的静默空转）。
     * 整条链都无主管时兜底超管（与 ROLE 策略一致口径），保证单据不卡死。</p>
     */
    private List<Long> resolveLeaderByChain(FlowDefinition fd, String nodeKey, Long startDept, int level) {
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
            List<Long> superAdmins = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getRoleId, SUPER_ADMIN_ROLE_ID)).stream()
                    .map(UserRole::getUserId).distinct().toList();
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
        vo.setBillCode(inst.getBillCode());
        vo.setFlowName(flowNameOf(inst));
        vo.setTitle(inst.getTitle());
        vo.setApplicantId(inst.getApplicantId());
        vo.setApplicantName(userName(inst.getApplicantId()));
        vo.setStatus(inst.getStatus());
        vo.setStatusName(STATUS_NAME.get(inst.getStatus()));
        vo.setCurrentNode(inst.getCurrentNode());
        vo.setFormData(inst.getFormData());
        vo.setCreateTime(fmt(inst.getCreateTime()));
        if (inst.getDueTime() != null) {
            vo.setDueTime(FMT.format(inst.getDueTime()));
            // 仅「审批中」才可能逾期：挂起/已结束都不再计时
            vo.setOverdue(Objects.equals(inst.getStatus(), STATUS_APPROVING)
                    && inst.getDueTime().isBefore(LocalDateTime.now()));
        } else {
            vo.setOverdue(false);
        }
        return vo;
    }

    /**
     * 批量转 VO，并一次性补齐发起人的部门与公司。
     *
     * <p>列表页每行都要显示这两个字段，逐行查库会变成 3N 次查询，故按申请人集合批量解析。</p>
     */
    private List<ApprovalInstanceVO> toInstanceVOs(List<ApprovalInstance> list) {
        List<Long> applicants = new ArrayList<>();
        for (ApprovalInstance inst : list) {
            applicants.add(inst.getApplicantId());
        }
        Map<Long, String[]> org = resolveOrg(applicants);
        List<ApprovalInstanceVO> vos = new ArrayList<>(list.size());
        for (ApprovalInstance inst : list) {
            ApprovalInstanceVO vo = toInstanceVO(inst);
            String[] names = org.get(inst.getApplicantId());
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 批量解析「用户 → 所在部门 / 所属公司」。
     *
     * <p>用户 → 部门 → 公司各查一次（3 条 SQL），在内存里拼装；
     * 查不到的项留 null，由前端决定显示 `-`。</p>
     *
     * @return userId → [deptName, companyName]
     */
    private Map<Long, String[]> resolveOrg(List<Long> userIds) {
        Map<Long, String[]> org = new HashMap<>();
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return org;
        }
        List<User> users = userMapper.selectBatchIds(ids);
        if (users.isEmpty()) {
            return org;
        }
        Set<Long> deptIds = new HashSet<>();
        for (User u : users) {
            if (u.getDeptId() != null) {
                deptIds.add(u.getDeptId());
            }
        }
        Map<Long, Department> deptMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department d : departmentMapper.selectBatchIds(deptIds)) {
                deptMap.put(d.getId(), d);
            }
        }
        Set<Long> companyIds = new HashSet<>();
        for (Department d : deptMap.values()) {
            if (d.getCompanyId() != null) {
                companyIds.add(d.getCompanyId());
            }
        }
        Map<Long, String> companyMap = new HashMap<>();
        if (!companyIds.isEmpty()) {
            for (Company c : companyMapper.selectBatchIds(companyIds)) {
                companyMap.put(c.getId(), c.getCompanyName());
            }
        }
        for (User u : users) {
            Department d = u.getDeptId() == null ? null : deptMap.get(u.getDeptId());
            org.put(u.getId(), d == null
                    ? new String[]{null, null}
                    : new String[]{d.getDeptName(), companyMap.get(d.getCompanyId())});
        }
        return org;
    }

    /** 单条记录转 VO（详情时间轴用）：自行取单据，适合数据量小的场景 */
    private ApprovalRecordVO toRecordVO(ApprovalRecord r) {
        return toRecordVO(r, instanceMapper.selectById(r.getInstanceId()));
    }

    /** 列表用：单据由调用方批量预取后传入，避免逐条查询 */
    private ApprovalRecordVO toRecordVO(ApprovalRecord r, ApprovalInstance preloaded) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setInstanceId(r.getInstanceId());
        ApprovalInstance inst = preloaded != null ? preloaded : instanceMapper.selectById(r.getInstanceId());
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

    /** 时间范围入参解析（起始，缺时分秒补 00:00:00）；空值返回 null，格式非法抛 400 */
    private LocalDateTime parseBeginTime(String s) {
        return parseTime(s, " 00:00:00");
    }

    /** 时间范围入参解析（结束，缺时分秒补 23:59:59）；空值返回 null，格式非法抛 400 */
    private LocalDateTime parseEndTime(String s) {
        return parseTime(s, " 23:59:59");
    }

    /**
     * 时间入参统一解析：{@code <=10} 字符按纯日期补足时分秒。
     *
     * <p><b>为什么非法格式不再静默忽略</b>：此前解析失败返回 {@code null}（等同未传），会让「时间范围筛选」
     * 在用户填错日期时静默失效并返回全量结果，掩盖问题；改为抛 400 让错误即时可见。</p>
     */
    private LocalDateTime parseTime(String s, String timeSuffix) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            String v = s.length() <= 10 ? s + timeSuffix : s;
            return LocalDateTime.parse(v, FMT);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "时间格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 分页入参归一：页码 <1 归 1；页大小 <1 归默认 10（过大由 MyBatis-Plus 全局 maxLimit=1000 截断） */
    private static int safePageNo(int pageNo) {
        return pageNo < 1 ? 1 : pageNo;
    }

    private static int safePageSize(int pageSize) {
        return pageSize < 1 ? 10 : pageSize;
    }

    /** 转义 LIKE 通配符（{@code \ % _}）：避免用户输入被当作通配符（MySQL 默认以 {@code \} 为转义符） */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
