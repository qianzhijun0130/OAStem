package com.kellen.oa_system.hr.attendance.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.attendance.dto.HrRequestSaveRequest;
import com.kellen.oa_system.hr.attendance.vo.HrRequestVO;

import java.util.List;

/**
 * 加班 / 出差 / 补卡 业务接口。
 *
 * <p>三者共用提交、撤销、分页、我的四个动作，因此收在一个服务里；
 * 各类型的差异化校验在实现内按 {@code bizType} 分支处理。</p>
 */
public interface HrRequestService {

    /** 提交申请（校验后落台账并进入审批） */
    Long submit(HrRequestSaveRequest request);

    /** 撤销本人审批中的申请 */
    void cancel(Integer bizType, Long id);

    /** 台账分页：bizType 1 加班 2 出差 3 补卡 */
    PageResult<HrRequestVO> page(Integer bizType, String month, Long deptId, Integer status,
                                 String keyword, Integer pageNo, Integer pageSize);

    /** 我的申请 */
    List<HrRequestVO> mine(Integer bizType);
}
