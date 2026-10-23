package com.kellen.oa_system.hr.leave.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.leave.dto.LeavePageQuery;
import com.kellen.oa_system.hr.leave.dto.LeaveSubmitRequest;
import com.kellen.oa_system.hr.leave.vo.LeaveVO;

import java.util.List;

/**
 * 请假业务接口。
 *
 * <p>提交时同时做三件事：落业务单、占额度、起审批。三者必须同事务，
 * 否则会出现「单子建了但没进流程」或「流程在跑但额度没占」。</p>
 */
public interface LeaveService {

    /** 提交请假（校验冲突与额度后直接进审批） */
    Long submit(LeaveSubmitRequest request);

    /** 撤销：审批中可撤销，额度随之释放（由审批回调统一处理） */
    void cancel(Long leaveId);

    /** 请假单分页（HR 视角，受数据范围约束） */
    PageResult<LeaveVO> page(LeavePageQuery query);

    /** 我的请假 */
    List<LeaveVO> mine(Integer leaveStatus);

    /** 详情（本人 / 直属上级 / HR 可见） */
    LeaveVO detail(Long leaveId);
}
