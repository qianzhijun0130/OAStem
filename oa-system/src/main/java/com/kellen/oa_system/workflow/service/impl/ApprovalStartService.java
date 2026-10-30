package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.support.ApprovalBranchSupport;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalInstanceSupport;
import com.kellen.oa_system.workflow.support.ApprovalTaskSupport;
import com.kellen.oa_system.workflow.support.ApprovalVariableSupport;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 发起环节：直接提交、保存草稿、修改草稿、提交草稿。
 *
 * <p>两条共用逻辑：<b>取「启用且已部署」的流程模板</b>（发起与提交草稿是同一套校验）、
 * <b>启动引擎并把首个节点挂上候选人</b>。后者与「审批流转」「恢复」共用
 * {@link ApprovalTaskSupport#assignCurrentTasks}，避免三处各写一遍导致审批人解析口径漂移。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalStartService {

    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final FlowableEngineService engine;
    private final ApprovalInstanceSupport instanceSupport;
    private final ApprovalVariableSupport variableSupport;
    private final ApprovalTaskSupport taskSupport;
    private final ApprovalBranchSupport branchSupport;

    public Long start(ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        FlowDefinition fd = requireEnabledFlow(req.getFlowCode());
        ApprovalInstance inst = new ApprovalInstance();
        inst.setFlowDefId(fd.getId());
        inst.setFlowCode(fd.getFlowCode());
        inst.setTitle(req.getTitle());
        inst.setApplicantId(applicantId);
        inst.setFormData(req.getFormData());
        inst.setStatus(ApprovalConstants.STATUS_APPROVING);
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
    public Long saveDraft(ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        FlowDefinition fd = requireEnabledFlow(req.getFlowCode());
        ApprovalInstance inst = new ApprovalInstance();
        inst.setFlowDefId(fd.getId());
        inst.setFlowCode(fd.getFlowCode());
        inst.setTitle(req.getTitle());
        inst.setApplicantId(applicantId);
        inst.setFormData(req.getFormData());
        inst.setStatus(ApprovalConstants.STATUS_DRAFT);
        instanceMapper.insert(inst);
        applyBillCode(inst);
        return inst.getId();
    }

    public void updateDraft(Long instanceId, ApprovalStartRequest req) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        if (!Objects.equals(inst.getApplicantId(), applicantId)) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        if (!Objects.equals(inst.getStatus(), ApprovalConstants.STATUS_DRAFT)) {
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
    public void submitDraft(Long instanceId) {
        Long applicantId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        if (!Objects.equals(inst.getApplicantId(), applicantId)) {
            // 不暴露「别人的草稿是否存在」
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        if (!Objects.equals(inst.getStatus(), ApprovalConstants.STATUS_DRAFT)) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_DONE, "仅草稿状态的申请可以提交");
        }
        FlowDefinition fd = requireEnabledFlow(inst.getFlowCode());
        inst.setStatus(ApprovalConstants.STATUS_APPROVING);
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
        vars.putAll(variableSupport.buildApplicantContextVars(applicantId));
        vars.putAll(variableSupport.flattenFormData(inst.getFormData()));
        String procInstId = engine.startProcessInstance(procKey(fd), String.valueOf(inst.getId()), vars);
        inst.setProcInstId(procInstId);
        instanceMapper.updateById(inst);

        instanceSupport.insertRecord(inst, "submit", applicantId, "提交申请", "发起申请");

        Task firstTask = taskSupport.assignCurrentTasks(inst, fd);
        // 首个节点也可能经网关路由而来（发起即分流），一并记录命中分支
        branchSupport.recordBranchHit(inst, fd, firstTask == null ? null : firstTask.getTaskDefinitionKey());
    }

    /** 启动/查询流程统一用 BPMN 的 process id（bpmnKey），历史数据缺失时回退 flowCode */
    private String procKey(FlowDefinition fd) {
        return StringUtils.hasText(fd.getBpmnKey()) ? fd.getBpmnKey() : fd.getFlowCode();
    }
}
