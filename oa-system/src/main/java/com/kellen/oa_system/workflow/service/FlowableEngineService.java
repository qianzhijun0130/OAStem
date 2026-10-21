package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 引擎能力封装。
 *
 * <p>仅暴露审批模块需要的薄封装：部署 / 卸载 BPMN、取最新流程定义、读取资源 XML、
 * 给任务挂候选人、判断流程是否仍在运行、删除流程实例。业务层不直接接触 Flowable
 * 的 Query API，统一走这里，便于后续替换为自研引擎或加日志/审计。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowableEngineService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    /** 部署一份 BPMN（资源名固定为 {key}.bpmn20.xml 以便 Flowable 识别） */
    public Deployment deploy(String flowCode, String bpmnXml) {
        return repositoryService.createDeployment()
                .name(flowCode)
                .addString(flowCode + ".bpmn20.xml", bpmnXml)
                .deploy();
    }

    /** 卸载部署（级联删除流程定义与历史） */
    public void undeploy(String deploymentId) {
        if (deploymentId != null && !deploymentId.isBlank()) {
            repositoryService.deleteDeployment(deploymentId, true);
        }
    }

    /** 取某流程 key 的最新版本定义 */
    public ProcessDefinition getLatestProcessDefinition(String flowCode) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(flowCode)
                .latestVersion()
                .singleResult();
    }

    /** 读取流程部署里的 BPMN 资源文本（用于前端 modeler 回显） */
    public String getResourceXml(String deploymentId, String resourceName) {
        try (InputStream is = repositoryService.getResourceAsStream(deploymentId, resourceName)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取流程资源失败 deploymentId={} resource={}", deploymentId, resourceName, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取流程资源失败");
        }
    }

    /** 给任务挂多个候选人（按用户 id）；审批人解析结果统一走候选人，便于「待办」检索 */
    public void addCandidateUsers(String taskId, List<Long> userIds) {
        for (Long uid : userIds) {
            taskService.addCandidateUser(taskId, String.valueOf(uid));
        }
    }

    /**
     * 清空任务的 assignee。
     *
     * <p>审批人统一由模板 node_config 解析并以「候选人」方式挂载。若 BPMN 里写了
     * flowable:assignee，任务会被锁定到固定人，taskCandidateUser 检索不到，
     * 待办列表就会出现「流程在跑但没人能处理」。挂候选人前先清空 assignee 可避免该问题。</p>
     */
    public void clearAssignee(String taskId) {
        taskService.setAssignee(taskId, null);
    }

    /** 移除任务上的某个候选人（转办时移出原处理人） */
    public void deleteCandidateUser(String taskId, Long userId) {
        taskService.deleteCandidateUser(taskId, String.valueOf(userId));
    }

    /**
     * 摘除任务上的全部候选人（挂起时使用）。
     *
     * <p><b>为什么不能只靠 suspend 引擎实例</b>：Flowable 的 {@code taskCandidateUser} 查询
     * <b>不会</b>排除挂起实例下的任务（TaskQuery 只提供按时间/人查「挂起」的能力，没有 active 布尔过滤），
     * 仅 suspend 的话审批人待办里仍会看到这条单据。摘掉候选人后待办自然消失，
     * 恢复时再按 node_config 重新解析即可。</p>
     */
    public void clearCandidateUsers(String taskId) {
        taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> link.getUserId() != null)
                .forEach(link -> taskService.deleteCandidateUser(taskId, link.getUserId()));
    }

    public Task getTask(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public boolean isProcessActive(String procInstId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).count() > 0;
    }

    /**
     * 删除流程实例（驳回 / 撤销时调用）。
     *
     * <p>并发场景：两人同时处理同一申请（例如并发驳回），胜者已结束并删除实例，
     * 败者再删会抛 {@link FlowableObjectNotFoundException}。这属正常竞争结果而非故障，
     * 视为幂等忽略（流程已不在，删除本就是「让它不在」）。</p>
     *
     * <p>其余 Flowable / 持久层异常交给 {@link #asConcurrencyConflict} 翻译（D7：
     * 并发审批撞 MySQL 死锁不应变成 5000）。</p>
     */
    public void deleteProcessInstance(String procInstId, String reason) {
        try {
            runtimeService.deleteProcessInstance(procInstId, reason);
        } catch (FlowableObjectNotFoundException e) {
            log.warn("删除流程实例时实例已不存在（可能被并发操作提前结束）: procInstId={}", procInstId);
        } catch (FlowableException | PersistenceException e) {
            throw asConcurrencyConflict(e);
        }
    }

    /**
     * 挂起流程实例（阶段四·挂起/恢复）。
     *
     * <p>挂起后该实例下所有任务都不再被 {@code taskCandidateUser} 查到——表现为「待办里暂时消失」，
     * 这正是业务想要的「先放一放、不占用审批人的待办」。恢复后任务原样回到待办。</p>
     *
     * <p>实例已不存在时按 3101 处理；其余引擎异常交给并发冲突翻译（与 complete 同口径）。</p>
     */
    public void suspendProcessInstance(String procInstId) {
        try {
            runtimeService.suspendProcessInstanceById(procInstId);
        } catch (FlowableObjectNotFoundException e) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在或已结束");
        } catch (FlowableException | PersistenceException e) {
            throw asConcurrencyConflict(e);
        }
    }

    /** 恢复流程实例：原任务重新回到候选人待办 */
    public void activateProcessInstance(String procInstId) {
        try {
            runtimeService.activateProcessInstanceById(procInstId);
        } catch (FlowableObjectNotFoundException e) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在或已结束");
        } catch (FlowableException | PersistenceException e) {
            throw asConcurrencyConflict(e);
        }
    }

    /** 启动流程实例，返回流程实例 id（businessKey 用审批单 id） */
    public String startProcessInstance(String flowCode, String businessKey, Map<String, Object> vars) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(flowCode, businessKey, vars);
        return pi.getId();
    }

    /**
     * 完成用户任务（携带变量，如审批结论 outcome）。
     *
     * <p>两人同时审批同一任务时，败者的事务可能在 commit 阶段撞上 MySQL 死锁
     * （Flowable 更新 execution 行时加锁顺序交错），或任务已被胜者删除后抛
     * {@link FlowableObjectNotFoundException}。这些都不是系统故障，而是「单据已被他人处理」
     * 的正常竞争结果，应当转成业务码而非 5000（D7 缺陷）。</p>
     */
    public void complete(String taskId, Map<String, Object> vars) {
        try {
            taskService.complete(taskId, vars);
        } catch (FlowableException | PersistenceException e) {
            throw asConcurrencyConflict(e);
        }
    }

    /**
     * 把 Flowable / 持久层在并发审批路径上产生的异常翻译成可读业务异常。
     *
     * <p>识别三类竞争结果：
     * <ul>
     *   <li>任务已不存在（{@link FlowableObjectNotFoundException}）→ 3103 任务不存在；</li>
     *   <li>死锁 / 乐观锁（消息含 deadlock / lock）→ 3102 审批单已结束，无法操作；</li>
     *   <li>其余持久层 / 引擎异常 → 5000 系统错误（真实故障，不掩盖）。</li>
     * </ul>
     * 包装异常由 Spring 包了一层，故递归下钻找第一个 Flowable / 持久层根因再判断。</p>
     */
    private BizException asConcurrencyConflict(Exception e) {
        Throwable root = e;
        Throwable flowableOrPersistence = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
            if (root instanceof FlowableException || root instanceof PersistenceException) {
                flowableOrPersistence = root;
            }
        }
        String msg = flowableOrPersistence.getMessage();
        msg = msg != null ? msg.toLowerCase() : "";
        if (flowableOrPersistence instanceof FlowableObjectNotFoundException) {
            return new BizException(ErrorCode.TASK_NOT_FOUND, "审批任务已被处理，请刷新列表");
        }
        if (flowableOrPersistence instanceof FlowableOptimisticLockingException
                || msg.contains("deadlock") || msg.contains("lock wait")) {
            return new BizException(ErrorCode.INSTANCE_ALREADY_DONE, "审批单已被他人处理，请刷新列表");
        }
        return new BizException(ErrorCode.SYSTEM_ERROR, "流程引擎调用失败");
    }

    /**
     * 更新运行中流程实例的变量。
     *
     * <p>用于「节点填了表单的可编辑字段」的场景：{@code form_data} 更新后，
     * 同步写回流程变量，保证后续节点的条件表达式读到的是最新值
     * （例如销假节点填了实际返岗天数，后面按该值分支）。</p>
     *
     * <p>实例已结束时 Flowable 会抛异常，此处吞掉并记日志 ——
     * 变量同步只是锦上添花，不该让审批本身失败。</p>
     */
    public void setVariables(String procInstId, Map<String, Object> vars) {
        if (procInstId == null || vars == null || vars.isEmpty()) {
            return;
        }
        try {
            runtimeService.setVariables(procInstId, vars);
        } catch (Exception e) {
            log.warn("同步流程变量失败（不影响审批结果）: procInstId={}", procInstId, e);
        }
    }

    /**
     * 取某流程实例当前的全部流程变量快照。
     *
     * <p>用于「分支命中日志」：网关路由时需要把当时参与判断的变量值一并留存，
     * 否则事后无法解释「为什么走了这条分支」。</p>
     *
     * <p>实例已结束或读取失败时返回空表 —— 命中日志是追溯辅助，
     * 不该因为快照取不到就让审批动作失败。</p>
     */
    public Map<String, Object> getVariables(String procInstId) {
        if (procInstId == null) {
            return Map.of();
        }
        try {
            return new HashMap<>(runtimeService.getVariables(procInstId));
        } catch (Exception e) {
            log.warn("读取流程变量快照失败（不影响审批结果）: procInstId={}", procInstId, e);
            return Map.of();
        }
    }

    /**
     * 设置任务处理截止时间（阶段四·超时）。
     *
     * <p>写在 Flowable 的 {@code DUE_} 上，引擎侧可用 {@code taskDueBefore} 检索逾期任务；
     * 业务侧的「是否逾期」以 {@code oa_approval_instance.due_time} 为准（列表免 N+1）。</p>
     */
    public void setTaskDueDate(String taskId, LocalDateTime dueTime) {
        if (taskId == null || dueTime == null) {
            return;
        }
        taskService.setDueDate(taskId, Date.from(dueTime.atZone(ZoneId.systemDefault()).toInstant()));
    }

    /** 某流程实例下的全部当前任务（一般单条，并行网关会多条） */
    public List<Task> listTasks(String procInstId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).list();
    }

    /** 当前用户是否为某任务的候选人（待办鉴权） */
    public boolean isCandidate(String taskId, String userId) {
        return taskService.createTaskQuery().taskId(taskId).taskCandidateUser(userId).count() > 0;
    }

    /** 待办数量 */
    public long countCandidateTasks(String userId) {
        return taskService.createTaskQuery().taskCandidateUser(userId).count();
    }

    /** 待办分页（按创建时间倒序） */
    public List<Task> listCandidateTasks(String userId, int firstResult, int maxResults) {
        return taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(firstResult, maxResults);
    }
}
