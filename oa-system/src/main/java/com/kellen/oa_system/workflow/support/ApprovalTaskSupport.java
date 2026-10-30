package com.kellen.oa_system.workflow.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.workflow.dto.NodeConfigItem;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前节点的任务挂载：把审批人以「候选人」方式挂到 Flowable 任务上，并维护当前节点与超时计时。
 *
 * <p>这是发起、审批流转、恢复三处共用的同一步。三条路径各写一遍的话，
 * 最容易漏的是<b>清空上一节点的 {@code due_time} 与 {@code timeout_handled_at}</b>：
 * 漏了前者会造成「新节点一进入就显示已逾期」，漏了后者会让本节点到期被当成「已处置」而永不再扫。</p>
 *
 * <p>注意两者都必须用显式 SET：{@code updateById} 的 NOT_NULL 策略写不进 null。</p>
 */
@Component
@RequiredArgsConstructor
public class ApprovalTaskSupport {

    private final FlowableEngineService engine;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalAssigneeSupport assigneeSupport;

    /**
     * 节点未单独配置超时时的默认小时数（阶段四）。0 表示不启用超时。
     * 单节点可用 {@code node_config.timeoutHours} 覆盖。
     */
    @Value("${approval.timeout.hours:0}")
    private int defaultTimeoutHours;

    /**
     * 把当前流程实例的所有活动任务挂上候选人，并更新审批单当前节点。
     *
     * @return 首个活动任务（供分支命中日志反推落点）；无任务时返回 null
     */
    public Task assignCurrentTasks(ApprovalInstance inst, FlowDefinition fd) {
        List<Task> tasks = engine.listTasks(inst.getProcInstId());
        if (tasks.isEmpty()) {
            return null;
        }
        LocalDateTime dueTime = null;
        for (Task task : tasks) {
            List<Long> candidates = assigneeSupport.resolveCandidates(
                    fd, inst.getApplicantId(), task.getTaskDefinitionKey());
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
    public int timeoutHoursOf(FlowDefinition fd, String nodeId) {
        if (fd != null && nodeId != null) {
            for (NodeConfigItem item : assigneeSupport.parseNodeConfig(fd.getNodeConfig())) {
                if (nodeId.equals(item.getNodeId()) && item.getTimeoutHours() != null) {
                    return Math.max(item.getTimeoutHours(), 0);
                }
            }
        }
        return Math.max(defaultTimeoutHours, 0);
    }
}
