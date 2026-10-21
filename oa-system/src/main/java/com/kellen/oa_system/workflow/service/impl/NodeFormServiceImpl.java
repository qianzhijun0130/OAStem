package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Form;
import com.kellen.oa_system.system.mapper.FormMapper;
import com.kellen.oa_system.workflow.dto.NodeFormBindRequest;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.entity.FlowNodeForm;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.mapper.FlowNodeFormMapper;
import com.kellen.oa_system.workflow.service.NodeFormService;
import com.kellen.oa_system.workflow.vo.NodeFormVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程节点↔表单 绑定实现。
 *
 * <p>绑定语义为「全量覆盖」：同节点已有存活绑定时先逻辑删除再插入新记录。
 * 这样处理是因为唯一索引是 {@code (flow_def_id, node_key, active_flag)}，
 * 而已删行的 active_flag 为 NULL —— 若直接 insert 会与「旧行仍存活」冲突，
 * 若直接 update 又会让审计字段 update_time 的语义变得含糊。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeFormServiceImpl implements NodeFormService {

    private final FlowNodeFormMapper flowNodeFormMapper;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final FormMapper formMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindForm(NodeFormBindRequest req) {
        FlowDefinition flow = flowDefinitionMapper.selectById(req.getFlowDefId());
        if (flow == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND_FOR_BIND);
        }
        Form form = formMapper.selectById(req.getFormId());
        if (form == null) {
            throw new BizException(ErrorCode.FORM_NOT_FOUND);
        }

        String nodeKey = StringUtils.hasText(req.getNodeKey()) ? req.getNodeKey().trim() : "init";
        int isPrimary = req.getIsPrimary() == null ? 1 : req.getIsPrimary();

        // 同节点已有绑定 → 逻辑删除旧绑定，再插新绑定（全量覆盖语义）
        List<FlowNodeForm> olds = flowNodeFormMapper.selectList(new LambdaQueryWrapper<FlowNodeForm>()
                .eq(FlowNodeForm::getFlowDefId, req.getFlowDefId())
                .eq(FlowNodeForm::getNodeKey, nodeKey));
        for (FlowNodeForm old : olds) {
            flowNodeFormMapper.deleteById(old.getId());
        }

        // 同一流程只允许一个主表单：新绑定为主表单时，清掉其他节点的主表单标记
        if (isPrimary == 1) {
            List<FlowNodeForm> siblings = flowNodeFormMapper.selectList(new LambdaQueryWrapper<FlowNodeForm>()
                    .eq(FlowNodeForm::getFlowDefId, req.getFlowDefId())
                    .ne(FlowNodeForm::getNodeKey, nodeKey));
            for (FlowNodeForm sibling : siblings) {
                if (sibling.getIsPrimary() != null && sibling.getIsPrimary() == 1) {
                    FlowNodeForm patch = new FlowNodeForm();
                    patch.setId(sibling.getId());
                    patch.setIsPrimary(0);
                    flowNodeFormMapper.updateById(patch);
                }
            }
        }

        FlowNodeForm bind = new FlowNodeForm();
        bind.setFlowDefId(req.getFlowDefId());
        bind.setNodeKey(nodeKey);
        bind.setNodeName(StringUtils.hasText(req.getNodeName()) ? req.getNodeName().trim() : nodeKey);
        bind.setFormId(req.getFormId());
        bind.setIsPrimary(isPrimary);
        bind.setSort(olds.isEmpty() ? 0 : olds.get(0).getSort());
        bind.setCreateBy(SecurityContextUtil.getCurrentUserId());
        bind.setUpdateBy(SecurityContextUtil.getCurrentUserId());
        flowNodeFormMapper.insert(bind);
    }

    @Override
    public void unbindForm(Long id) {
        FlowNodeForm bind = flowNodeFormMapper.selectById(id);
        if (bind == null) {
            throw new BizException(ErrorCode.FORM_NOT_FOUND);
        }
        flowNodeFormMapper.deleteById(id);
    }

    @Override
    public List<NodeFormVO> listByFlowDef(Long flowDefId) {
        List<FlowNodeForm> binds = flowNodeFormMapper.selectList(new LambdaQueryWrapper<FlowNodeForm>()
                .eq(FlowNodeForm::getFlowDefId, flowDefId)
                .orderByAsc(FlowNodeForm::getSort)
                .orderByAsc(FlowNodeForm::getId));
        return assemble(binds, null);
    }

    @Override
    public NodeFormVO getPrimaryFormByFlowCode(String flowCode) {
        if (!StringUtils.hasText(flowCode)) {
            return null;
        }
        FlowDefinition flow = flowDefinitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getFlowCode, flowCode)
                .last("LIMIT 1"));
        if (flow == null) {
            return null;
        }
        List<FlowNodeForm> binds = flowNodeFormMapper.selectList(new LambdaQueryWrapper<FlowNodeForm>()
                .eq(FlowNodeForm::getFlowDefId, flow.getId())
                .eq(FlowNodeForm::getIsPrimary, 1)
                .orderByAsc(FlowNodeForm::getId)
                .last("LIMIT 1"));
        if (binds.isEmpty()) {
            // 未绑定表单是合法状态（模板可能仍在配置中），返回 null 由前端降级
            return null;
        }
        List<NodeFormVO> vos = assemble(binds, flow);
        return vos.isEmpty() ? null : vos.get(0);
    }

    // ------------------------- 内部辅助 -------------------------

    /**
     * 组装绑定 VO：一次性把 form / flowDefinition 查出来再映射，避免逐条查库（N+1）。
     */
    private List<NodeFormVO> assemble(List<FlowNodeForm> binds, FlowDefinition knownFlow) {
        if (binds.isEmpty()) {
            return List.of();
        }
        Map<Long, Form> formMap = formMapper.selectBatchIds(
                        binds.stream().map(FlowNodeForm::getFormId).distinct().toList())
                .stream().collect(Collectors.toMap(Form::getId, Function.identity()));

        Map<Long, FlowDefinition> flowMap = new HashMap<>();
        if (knownFlow != null) {
            flowMap.put(knownFlow.getId(), knownFlow);
        } else {
            List<Long> flowIds = binds.stream().map(FlowNodeForm::getFlowDefId).distinct().toList();
            flowMap.putAll(flowDefinitionMapper.selectBatchIds(flowIds).stream()
                    .collect(Collectors.toMap(FlowDefinition::getId, Function.identity())));
        }

        List<NodeFormVO> list = new ArrayList<>(binds.size());
        for (FlowNodeForm bind : binds) {
            NodeFormVO vo = new NodeFormVO();
            vo.setId(bind.getId());
            vo.setFlowDefId(bind.getFlowDefId());
            vo.setNodeKey(bind.getNodeKey());
            vo.setNodeName(bind.getNodeName());
            vo.setFormId(bind.getFormId());
            vo.setIsPrimary(bind.getIsPrimary());
            vo.setSort(bind.getSort());

            FlowDefinition flow = flowMap.get(bind.getFlowDefId());
            if (flow != null) {
                vo.setFlowCode(flow.getFlowCode());
                vo.setFlowName(flow.getFlowName());
            }
            Form form = formMap.get(bind.getFormId());
            if (form != null) {
                vo.setFormCode(form.getFormCode());
                vo.setFormName(form.getFormName());
                vo.setRuleJson(form.getRuleJson());
            }
            list.add(vo);
        }
        return list;
    }
}
