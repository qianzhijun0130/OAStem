package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.support.ApprovalVoAssembler;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批查询：待办 / 已办 / 我的发起 / 全量分页。
 *
 * <p>四个列表的共同要求是「一页不要打出几十条 SQL」：待办要先拿引擎任务、再批量取实例、
 * 流程名、申请人；已办要先分页记录、再批量取实例、流程名、处理人。这里的批量预取都是
 * BE-OPT-05 的成果，改动时请沿用——退化成逐行单查不会报错，只是慢得没人察觉。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalQueryService {

    private final FlowableEngineService engine;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final UserMapper userMapper;
    private final ApprovalVoAssembler assembler;

    public PageResult<ApprovalTodoVO> todo(int pageNo, int pageSize) {
        pageNo = ApprovalVoAssembler.safePageNo(pageNo);
        pageSize = ApprovalVoAssembler.safePageSize(pageSize);
        Long userId = SecurityContextUtil.getCurrentUserId();
        long total = engine.countCandidateTasks(String.valueOf(userId));
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        List<Task> tasks = engine.listCandidateTasks(String.valueOf(userId), (pageNo - 1) * pageSize, pageSize);
        List<ApprovalTodoVO> vos = new ArrayList<>();
        if (tasks.isEmpty()) {
            return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
        }

        // 三处批量预取（BE-OPT-05）：实例、流程名、申请人姓名。
        // 原实现每条任务一次 selectOne + 每行两次单查，一页 10 条即约 30 条 SQL。
        Set<String> procIds = new HashSet<>();
        for (Task t : tasks) {
            if (t.getProcessInstanceId() != null) {
                procIds.add(t.getProcessInstanceId());
            }
        }
        Map<String, ApprovalInstance> instByProc = new HashMap<>();
        if (!procIds.isEmpty()) {
            for (ApprovalInstance inst : instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                    .in(ApprovalInstance::getProcInstId, procIds)
                    .eq(ApprovalInstance::getIsDeleted, 0))) {
                instByProc.put(inst.getProcInstId(), inst);
            }
        }
        Set<Long> flowDefIds = new HashSet<>();
        Set<Long> applicantIds = new HashSet<>();
        for (ApprovalInstance inst : instByProc.values()) {
            if (inst.getFlowDefId() != null) {
                flowDefIds.add(inst.getFlowDefId());
            }
            if (inst.getApplicantId() != null) {
                applicantIds.add(inst.getApplicantId());
            }
        }
        Map<Long, String> flowNameMap = new HashMap<>();
        if (!flowDefIds.isEmpty()) {
            for (FlowDefinition fd : flowDefinitionMapper.selectBatchIds(flowDefIds)) {
                flowNameMap.put(fd.getId(), fd.getFlowName());
            }
        }
        Map<Long, String> applicantNameMap = new HashMap<>();
        if (!applicantIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(applicantIds)) {
                applicantNameMap.put(u.getId(), u.getRealName());
            }
        }

        // instanceId → applicantId：循环结束后一次性解析部门/公司，避免逐行查库
        Map<Long, Long> applicantOfInstance = new HashMap<>();
        for (Task t : tasks) {
            ApprovalInstance inst = instByProc.get(t.getProcessInstanceId());
            if (inst == null) {
                continue;
            }
            ApprovalTodoVO vo = new ApprovalTodoVO();
            vo.setTaskId(t.getId());
            vo.setInstanceId(inst.getId());
            vo.setTitle(inst.getTitle());
            String flowName = inst.getFlowDefId() == null ? null : flowNameMap.get(inst.getFlowDefId());
            vo.setFlowName(flowName != null ? flowName : assembler.flowNameOf(inst));
            String applicantName = inst.getApplicantId() == null ? null
                    : applicantNameMap.get(inst.getApplicantId());
            vo.setApplicantName(applicantName != null ? applicantName : assembler.userName(inst.getApplicantId()));
            vo.setCurrentNode(t.getName() != null ? t.getName() : t.getTaskDefinitionKey());
            vo.setCreateTime(assembler.fmt(t.getCreateTime()));
            vos.add(vo);
            applicantOfInstance.put(inst.getId(), inst.getApplicantId());
        }
        Map<Long, String[]> org = assembler.resolveOrg(new ArrayList<>(applicantOfInstance.values()));
        for (ApprovalTodoVO vo : vos) {
            String[] names = org.get(applicantOfInstance.get(vo.getInstanceId()));
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    public PageResult<ApprovalRecordVO> done(int pageNo, int pageSize) {
        pageNo = ApprovalVoAssembler.safePageNo(pageNo);
        pageSize = ApprovalVoAssembler.safePageSize(pageSize);
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<ApprovalRecord> q = new LambdaQueryWrapper<>();
        q.eq(ApprovalRecord::getHandlerId, userId)
                .in(ApprovalRecord::getAction, "approve", "reject", "transfer")
                .orderByDesc(ApprovalRecord::getCreateTime);
        long total = recordMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalRecord> page = new Page<>(pageNo, pageSize);
        recordMapper.selectPage(page, q);
        // 批量取单据（原本 toRecordVO 内部逐条查，这里改成一次批量，顺带拿到申请人用于解析部门/公司）
        Set<Long> instIds = new HashSet<>();
        for (ApprovalRecord r : page.getRecords()) {
            instIds.add(r.getInstanceId());
        }
        Map<Long, ApprovalInstance> instMap = new HashMap<>();
        if (!instIds.isEmpty()) {
            for (ApprovalInstance inst : instanceMapper.selectBatchIds(instIds)) {
                instMap.put(inst.getId(), inst);
            }
        }
        List<Long> applicants = new ArrayList<>();
        for (ApprovalInstance inst : instMap.values()) {
            applicants.add(inst.getApplicantId());
        }
        Map<Long, String[]> org = assembler.resolveOrg(applicants);

        // 流程名与处理人姓名同样批量预取。
        // 此前 toRecordVO 内部逐行调 flowNameOf / userName，一页 10 条就多 20 次单行查询——
        // 注释写着「已改成一次批量」，实际只预取了实例，另两项仍在逐行查（BE-OPT-05）。
        Set<Long> flowDefIds = new HashSet<>();
        Set<Long> handlerIds = new HashSet<>();
        for (ApprovalRecord r : page.getRecords()) {
            handlerIds.add(r.getHandlerId());
            ApprovalInstance inst = instMap.get(r.getInstanceId());
            if (inst != null && inst.getFlowDefId() != null) {
                flowDefIds.add(inst.getFlowDefId());
            }
        }
        Map<Long, String> flowNameMap = new HashMap<>();
        if (!flowDefIds.isEmpty()) {
            for (FlowDefinition fd : flowDefinitionMapper.selectBatchIds(flowDefIds)) {
                flowNameMap.put(fd.getId(), fd.getFlowName());
            }
        }
        Map<Long, String> handlerNameMap = new HashMap<>();
        handlerIds.remove(null);
        if (!handlerIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(handlerIds)) {
                handlerNameMap.put(u.getId(), u.getRealName());
            }
        }

        List<ApprovalRecordVO> vos = new ArrayList<>();
        for (ApprovalRecord r : page.getRecords()) {
            ApprovalInstance inst = instMap.get(r.getInstanceId());
            ApprovalRecordVO vo = assembler.toRecordVO(r, inst, flowNameMap, handlerNameMap);
            String[] names = inst == null ? null : org.get(inst.getApplicantId());
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
            vos.add(vo);
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    public PageResult<ApprovalInstanceVO> mine(Integer status, int pageNo, int pageSize) {
        pageNo = ApprovalVoAssembler.safePageNo(pageNo);
        pageSize = ApprovalVoAssembler.safePageSize(pageSize);
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<ApprovalInstance> q = new LambdaQueryWrapper<>();
        q.eq(ApprovalInstance::getApplicantId, userId).eq(ApprovalInstance::getIsDeleted, 0);
        if (status != null) {
            q.eq(ApprovalInstance::getStatus, status);
        }
        q.orderByDesc(ApprovalInstance::getId);
        long total = instanceMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalInstance> page = new Page<>(pageNo, pageSize);
        instanceMapper.selectPage(page, q);
        List<ApprovalInstanceVO> vos = assembler.toInstanceVOs(page.getRecords());
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

}
