package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.support.ApprovalAssigneeSupport;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalInstanceSupport;
import com.kellen.oa_system.workflow.support.ApprovalLifecycleSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 到期审批的自动处置（阶段四）。
 *
 * <p><b>幂等</b>：只捞 {@code due_time <= now && timeout_handled_at IS NULL} 的记录，
 * 处理后写 {@code timeout_handled_at}；进入下一节点时该字段被重置，故每个节点最多触发一次。</p>
 *
 * <p><b>事务</b>：整批一个事务（由门面开启）。量级极小（只含已到期未处置），
 * 单条异常整批回滚、下一分钟重试，比「部分成功」更容易推理。</p>
 *
 * <p>自动驳回同样要经过终态收敛点，否则业务表不会回写，请假/加班单会永远停在「审批中」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTimeoutService {

    private final ApprovalInstanceMapper instanceMapper;
    private final FlowableEngineService engine;
    private final ApprovalInstanceSupport instanceSupport;
    private final ApprovalAssigneeSupport assigneeSupport;
    private final ApprovalLifecycleSupport lifecycleSupport;

    public int applyTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalInstance> due = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalConstants.STATUS_APPROVING)
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
            int action = timeoutActionOf(instanceSupport.getFlowDef(inst), nodeId);
            if (action == ApprovalConstants.TIMEOUT_ACTION_REJECT) {
                instanceSupport.insertRecord(inst, "timeout", null, "超时", "已超时，系统自动驳回");
                if (inst.getProcInstId() != null) {
                    engine.deleteProcessInstance(inst.getProcInstId(), "超时自动驳回");
                }
                lifecycleSupport.finish(inst, ApprovalConstants.STATUS_REJECTED);
                continue;
            }
            instanceSupport.insertRecord(inst, "timeout", null, "超时",
                    action == ApprovalConstants.TIMEOUT_ACTION_REMIND ? "已超时，已提醒当前审批人" : "已超时");
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
            for (NodeConfigItem item : assigneeSupport.parseNodeConfig(fd.getNodeConfig())) {
                if (nodeId.equals(item.getNodeId()) && item.getTimeoutAction() != null) {
                    return Math.max(item.getTimeoutAction(), 0);
                }
            }
        }
        return ApprovalConstants.TIMEOUT_ACTION_MARK;
    }
}
