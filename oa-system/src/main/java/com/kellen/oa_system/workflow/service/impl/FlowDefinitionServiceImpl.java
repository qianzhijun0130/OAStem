package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.workflow.dto.FlowConditionItem;
import com.kellen.oa_system.workflow.dto.FlowDefinitionImportRequest;
import com.kellen.oa_system.workflow.dto.FlowDefinitionPageQuery;
import com.kellen.oa_system.workflow.dto.FlowDefinitionSaveRequest;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.FlowDefinitionService;
import com.kellen.oa_system.workflow.service.FlowVariableService;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.util.BpmnXmlNormalizer;
import com.kellen.oa_system.workflow.util.BpmnXmlParser;
import com.kellen.oa_system.workflow.util.BpmnXmlValidator;
import com.kellen.oa_system.workflow.vo.FlowDefinitionVO;
import com.kellen.oa_system.workflow.vo.FlowOptionVO;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.exceptions.XMLException;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程模板管理实现。
 *
 * <p>保存模板时：bpmnXml 非空则部署到 Flowable，并把 deploymentId / bpmnKey 落库；
 * 更新时若重新上传 BPMN，会先卸载旧部署再部署新版本（同一 flowCode 保持最新版本）。
 * 删除模板走逻辑删除，并级联卸载 Flowable 部署。</p>
 *
 * <p>部署前一律对 BPMN 做归一化（{@link BpmnXmlNormalizer}）：把 {@code <process id>} 改写为流程编码，
 * 使引擎侧 key 与业务编码一一对应，避免不同模板共用默认 process id 时「选了 A 却跑 B」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

    /** 模板启用状态：1启用（停用为 0，停用后不可发起） */
    private static final int STATUS_ENABLED = 1;

    /** 审批实例的「未结束」状态：1草稿 / 2审批中（3已通过、4已驳回、5已撤销均属已结束） */
    private static final List<Integer> INSTANCE_ACTIVE_STATUS = List.of(1, 2);

    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final FlowableEngineService engineService;
    private final ObjectMapper objectMapper;
    private final UserRoleMapper userRoleMapper;
    private final FlowVariableService flowVariableService;

    @Override
    public PageResult<FlowDefinitionVO> pageDefinitions(FlowDefinitionPageQuery query) {
        LambdaQueryWrapper<FlowDefinition> q = new LambdaQueryWrapper<>();
        q.eq(FlowDefinition::getIsDeleted, 0);
        if (query.getStatus() != null) {
            q.eq(FlowDefinition::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getFlowName())) {
            q.like(FlowDefinition::getFlowName, query.getFlowName());
        }
        if (StringUtils.hasText(query.getFlowCode())) {
            q.eq(FlowDefinition::getFlowCode, query.getFlowCode());
        }
        if (StringUtils.hasText(query.getCategory())) {
            q.eq(FlowDefinition::getCategory, query.getCategory());
        }
        q.orderByDesc(FlowDefinition::getId);

        long total = flowDefinitionMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) query.getPageNo(), (long) query.getPageSize());
        }
        Page<FlowDefinition> page = new Page<>(query.getPageNo(), query.getPageSize());
        flowDefinitionMapper.selectPage(page, q);
        List<FlowDefinitionVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, total, (long) query.getPageNo(), (long) query.getPageSize());
    }

    @Override
    public FlowDefinitionVO getDetail(Long id) {
        return toVO(getById(id));
    }

    @Override
    public List<FlowOptionVO> listEnabledOptions() {
        // 只 select 业务必需列：既省带宽，也从物理上杜绝把 deploymentId / nodeConfig
        // 等模板管理内部信息透给普通员工（VO 里没有对应属性，映射不到）。
        List<FlowDefinition> enabled = flowDefinitionMapper.selectList(
                new LambdaQueryWrapper<FlowDefinition>()
                        .select(FlowDefinition::getId,
                                FlowDefinition::getFlowCode,
                                FlowDefinition::getFlowName,
                                FlowDefinition::getCategory,
                                FlowDefinition::getIcon,
                                FlowDefinition::getFormFields)
                        .eq(FlowDefinition::getIsDeleted, 0)
                        .eq(FlowDefinition::getStatus, STATUS_ENABLED)
                        .orderByAsc(FlowDefinition::getId));
        return enabled.stream().map(fd -> {
            FlowOptionVO vo = new FlowOptionVO();
            vo.setId(fd.getId());
            vo.setFlowCode(fd.getFlowCode());
            vo.setFlowName(fd.getFlowName());
            vo.setCategory(fd.getCategory());
            vo.setIcon(fd.getIcon());
            vo.setFormFields(fd.getFormFields());
            return vo;
        }).toList();
    }

    @Override
    public String getBpmnXml(Long id) {
        FlowDefinition fd = getById(id);
        ProcessDefinition pd = engineService.getLatestProcessDefinition(procKey(fd));
        if (pd == null) {
            throw new BizException(ErrorCode.FLOW_NOT_DEPLOYED);
        }
        return engineService.getResourceXml(pd.getDeploymentId(), pd.getResourceName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDefinition(FlowDefinitionSaveRequest req) {
        if (req.getId() != null) {
            return updateExisting(req);
        }
        // 新增：校验 flowCode 唯一
        Long exist = flowDefinitionMapper.selectCount(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getIsDeleted, 0)
                .eq(FlowDefinition::getFlowCode, req.getFlowCode()));
        if (exist != null && exist > 0) {
            throw new BizException(ErrorCode.FLOW_DEF_DUPLICATE);
        }
        FlowDefinition fd = new FlowDefinition();
        fd.setFlowCode(req.getFlowCode());
        fd.setFlowName(req.getFlowName());
        fd.setCategory(blankToNull(req.getCategory()));
        fd.setIcon(blankToNull(req.getIcon()));
        fd.setFormFields(blankToNull(req.getFormFields()));
        fd.setNodeConfig(blankToNull(req.getNodeConfig()));
        fd.setFlowConfig(blankToNull(req.getFlowConfig()));
        fd.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        fd.setRemark(blankToNull(req.getRemark()));
        if (StringUtils.hasText(req.getBpmnXml())) {
            deployAndBind(fd, req.getBpmnXml(), parseFlowConfig(req.getFlowConfig()));
        }
        flowDefinitionMapper.insert(fd);
        return fd.getId();
    }

    private Long updateExisting(FlowDefinitionSaveRequest req) {
        FlowDefinition existing = getById(req.getId());
        if (StringUtils.hasText(req.getBpmnXml())) {
            deployAndBind(existing, req.getBpmnXml(), parseFlowConfig(req.getFlowConfig()));
        }
        existing.setFlowName(req.getFlowName());
        // 可清空的列统一「空白 → null」：否则前端清空后传空串会落成 ''，
        // 与「未配置」在库里有两种表示，查询与回显都要额外兼容（D1 同源问题）。
        existing.setCategory(blankToNull(req.getCategory()));
        existing.setIcon(blankToNull(req.getIcon()));
        existing.setFormFields(blankToNull(req.getFormFields()));
        existing.setNodeConfig(blankToNull(req.getNodeConfig()));
        existing.setFlowConfig(blankToNull(req.getFlowConfig()));
        existing.setRemark(blankToNull(req.getRemark()));
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        // 用 LambdaUpdateWrapper 显式 set 每一列，而不是 updateById(部分实体)：
        // MyBatis-Plus 的 NOT_NULL 策略会把 null 字段从 SET 里剔除，导致「传 null 清空」失效
        // （D1：flowConfig 清空后旧值仍在，DTO 注释承诺落不了地；icon / category / remark 同理）。
        // 代价是 wrapper 更新不触发自动填充，update_time 需要自己给（见下）。
        // 注意：deployAndBind 会把部署结果写回实体，deploymentId / bpmnKey 必须一并落库。
        LambdaUpdateWrapper<FlowDefinition> update = new LambdaUpdateWrapper<FlowDefinition>()
                .eq(FlowDefinition::getId, existing.getId())
                .set(FlowDefinition::getFlowName, existing.getFlowName())
                .set(FlowDefinition::getCategory, existing.getCategory())
                .set(FlowDefinition::getIcon, existing.getIcon())
                .set(FlowDefinition::getFormFields, existing.getFormFields())
                .set(FlowDefinition::getNodeConfig, existing.getNodeConfig())
                .set(FlowDefinition::getFlowConfig, existing.getFlowConfig())
                .set(FlowDefinition::getStatus, existing.getStatus())
                .set(FlowDefinition::getRemark, existing.getRemark())
                .set(FlowDefinition::getDeploymentId, existing.getDeploymentId())
                .set(FlowDefinition::getBpmnKey, existing.getBpmnKey())
                // update_time 必须显式给：wrapper 更新不走 MetaObjectHandler 的自动填充，
                // 而本表 DDL 也没有 ON UPDATE CURRENT_TIMESTAMP（审计字段全靠填充），
                // 漏掉这一行会让「改过的时间」停在旧值上。
                .set(FlowDefinition::getUpdateTime, LocalDateTime.now());
        flowDefinitionMapper.update(null, update);
        return existing.getId();
    }

    /**
     * 空白字符串归一到 null。
     *
     * <p>「未配置」在库里只允许一种表示（NULL）。若允许空串混入，凡是按
     * {@code IS NULL} 判断「有没有配」的地方都要额外兼容，迟早漏一处。</p>
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * 取历史 flow_config 里涉及的连线 id，用于部署时清理「本次已删除」的注入条件。
     *
     * <p>解析失败按「无历史」处理而不是抛错：历史值若被人工改坏，不应连带阻断本次保存；
     * 表达式残留最多退回旧行为（条件仍在），不会让流程部署失败。</p>
     */
    private Set<String> previousFlowIds(String flowConfig) {
        if (!StringUtils.hasText(flowConfig)) {
            return Set.of();
        }
        try {
            List<FlowConditionItem> items = objectMapper.readValue(flowConfig,
                    new TypeReference<List<FlowConditionItem>>() {
                    });
            Set<String> ids = new LinkedHashSet<>();
            for (FlowConditionItem item : items) {
                if (item != null && StringUtils.hasText(item.getFlowId())) {
                    ids.add(item.getFlowId());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("解析历史 flow_config 失败，跳过条件清理：{}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 解析分支条件配置 JSON。
     *
     * <p><b>解析失败必须抛错而不是当作空配置</b>：静默丢弃会让「保存成功但流程行为与配置不符」——
     * 用户在界面上明明配了 {@code days > 3} 走人事，跑起来却全走主管，这类静默错路由
     * 比保存时报错难排查一个数量级。</p>
     *
     * @return 解析结果；入参为空时返回空列表（历史模板与未配条件的场景）
     * @throws BizException 2508 JSON 非法或结构不符
     */
    private List<FlowConditionItem> parseFlowConfig(String flowConfig) {
        if (!StringUtils.hasText(flowConfig)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(flowConfig, new TypeReference<List<FlowConditionItem>>() {
            });
        } catch (Exception e) {
            log.warn("分支条件配置 JSON 解析失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FLOW_CONDITION_INVALID,
                    "分支条件配置格式非法，请重新打开设计器再保存");
        }
    }

    /**
     * 校验分支条件引用的变量名是否合法（必须落在「本流程表单字段 ∪ 系统内置变量」内）。
     *
     * <p><b>为什么必须校验</b>：运行时若引用了一个不存在的变量名，JUEL 求值取到 null，
     * 网关会静默走默认分支——流程"看起来正常"、单据也能走完，但路由是错的。
     * 这类问题在保存阶段拦住，用户当场就能看到是哪个变量名写错；
     * 等到上线后排查「为什么这个单子没走大额审批」，成本要高一个数量级。</p>
     *
     * <p><b>两个豁免</b>：
     * <ol>
     *   <li>手写表达式（{@code raw}）模式不校验：从表达式文本里正则抽变量名会误伤字符串
     *       字面量（如 {@code ${reason == 'outcome'}}），且该模式本就只开放给持有
     *       {@code system:flow:condition-raw} 权限的人；</li>
     *   <li>模板 id 为空（首次保存、尚未落库）时跳过：此时拿不到流程主键，无法解析
     *       绑定了哪个表单。实际业务里"先保存模板 → 再绑定表单 → 再配条件"，
     *       走到这一步时模板已有 id，不会漏校验。</li>
     * </ol>
     * </p>
     */
    private void assertConditionVariablesValid(Long flowDefId, List<FlowConditionItem> conditions) {
        if (flowDefId == null || conditions == null || conditions.isEmpty()) {
            return;
        }
        Set<String> validKeys = flowVariableService.listValidKeys(flowDefId);
        for (FlowConditionItem item : conditions) {
            if (item == null || item.isRawMode()) {
                continue;
            }
            for (String field : item.referencedFields()) {
                if (!validKeys.contains(field)) {
                    throw new BizException(ErrorCode.FLOW_CONDITION_INVALID,
                            "条件变量「" + field + "」不存在：请从变量下拉中选择（表单字段需先在「表单绑定」页绑定表单，"
                                    + "或改用「发起人 / 部门 / 审批结果」等系统变量）");
                }
            }
        }
    }

    private void deployAndBind(FlowDefinition fd, String bpmnXml, List<FlowConditionItem> conditions) {
        // 1) 归一化：把 BPMN 的 <process id> 统一改写为流程编码。
        //    引擎的流程定义 key 取自 <process id>，而「发起申请」按 key 启动且取最新版本；
        //    若多个模板共用同一个 process id（设计器新建时默认 process_1），后部署的会顶掉先部署的，
        //    出现「选了 A 模板却执行 B 模板」的错位。归一化后 key == 流程编码，由唯一约束保证一一对应。
        String normalizedXml = BpmnXmlNormalizer.overrideProcessId(bpmnXml, fd.getFlowCode());
        // 2) 预展开 role:X → candidateUsers：让 Flowable 部署时直接写「用户」身份链接，
        //    避免本库未启用 IDM 导致 candidateGroups 节点永远进不了待办（修复 SMOKE-D01）。
        normalizedXml = BpmnXmlNormalizer.preExpandCandidateUsers(normalizedXml, this::resolveRoleUsers);
        // 3) 注入分支条件：把 flow_config 的结构化条件写进 sequenceFlow 的 conditionExpression，
        //    并为默认分支所在网关写 default 属性。只处理配置里出现的连线，未出现的保持原样
        //    （内置模板手写的 ${outcome == 'approve'} 等历史表达式因此不受影响）。
        //    注入前先做变量合法性校验：拿到错误提示时 XML 还没被改动，失败路径干净。
        assertConditionVariablesValid(fd.getId(), conditions);
        // 上一次 flow_config 涉及的连线：本次已不在配置里的，要把当初注入的表达式删掉。
        // 画布 XML 是从「已部署资源」加载的，自带旧表达式；不清理就会「界面清空了、运行仍按旧条件分流」。
        // 注意此处 fd.flowConfig 仍是旧值——updateExisting 在调用本方法之后才覆盖它。
        normalizedXml = BpmnXmlNormalizer.injectConditions(normalizedXml, conditions,
                previousFlowIds(fd.getFlowConfig()));
        // 4) 归一化已完成，这里的 key 即流程编码；仍按 BPMN 实际内容解析，避免与实际部署结果脱节。
        BpmnXmlParser.ParsedBpmn parsed = BpmnXmlParser.parse(normalizedXml);
        assertBpmnKeyAvailable(parsed.key(), fd.getFlowCode());
        // 5) 结构校验：拦住「能解析但跑起来会错位 / 卡死」的流程（缺开始/结束事件、排他网关缺默认
        //    分支、孤立节点等）。校验只看 BPMN 结构，不校验审批人——设计器把审批人写在 node_config
        //    而不写进 BPMN，是否已配审批人由设计器保存/发布路径（前端 validatePublishable + 运行期
        //    ApprovalServiceImpl.resolveCandidates 的兜底）把关，避免在此误拒所有设计器保存。
        assertBpmnStructureValid(normalizedXml);

        // 卸载旧部署必须排在**所有校验之后**：deleteDeployment(id, true) 是级联删除，
        // 先卸载再校验的话，一次「校验不通过」的保存就会把线上部署连同在跑实例一起删掉，
        // 表现为「保存报错 → 流程从发起页消失」。
        if (StringUtils.hasText(fd.getDeploymentId())) {
            engineService.undeploy(fd.getDeploymentId());
        }
        var deployment = deploy(fd.getFlowCode(), normalizedXml);
        ProcessDefinition pd = engineService.getLatestProcessDefinition(parsed.key());
        if (pd == null) {
            throw new BizException(ErrorCode.FLOW_NOT_DEPLOYED);
        }
        fd.setDeploymentId(deployment.getId());
        fd.setBpmnKey(pd.getKey());
    }

    /**
     * 部署到 Flowable，并把引擎侧的校验失败转成可读的业务错误。
     *
     * <p>部署期是 BPMN 规范校验的最终关口：JDK DOM 只能验「格式是否合法」，XSD 层面的规则
     * （如 {@code <process>} 内含文本子级、节点属性缺失）由 Flowable 在部署时才校验。若不在此转换，
     * 这类「内容不合规」会落进全局兜底分支变成 {@code 5000 系统内部错误}，使用者拿不到
     * 「文件哪里不对」的提示——与文档约定的 {@code 3005} 不符。</p>
     *
     * <p><b>为什么分两个 catch</b>：Flowable 8 的 {@link XMLException}（XSD/XML 校验失败）
     * <b>直接继承 {@link RuntimeException}</b>，并不是 {@code FlowableException} 的子类——
     * 只捕获 {@code FlowableException} 会漏掉最常见的那一类部署失败。</p>
     */
    private Deployment deploy(String flowCode, String bpmnXml) {
        try {
            return engineService.deploy(flowCode, bpmnXml);
        } catch (XMLException e) {
            throw bpmnInvalid(flowCode, e);
        } catch (FlowableException e) {
            throw bpmnInvalid(flowCode, e);
        }
    }

    /**
     * 把引擎侧的部署失败统一转成「BPMN 文件不合规」业务异常（3005）。
     *
     * <p>对外只给可读的固定文案：引擎原始异常里带着 {@code javax.xml.transform.TransformerException}
     * 之类的类名与极长的 XSD 报错链，直接回给前端既不友好也暴露内部实现；完整原因与堆栈记入日志，
     * 需要定位细节时查日志即可。</p>
     */
    private BizException bpmnInvalid(String flowCode, RuntimeException cause) {
        log.warn("BPMN 部署被引擎拒绝 flowCode={}：{}", flowCode, cause.getMessage(), cause);
        return new BizException(ErrorCode.FLOW_BPMN_INVALID,
                "BPMN 文件未通过流程引擎校验，请检查流程定义是否符合 BPMN 2.0 规范");
    }

    /**
     * 结构校验：把「能解析、但运行期会错位或卡死」的流程在设计器保存时当场拦住。
     *
     * <p>对齐 RuoYi 流程设计的严格模式：保存即校验，错误信息携带节点名称与原因，
     * 用户无需等到发起申请才发现「网关忘了配默认分支 / 画了孤立节点」。</p>
     */
    private void assertBpmnStructureValid(String normalizedXml) {
        List<BpmnXmlValidator.StructureIssue> issues = BpmnXmlValidator.validate(normalizedXml);
        if (!issues.isEmpty()) {
            String detail = issues.stream()
                    .map(i -> (i.nodeName() != null ? "「" + i.nodeName() + "」" : "") + i.message())
                    .collect(Collectors.joining("；"));
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID, "流程结构校验未通过：" + detail);
        }
    }

    /**
     * 兜底校验：即将使用的流程标识不能已被别的模板占用。
     */
    private void assertBpmnKeyAvailable(String bpmnKey, String flowCode) {
        Long occupied = flowDefinitionMapper.selectCount(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getIsDeleted, 0)
                .eq(FlowDefinition::getBpmnKey, bpmnKey)
                .ne(FlowDefinition::getFlowCode, flowCode));
        if (occupied != null && occupied > 0) {
            throw new BizException(ErrorCode.FLOW_KEY_CONFLICT,
                    "流程标识 " + bpmnKey + " 已被其他流程模板占用，请更换流程编码");
        }
    }

    /**
     * 按角色 id 解析该角色下的启用用户 id 列表（去重）。
     *
     * <p>供 {@link BpmnXmlNormalizer#preExpandCandidateUsers} 部署期调用：把 BPMN 中
     * {@code role:X} 引用预展开成 {@code candidateUsers}，让 Flowable 部署时直接写「用户」
     * 身份链接，规避本库无 IDM 导致 {@code taskCandidateUser} 检索不到候选组节点的问题。</p>
     */
    private List<Long> resolveRoleUsers(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, roleId)).stream()
                .map(UserRole::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importDefinition(FlowDefinitionImportRequest req) {
        BpmnXmlParser.ParsedBpmn parsed = BpmnXmlParser.parse(req.getBpmnXml());
        String flowCode = StringUtils.hasText(req.getFlowCode()) ? req.getFlowCode().trim() : parsed.key();

        Long exist = flowDefinitionMapper.selectCount(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getIsDeleted, 0)
                .eq(FlowDefinition::getFlowCode, flowCode));
        if (exist != null && exist > 0) {
            throw new BizException(ErrorCode.FLOW_DEF_DUPLICATE);
        }

        FlowDefinition fd = new FlowDefinition();
        fd.setFlowCode(flowCode);
        fd.setFlowName(StringUtils.hasText(req.getFlowName()) ? req.getFlowName().trim() : parsed.name());
        fd.setCategory(req.getCategory());
        fd.setRemark(req.getRemark());
        fd.setStatus(1);
        fd.setNodeConfig(writeNodeConfig(parsed.nodes()));
        // 导入的 BPMN 不带结构化条件配置：条件若已写在 XML 的 conditionExpression 里会原样部署，
        // 需要可视化配置时在设计器打开后重新保存即可生成 flow_config。
        deployAndBind(fd, req.getBpmnXml(), List.of());
        flowDefinitionMapper.insert(fd);
        log.info("导入流程模板成功 flowCode={} bpmnKey={} 节点数={}", fd.getFlowCode(), fd.getBpmnKey(),
                parsed.nodes().size());
        return fd.getId();
    }

    /** 由解析出的节点生成 node_config JSON；未按约定标注审批人的节点 assigneeType=0，导入后在设计器补配 */
    private String writeNodeConfig(List<BpmnXmlParser.BpmnNode> nodes) {
        List<NodeConfigItem> items = nodes.stream().map(n -> {
            NodeConfigItem item = new NodeConfigItem();
            item.setNodeId(n.nodeId());
            item.setNodeName(n.nodeName());
            item.setAssigneeType(n.assigneeType());
            item.setAssigneeValue(n.assigneeValue());
            return item;
        }).toList();
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "生成节点审批人配置失败");
        }
    }

    /** 启动/查询流程统一用 BPMN 的 process id（bpmnKey），历史数据缺失时回退 flowCode */
    private String procKey(FlowDefinition fd) {
        return StringUtils.hasText(fd.getBpmnKey()) ? fd.getBpmnKey() : fd.getFlowCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(Long id) {
        FlowDefinition fd = getById(id);
        // 引用完整性：模板是审批实例的「配置底座」，删掉它会让在途实例失去流程定义——详情/继续审批都会
        // 查不到模板（3001），而引擎侧部署已卸载、运行时实例却仍在，形成孤儿数据。
        // 与字典删除（类型下有数据项则拒绝 2303）保持同一口径：先确认「无人使用」再允许删除。
        Long active = approvalInstanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getFlowCode, fd.getFlowCode())
                .in(ApprovalInstance::getStatus, INSTANCE_ACTIVE_STATUS));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.FLOW_DEF_IN_USE,
                    "该流程模板下存在 " + active + " 个未结束的审批实例，无法删除");
        }
        if (StringUtils.hasText(fd.getDeploymentId())) {
            engineService.undeploy(fd.getDeploymentId());
        }
        // 唯一性已由数据库保证：uk_flow_code 改为 (flow_code, active_flag)，
        // 已删行 active_flag=NULL，原编码自动释放，应用层无需改写业务值。
        // is_deleted 带 @TableLogic，必须走 MP 的 deleteById 才会转成 UPDATE ... SET is_deleted=1，
        // 直接 updateById 会把该字段从 SET 中剔除导致逻辑删除不生效。
        flowDefinitionMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        FlowDefinition fd = getById(id);
        // 发布（status=1）前做审批人完整性校验：设计器把审批人写在 node_config（不写进 BPMN），
        // 未配置的节点发起后无人可办。这是直接调接口时的最后一道闸，与前端「发布」按钮同口径。
        if (Integer.valueOf(STATUS_ENABLED).equals(status)) {
            assertAssigneeConfigComplete(fd);
        }
        // 同上：只提交 id + status，让 update_time 走自动填充
        FlowDefinition update = new FlowDefinition();
        update.setId(id);
        update.setStatus(status);
        flowDefinitionMapper.updateById(update);
    }

    /**
     * 发布前的审批人完整性校验（基于 node_config，而非 BPMN）。
     *
     * <p><b>为什么读 node_config</b>：设计器把审批人规则保存在 {@code oa_flow_definition.node_config}，
     * BPMN 的 userTask 不携带审批人（与手写种子 BPMN 的 {@code oa:assignee} 不同），故只能以 node_config 为准。</p>
     *
     * <p><b>校验口径</b>：未配置规则（type=0）、或规则要求选人/角色/部门却未取值，均不允许发布；
     * 「部门主管 / 申请人本人」类型本身即可确定候选人，只校验类型、不要求取值。仅取类型默认值、不校验层级——
     * 部门主管的 assigneeValue 是可选层级，留空按 1 处理。</p>
     */
    private void assertAssigneeConfigComplete(FlowDefinition fd) {
        List<String> problems = new ArrayList<>();
        for (NodeConfigItem item : readNodeConfig(fd.getNodeConfig())) {
            int type = item.getAssigneeType() == null ? BpmnXmlParser.ASSIGNEE_UNSET : item.getAssigneeType();
            String label = StringUtils.hasText(item.getNodeName()) ? item.getNodeName()
                    : (StringUtils.hasText(item.getNodeId()) ? item.getNodeId() : "未命名节点");
            if (type == BpmnXmlParser.ASSIGNEE_UNSET) {
                problems.add("「" + label + "」未配置审批人");
            } else if ((type == BpmnXmlParser.ASSIGNEE_USER || type == BpmnXmlParser.ASSIGNEE_ROLE
                    || type == BpmnXmlParser.ASSIGNEE_DEPT_LEADER)
                    && !StringUtils.hasText(item.getAssigneeValue())) {
                problems.add("「" + label + "」未选择具体" + switch (type) {
                    case BpmnXmlParser.ASSIGNEE_USER -> "人员";
                    case BpmnXmlParser.ASSIGNEE_ROLE -> "角色";
                    default -> "部门";
                });
            }
        }
        if (!problems.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_BPMN_INVALID,
                    "发布前请先补全审批人配置：" + String.join("；", problems));
        }
    }

    /** 解析 node_config JSON；空或损坏时返回空列表（结构问题由设计器与部署期校验拦截）。 */
    private List<NodeConfigItem> readNodeConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            NodeConfigItem[] arr = objectMapper.readValue(json, NodeConfigItem[].class);
            return List.of(arr);
        } catch (Exception e) {
            log.warn("node_config 解析失败，跳过审批人完整性校验：{}", e.getMessage());
            return List.of();
        }
    }

    private FlowDefinition getById(Long id) {
        FlowDefinition fd = flowDefinitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getId, id)
                .eq(FlowDefinition::getIsDeleted, 0));
        if (fd == null) {
            throw new BizException(ErrorCode.FLOW_DEF_NOT_FOUND);
        }
        return fd;
    }

    private FlowDefinitionVO toVO(FlowDefinition fd) {
        FlowDefinitionVO vo = new FlowDefinitionVO();
        vo.setId(fd.getId());
        vo.setFlowCode(fd.getFlowCode());
        vo.setFlowName(fd.getFlowName());
        vo.setCategory(fd.getCategory());
        vo.setIcon(fd.getIcon());
        vo.setBpmnKey(fd.getBpmnKey());
        vo.setDeploymentId(fd.getDeploymentId());
        vo.setFormFields(fd.getFormFields());
        vo.setNodeConfig(fd.getNodeConfig());
        vo.setFlowConfig(fd.getFlowConfig());
        vo.setStatus(fd.getStatus());
        vo.setRemark(fd.getRemark());
        vo.setCreateTime(fd.getCreateTime());
        vo.setUpdateTime(fd.getUpdateTime());
        return vo;
    }
}
