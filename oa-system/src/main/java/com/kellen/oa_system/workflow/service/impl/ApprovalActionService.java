package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalBranchSupport;
import com.kellen.oa_system.workflow.support.ApprovalFormSupport;
import com.kellen.oa_system.workflow.support.ApprovalInstanceSupport;
import com.kellen.oa_system.workflow.support.ApprovalLifecycleSupport;
import com.kellen.oa_system.workflow.support.ApprovalTaskSupport;
import com.kellen.oa_system.workflow.support.ApprovalVoAssembler;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 审批动作：同意 / 驳回 / 撤销 / 转办。
 *
 * <p><b>驳回即终止流程</b>，且必须经过 {@link ApprovalLifecycleSupport#finish}——
 * 那是终态的唯一收敛点，业务表回写（请假/加班/出差/补卡）挂在那里。</p>
 *
 * <p>同意时先看引擎里流程是否还在跑：还在跑就挂下一节点候选人并记分支命中，
 * 跑完（自然结束）才置「已通过」。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalActionService {

    private final FlowableEngineService engine;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalInstanceSupport instanceSupport;
    private final ApprovalFormSupport formSupport;
    private final ApprovalTaskSupport taskSupport;
    private final ApprovalBranchSupport branchSupport;
    private final ApprovalLifecycleSupport lifecycleSupport;
    private final ApprovalVoAssembler assembler;

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
        if (Objects.equals(inst.getStatus(), ApprovalConstants.STATUS_SUSPENDED)) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_SUSPENDED, "审批单已挂起，请先恢复后再审批");
        }

        // 合并本节点可编辑字段的补丁（销假节点填销假信息等场景）。
        // 必须在 engine.complete 之前做：complete 之后流程可能已结束，实例不再是运行态。
        formSupport.applyFormDataPatch(inst, task, req.getFormDataPatch());

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
            lifecycleSupport.finish(inst, ApprovalConstants.STATUS_REJECTED);
            return;
        }
        // 同意
        FlowDefinition fd = instanceSupport.getFlowDef(inst);
        if (engine.isProcessActive(inst.getProcInstId())) {
            Task next = taskSupport.assignCurrentTasks(inst, fd);
            // 记录本次流转经过网关时命中的分支（变量快照 + 表达式 + 分支名）
            branchSupport.recordBranchHit(inst, fd, next == null ? null : next.getTaskDefinitionKey());
        } else {
            lifecycleSupport.finish(inst, ApprovalConstants.STATUS_PASSED);
        }
    }

    public void withdraw(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        if (!inst.getApplicantId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (inst.getStatus() != ApprovalConstants.STATUS_APPROVING) {
            throw new BizException(ErrorCode.INSTANCE_CANNOT_WITHDRAW);
        }
        engine.deleteProcessInstance(inst.getProcInstId(), "撤销");
        instanceSupport.insertRecord(inst, "withdraw", userId, "撤销", "发起人撤销");
        lifecycleSupport.finish(inst, ApprovalConstants.STATUS_WITHDRAWN);
    }

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
        rec.setOpinion("转办给 " + assembler.userName(req.getTargetUserId()));
        recordMapper.insert(rec);
    }

    /** 当前登录用户是否超级管理员（role_code=SUPER_ADMIN） */
    private boolean isSuperAdmin() {
        return SecurityContextUtil.getCurrentUser().getRoles().contains(ApprovalConstants.SUPER_ADMIN_CODE);
    }
}
