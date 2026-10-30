package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.service.FlowableEngineService;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalFormSupport;
import com.kellen.oa_system.workflow.support.ApprovalInstanceSupport;
import com.kellen.oa_system.workflow.support.ApprovalVoAssembler;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审批详情：单据 + 流转时间轴 + 当前待办 + 表单/字段权限 + 是否可审批。
 *
 * <p><b>可见性判定有三条通道</b>：申请人本人、当前任务的候选人或历史处理人、持有
 * {@code system:approval:instance}（流程实例总览）或超管。三者取并集——
 * 少任何一条都会让正常用户拿到 403：例如主管审批完后想看回详情，
 * 此时他既不是申请人也不再是候选人，只能靠「历史处理人」这条。</p>
 *
 * <p>表单与字段权限都是「取不到就降级」：主表单取不到 → 前端按键值对展示；
 * 字段权限取不到 → 全部只读。详情页不能因为表单配置的脏数据打不开。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalDetailService {

    private final FlowableEngineService engine;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalInstanceSupport instanceSupport;
    private final ApprovalFormSupport formSupport;
    private final ApprovalVoAssembler assembler;

    public ApprovalDetailVO detail(Long instanceId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(instanceId);
        // 流程实例总览权限 / 超管：可查看任意单据详情，不受「参与人」可见性限制（修复 403 误判）
        boolean viewAnyInstance = canViewAnyInstance();
        // 运行中实例的首个任务：既用于「候选人可见性」判定，也用于详情里的当前待办与「可审批」标记
        Task runningTask = null;
        if (inst.getStatus() == ApprovalConstants.STATUS_APPROVING && inst.getProcInstId() != null) {
            List<Task> ts = engine.listTasks(inst.getProcInstId());
            if (!ts.isEmpty()) {
                runningTask = ts.get(0);
            }
        }
        boolean candidate = runningTask != null
                && engine.isCandidate(runningTask.getId(), String.valueOf(userId));
        if (!inst.getApplicantId().equals(userId) && !viewAnyInstance) {
            boolean handled = recordMapper.selectCount(new LambdaQueryWrapper<ApprovalRecord>()
                    .eq(ApprovalRecord::getInstanceId, inst.getId())
                    .eq(ApprovalRecord::getHandlerId, userId)) > 0;
            if (!candidate && !handled) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }

        ApprovalInstanceVO ivo = assembler.toInstanceVO(inst);
        List<ApprovalRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, inst.getId())
                .orderByAsc(ApprovalRecord::getCreateTime));
        List<ApprovalRecordVO> rvo = recs.stream().map(assembler::toRecordVO).toList();

        ApprovalTodoVO current = null;
        if (runningTask != null) {
            current = new ApprovalTodoVO();
            current.setTaskId(runningTask.getId());
            current.setInstanceId(inst.getId());
            current.setTitle(inst.getTitle());
            current.setFlowName(assembler.flowNameOf(inst));
            current.setApplicantName(assembler.userName(inst.getApplicantId()));
            current.setCurrentNode(runningTask.getName() != null
                    ? runningTask.getName() : runningTask.getTaskDefinitionKey());
            current.setCreateTime(assembler.fmt(runningTask.getCreateTime()));
        }
        ApprovalDetailVO detail = new ApprovalDetailVO();
        detail.setInstance(ivo);
        detail.setRecords(rvo);
        detail.setCurrentTask(current);
        // 下发发起环节的主表单规则：审批人打开待办时用同一份表单只读渲染，
        // 避免每个节点各绑一份造成内容与排版漂移。
        detail.setFormRuleJson(formSupport.primaryRuleJson(inst.getFlowCode()));
        // 当前节点的可编辑字段：未配置时为空集合（全部只读）。
        // 销假场景据此把「销假日期」等字段切成可编辑，员工填写后合并回同一份 form_data。
        detail.setEditableProps(current == null ? List.of()
                : formSupport.editablePropsOf(inst, current.getTaskId()));
        // 是否可审批：候选人或超管（超管可处理任意节点）。申请人 / 实例总览用户只读详情，
        // 前端据此隐藏审批区，避免「看得见按钮、点了却 3104」。
        detail.setCanApprove(current != null && (candidate || isSuperAdmin()));
        return detail;
    }

    /**
     * 当前用户能否查看任意流程实例（不受「参与人」限制）。
     *
     * <p>持有 {@code system:approval:instance}（流程实例总览页权限码）或为超管即可：该页面本身
     * 就是管理员总览，点开任意单据的详情是预期能力。</p>
     */
    private boolean canViewAnyInstance() {
        LoginUser u = SecurityContextUtil.getCurrentUser();
        return u.getPermissions().contains(ApprovalConstants.PERM_INSTANCE)
                || u.getRoles().contains(ApprovalConstants.SUPER_ADMIN_CODE);
    }

    private boolean isSuperAdmin() {
        return SecurityContextUtil.getCurrentUser().getRoles().contains(ApprovalConstants.SUPER_ADMIN_CODE);
    }
}
