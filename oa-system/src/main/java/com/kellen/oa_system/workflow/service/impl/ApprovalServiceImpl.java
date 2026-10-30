package com.kellen.oa_system.workflow.service.impl;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.workflow.dto.ApprovalCcRequest;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.service.ApprovalService;
import com.kellen.oa_system.workflow.vo.ApprovalCcVO;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;
import com.kellen.oa_system.workflow.vo.CcUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审批运行时门面。
 *
 * <p>本类只做两件事：<b>事务边界</b>（19 处注解全部留在这里）与<b>委派</b>。</p>
 *
 * <p>三条设计约束，改动前请先看：</p>
 * <ol>
 *   <li><b>终态只有一个收敛点</b>：{@code ApprovalLifecycleSupport#finish}。
 *       审批驳回、撤销、终止、超时驳回四条路径都调它，因为业务表回写
 *       （请假/加班/出差/补卡）挂在那里。别在任何新入口里自己写 {@code update status}。</li>
 *   <li><b>审批人以「候选人」挂到 Flowable 任务上，不设 assignee</b>，便于用
 *       taskCandidateUser 做待办检索；节点审批人由模板 node_config 解析。</li>
 *   <li><b>每次动作写一条流转记录</b>，同时驱动「我的已办」与详情时间轴，
 *       避免直接依赖 Flowable 历史表做按候选人检索。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalStartService startService;
    private final ApprovalQueryService queryService;
    private final ApprovalInstanceQueryService instanceQueryService;
    private final ApprovalActionService actionService;
    private final ApprovalDetailService detailService;
    private final ApprovalAdminService adminService;
    private final ApprovalCcService ccService;
    private final ApprovalTimeoutService timeoutService;

    // ---------- 发起 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(ApprovalStartRequest req) {
        return startService.start(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(ApprovalStartRequest req) {
        return startService.saveDraft(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long instanceId, ApprovalStartRequest req) {
        startService.updateDraft(instanceId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitDraft(Long instanceId) {
        startService.submitDraft(instanceId);
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    @Override
    public PageResult<ApprovalTodoVO> todo(int pageNo, int pageSize) {
        return queryService.todo(pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<ApprovalRecordVO> done(int pageNo, int pageSize) {
        return queryService.done(pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<ApprovalInstanceVO> mine(Integer status, int pageNo, int pageSize) {
        return queryService.mine(status, pageNo, pageSize);
    }

    @Override
    public PageResult<ApprovalInstanceVO> page(String title, String applicantName, Integer status,
                                                String flowCode, String beginTime, String endTime,
                                                int pageNo, int pageSize) {
        return instanceQueryService.page(title, applicantName, status, flowCode, beginTime, endTime,
                pageNo, pageSize);
    }

    // ---------- 管理动作 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long instanceId) {
        adminService.terminate(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspend(Long instanceId) {
        adminService.suspend(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long instanceId) {
        adminService.resume(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyTimeouts() {
        return timeoutService.applyTimeouts();
    }

    // ---------- 抄送 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cc(ApprovalCcRequest req) {
        ccService.cc(req);
    }

    @Override
    public PageResult<ApprovalCcVO> ccPage(Integer readFlag, int pageNo, int pageSize) {
        return ccService.ccPage(readFlag, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readCc(Long ccId) {
        ccService.readCc(ccId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAllCc() {
        ccService.readAllCc();
    }

    @Override
    public List<CcUserVO> ccUsers() {
        return ccService.ccUsers();
    }

    // ---------- 审批动作 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInstance(Long instanceId) {
        adminService.deleteInstance(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(ApprovalCompleteRequest req) {
        actionService.complete(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long instanceId) {
        actionService.withdraw(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(ApprovalTransferRequest req) {
        actionService.transfer(req);
    }

    // ---------- 详情 ----------

    @Transactional(readOnly = true)
    @Override
    public ApprovalDetailVO detail(Long instanceId) {
        return detailService.detail(instanceId);
    }
}
