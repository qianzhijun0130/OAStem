package com.kellen.oa_system.hr.leave.service;

import com.kellen.oa_system.hr.leave.dto.LeavePolicySaveRequest;
import com.kellen.oa_system.hr.leave.vo.LeavePolicyVO;

import java.util.List;
import java.util.Map;

/**
 * 地方假别政策维护。
 */
public interface LeavePolicyService {

    /**
     * 查询政策列表。
     *
     * @param year 生效年度，为空取当前年
     * @param city 城市，为空不限
     */
    List<LeavePolicyVO> list(Integer year, String city);

    /** 支持配置政策的假别（婚假/产假/陪产假/丧假），供前端下拉 */
    List<Map<String, Object>> policyTypes();

    /** 新增或修改；返回主键 */
    Long save(LeavePolicySaveRequest request);

    void delete(Long id);
}
