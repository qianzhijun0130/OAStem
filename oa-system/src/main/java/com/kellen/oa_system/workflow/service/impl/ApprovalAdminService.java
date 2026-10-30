package com.kellen.oa_system.workflow.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalInstanceSupport;
import com.kellen.oa_system.workflow.support.ApprovalLifecycleSupport;
import com.kellen.oa_system.workflow.support.ApprovalTaskSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理动作：强制终止 / 挂起 / 恢复 / 删除实例。
 *
 * <p><b>终止与撤销的区别</b>：撤销是发起人本人撤回自己的单；终止是具备实例管理权限的人
 * 对任意审批中（或已挂起）单据强停，状态记 6。</p>
 *
 * <p><b>挂起的顺序不能反</b>：必须先摘掉候选人再 suspend——引擎会拒绝修改已挂起任务的
 * identity link（{@code Cannot execute operation for a suspended Task}）。
 * 摘除后待办自然消失，因为 Flowable 的候选任务查询不会排除挂起实例下的任务。</p>
 *
 * <p>恢复时按 node_config 重新解析候选人（挂起时被摘除），同时重置当前节点与超时计时；
 * 不重建任务、不重算流程变量，因此挂起期间的人员变化不会影响恢复结果。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalAdminService {

    private final FlowableEngineService engine;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalInstanceSupport instanceSupport;
    private final ApprovalTaskSupport taskSupport;
    private final ApprovalLifecycleSupport lifecycleSupport;

    public void terminate(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        // 挂起态也允许终止：挂起只是暂停，若不先恢复就无法处置会让单据长期悬空
        if (inst.getStatus() != ApprovalConstants.STATUS_APPROVING
                && inst.getStatus() != ApprovalConstants.STATUS_SUSPENDED) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_DONE, "仅审批中或已挂起的流程可以终止");
        }
        if (inst.getProcInstId() != null && engine.isProcessActive(inst.getProcInstId())) {
            engine.deleteProcessInstance(inst.getProcInstId(), "管理员终止");
        }
        instanceSupport.insertRecord(inst, "terminate", userId, "终止", "管理员终止流程");
        lifecycleSupport.finish(inst, ApprovalConstants.STATUS_TERMINATED);
    }

    public void suspend(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        if (inst.getStatus() == ApprovalConstants.STATUS_SUSPENDED) {
            throw new BizException(ErrorCode.INSTANCE_ALREADY_SUSPENDED);
        }
        if (inst.getStatus() != ApprovalConstants.STATUS_APPROVING) {
            throw new BizException(ErrorCode.INSTANCE_CANNOT_SUSPEND);
        }
        if (inst.getProcInstId() != null) {
            // 顺序很关键：必须**先摘候选人、再 suspend**——引擎会拒绝修改已挂起任务的 identity link
            // （Cannot execute operation for a suspended Task）。摘除后待办自然消失，
            // 因为 Flowable 的候选任务查询不会排除挂起实例下的任务。
            engine.listTasks(inst.getProcInstId()).forEach(t -> engine.clearCandidateUsers(t.getId()));
            engine.suspendProcessInstance(inst.getProcInstId());
        }
        instanceSupport.saveStatus(inst, ApprovalConstants.STATUS_SUSPENDED);
        instanceSupport.insertRecord(inst, "suspend", userId, "挂起", "管理员挂起流程");
    }

    public void resume(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        if (inst.getStatus() != ApprovalConstants.STATUS_SUSPENDED) {
            throw new BizException(ErrorCode.INSTANCE_NOT_SUSPENDED);
        }
        if (inst.getProcInstId() != null) {
            engine.activateProcessInstance(inst.getProcInstId());
            // 恢复时按 node_config 重新解析候选人（挂起时被摘除），同时重置当前节点与超时计时
            taskSupport.assignCurrentTasks(inst, instanceSupport.getFlowDef(inst));
        }
        instanceSupport.saveStatus(inst, ApprovalConstants.STATUS_APPROVING);
        instanceSupport.insertRecord(inst, "resume", userId, "恢复", "管理员恢复流程");
    }

    /**
     * 删除流程实例（逻辑删除）。
     *
     * <p>若实例仍在运行（审批中且 Flowable 运行态存在），先清理引擎运行态再删库，
     * 避免遗留「幽灵 ACT_RU_*」导致待办/引擎查询出现脏数据。删除走 {@code deleteById}，
     * 受 {@code AuditEntity} 的逻辑删除拦截，仅标记 {@code is_deleted=1}，不物理抹除。</p>
     */
    public void deleteInstance(Long instanceId) {
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        if (inst.getStatus() == ApprovalConstants.STATUS_APPROVING
                && inst.getProcInstId() != null
                && engine.isProcessActive(inst.getProcInstId())) {
            engine.deleteProcessInstance(inst.getProcInstId(), "删除实例");
        }
        instanceMapper.deleteById(inst.getId());
    }
}
