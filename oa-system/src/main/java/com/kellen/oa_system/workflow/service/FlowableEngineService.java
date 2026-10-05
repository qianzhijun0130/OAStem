package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public Task getTask(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public boolean isProcessActive(String procInstId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).count() > 0;
    }

    public void deleteProcessInstance(String procInstId, String reason) {
        runtimeService.deleteProcessInstance(procInstId, reason);
    }

    /** 启动流程实例，返回流程实例 id（businessKey 用审批单 id） */
    public String startProcessInstance(String flowCode, String businessKey, Map<String, Object> vars) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(flowCode, businessKey, vars);
        return pi.getId();
    }

    /** 完成用户任务（携带变量，如审批结论 outcome） */
    public void complete(String taskId, Map<String, Object> vars) {
        taskService.complete(taskId, vars);
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
