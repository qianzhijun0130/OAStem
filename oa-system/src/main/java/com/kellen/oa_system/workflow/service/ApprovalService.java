package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.workflow.dto.ApprovalCcRequest;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.vo.ApprovalCcVO;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.CcUserVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;

/**
 * 审批运行时：发起、待办、已办、我的发起、同意/驳回、撤销、转办、详情。
 *
 * <p>当前登录用户统一在方法内通过 {@code SecurityContextUtil} 获取，Controller 不传 userId。</p>
 */
public interface ApprovalService {

    /** 发起申请（直接提交），返回审批单 id */
    Long start(ApprovalStartRequest req);

    /** 保存草稿：仅落审批单、不启动流程，返回审批单 id */
    Long saveDraft(ApprovalStartRequest req);

    /** 更新草稿内容（仅本人、仅草稿状态；避免重复点「保存」产生多张草稿） */
    void updateDraft(Long instanceId, ApprovalStartRequest req);

    /** 提交草稿：把本人草稿送进流程 */
    void submitDraft(Long instanceId);

    PageResult<ApprovalTodoVO> todo(int pageNo, int pageSize);

    PageResult<ApprovalRecordVO> done(int pageNo, int pageSize);

    PageResult<ApprovalInstanceVO> mine(Integer status, int pageNo, int pageSize);

    /**
     * 流程实例全量分页（管理员视角）：支持标题/发起人姓名/状态/流程编码/发起时间范围检索。
     */
    PageResult<ApprovalInstanceVO> page(String title, String applicantName, Integer status,
            String flowCode, String beginTime, String endTime, int pageNo, int pageSize);

    /** 强制终止审批中的流程实例（状态置 6 已终止，清理 Flowable 运行态） */
    void terminate(Long instanceId);

    /** 挂起审批中的流程实例（状态置 7 已挂起，任务暂时退出候选人待办） */
    void suspend(Long instanceId);

    /** 恢复已挂起的流程实例（状态回到 2 审批中，任务重新回到待办） */
    void resume(Long instanceId);

    /**
     * 扫描并处置「已到期未处置」的审批（阶段四·到期自动动作）。
     *
     * <p>由定时任务每分钟调用。动作取当前节点的 {@code node_config.timeoutAction}：
     * 0 仅标记、1 提醒、2 自动驳回。处理过会写 {@code timeout_handled_at} 保证幂等。</p>
     *
     * @return 本轮处置条数（无变化返回 0，便于静默）
     */
    int applyTimeouts();

    /** 抄送：把审批单抄送给若干人（仅告知，不产生待办、不影响流转） */
    void cc(ApprovalCcRequest req);

    /** 我的抄送任务分页（readFlag 为空表示全部，0 未读、1 已读） */
    PageResult<ApprovalCcVO> ccPage(Integer readFlag, int pageNo, int pageSize);

    /** 标记单条抄送已读（只能标记自己的） */
    void readCc(Long ccId);

    /** 我的抄送全部标记已读 */
    void readAllCc();

    /** 抄送人候选（仅启用用户，供抄送弹窗使用，不要求用户查询权限） */
    java.util.List<CcUserVO> ccUsers();

    /** 删除流程实例（逻辑删除；运行态实例先清理 Flowable 运行态） */
    void deleteInstance(Long instanceId);

    void complete(ApprovalCompleteRequest req);

    void withdraw(Long instanceId);

    void transfer(ApprovalTransferRequest req);

    ApprovalDetailVO detail(Long instanceId);
}
