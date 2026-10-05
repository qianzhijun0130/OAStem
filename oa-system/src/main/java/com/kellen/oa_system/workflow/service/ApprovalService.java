package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.workflow.dto.ApprovalCompleteRequest;
import com.kellen.oa_system.workflow.dto.ApprovalStartRequest;
import com.kellen.oa_system.workflow.dto.ApprovalTransferRequest;
import com.kellen.oa_system.workflow.vo.ApprovalDetailVO;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import com.kellen.oa_system.workflow.vo.ApprovalTodoVO;

/**
 * 审批运行时：发起、待办、已办、我的发起、同意/驳回、撤销、转办、详情。
 *
 * <p>当前登录用户统一在方法内通过 {@code SecurityContextUtil} 获取，Controller 不传 userId。</p>
 */
public interface ApprovalService {

    /** 发起申请，返回审批单 id */
    Long start(ApprovalStartRequest req);

    PageResult<ApprovalTodoVO> todo(int pageNo, int pageSize);

    PageResult<ApprovalRecordVO> done(int pageNo, int pageSize);

    PageResult<ApprovalInstanceVO> mine(Integer status, int pageNo, int pageSize);

    void complete(ApprovalCompleteRequest req);

    void withdraw(Long instanceId);

    void transfer(ApprovalTransferRequest req);

    ApprovalDetailVO detail(Long instanceId);
}
