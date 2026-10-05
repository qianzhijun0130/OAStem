package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.workflow.dto.FlowDefinitionImportRequest;
import com.kellen.oa_system.workflow.dto.FlowDefinitionPageQuery;
import com.kellen.oa_system.workflow.dto.FlowDefinitionSaveRequest;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.FlowDefinitionService;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.util.BpmnXmlNormalizer;
import com.kellen.oa_system.workflow.util.BpmnXmlParser;
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

import java.util.List;
import java.util.Objects;

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
        fd.setCategory(req.getCategory());
        fd.setFormFields(req.getFormFields());
        fd.setNodeConfig(req.getNodeConfig());
        fd.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        fd.setRemark(req.getRemark());
        if (StringUtils.hasText(req.getBpmnXml())) {
            deployAndBind(fd, req.getBpmnXml());
        }
        flowDefinitionMapper.insert(fd);
        return fd.getId();
    }

    private Long updateExisting(FlowDefinitionSaveRequest req) {
        FlowDefinition existing = getById(req.getId());
        if (StringUtils.hasText(req.getBpmnXml())) {
            // 重新部署：先卸载旧部署
            if (StringUtils.hasText(existing.getDeploymentId())) {
                engineService.undeploy(existing.getDeploymentId());
            }
            deployAndBind(existing, req.getBpmnXml());
        }
        existing.setFlowName(req.getFlowName());
        existing.setCategory(req.getCategory());
        existing.setFormFields(req.getFormFields());
        existing.setNodeConfig(req.getNodeConfig());
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        existing.setRemark(req.getRemark());
        // 只提交「id + 变更字段」的部分实体：整实体回写会带上从库里读出的旧 updateTime，
        // MyBatis-Plus 的自动填充不覆盖非空值，旧值会被显式写进 SET，
        // 使 MySQL 的 ON UPDATE CURRENT_TIMESTAMP 失效（详见 DictTypeServiceImpl#saveType）。
        // 注意：deployAndBind 会把部署结果写回实体，deploymentId / bpmnKey 必须一并落库。
        FlowDefinition update = new FlowDefinition();
        update.setId(existing.getId());
        update.setFlowName(existing.getFlowName());
        update.setCategory(existing.getCategory());
        update.setFormFields(existing.getFormFields());
        update.setNodeConfig(existing.getNodeConfig());
        update.setStatus(existing.getStatus());
        update.setRemark(existing.getRemark());
        update.setDeploymentId(existing.getDeploymentId());
        update.setBpmnKey(existing.getBpmnKey());
        flowDefinitionMapper.updateById(update);
        return existing.getId();
    }

    private void deployAndBind(FlowDefinition fd, String bpmnXml) {
        // 1) 归一化：把 BPMN 的 <process id> 统一改写为流程编码。
        //    引擎的流程定义 key 取自 <process id>，而「发起申请」按 key 启动且取最新版本；
        //    若多个模板共用同一个 process id（设计器新建时默认 process_1），后部署的会顶掉先部署的，
        //    出现「选了 A 模板却执行 B 模板」的错位。归一化后 key == 流程编码，由唯一约束保证一一对应。
        String normalizedXml = BpmnXmlNormalizer.overrideProcessId(bpmnXml, fd.getFlowCode());
        // 2) 预展开 role:X → candidateUsers：让 Flowable 部署时直接写「用户」身份链接，
        //    避免本库未启用 IDM 导致 candidateGroups 节点永远进不了待办（修复 SMOKE-D01）。
        normalizedXml = BpmnXmlNormalizer.preExpandCandidateUsers(normalizedXml, this::resolveRoleUsers);
        // 3) 归一化已完成，这里的 key 即流程编码；仍按 BPMN 实际内容解析，避免与实际部署结果脱节。
        BpmnXmlParser.ParsedBpmn parsed = BpmnXmlParser.parse(normalizedXml);
        assertBpmnKeyAvailable(parsed.key(), fd.getFlowCode());

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
     * 兜底校验：即将使用的流程标识不能已被别的模板占用。
     *
     * <p>归一化已保证 key == flowCode，而 flowCode 的唯一性在校验前置；此处再查一次是对
     * <b>存量脏数据</b>（归一化上线前创建、bpmn_key 与 flow_code 不一致的模板）和未来新增部署
     * 入口的安全网——宁可保存时报错，也不要等到发起时才「跑错流程」。</p>
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
        deployAndBind(fd, req.getBpmnXml());
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
        getById(id);
        // 同上：只提交 id + status，让 update_time 走自动填充
        FlowDefinition update = new FlowDefinition();
        update.setId(id);
        update.setStatus(status);
        flowDefinitionMapper.updateById(update);
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
        vo.setBpmnKey(fd.getBpmnKey());
        vo.setDeploymentId(fd.getDeploymentId());
        vo.setFormFields(fd.getFormFields());
        vo.setNodeConfig(fd.getNodeConfig());
        vo.setStatus(fd.getStatus());
        vo.setRemark(fd.getRemark());
        vo.setCreateTime(fd.getCreateTime());
        vo.setUpdateTime(fd.getUpdateTime());
        return vo;
    }
}
