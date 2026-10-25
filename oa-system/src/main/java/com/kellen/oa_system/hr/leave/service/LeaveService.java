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

    /**
     * 销假（提前返岗登记）。
     *
     * <p>只允许对「已通过」且「未销假」的请假单操作一次，按实际休假天数退回差额额度。
     * 销假是<b>事实登记</b>而不是诉求，因此不另起审批流——多一道审批只会让「人已经回来上班了、
     * 系统里还在休假」的状态拖得更久。</p>
     *
     * @param returnDate 实际返岗日期（须晚于请假开始日、不晚于请假结束日）
     */
    void returnFromLeave(Long leaveId, java.time.LocalDate returnDate, String remark);

    /**
     * 导出请假记录（与列表同一套筛选条件）。
     *
     * <p>直接写响应流，因此不返回 {@code Result}；导出带上限 5000 行，
     * 需要全量时按月份或部门分批。</p>
     */
    void export(LeavePageQuery query, jakarta.servlet.http.HttpServletResponse response);
}
