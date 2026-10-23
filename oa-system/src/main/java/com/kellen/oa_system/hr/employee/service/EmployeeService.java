package com.kellen.oa_system.hr.employee.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.employee.dto.CertSaveRequest;
import com.kellen.oa_system.hr.employee.dto.ContractSaveRequest;
import com.kellen.oa_system.hr.employee.dto.EmployeePageQuery;
import com.kellen.oa_system.hr.employee.dto.EmployeeProfileRequest;
import com.kellen.oa_system.hr.employee.vo.ChangeVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeDetailVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 员工档案业务接口。
 *
 * <p>「新增员工」不在本接口内：它复用用户管理模块的 {@code POST /system/user}
 * （工号生成、默认角色、初始密码都在那里），HR 档案页只负责**查看与维护档案**，
 * 避免同一张 oa_user 出现两套写入逻辑导致字段不同步。</p>
 */
public interface EmployeeService {

    /** 花名册分页（受数据范围约束：HR 全部 / 主管本部门及下级 / 员工本人） */
    PageResult<EmployeeVO> page(EmployeePageQuery query);

    /** 档案详情（含合同、证照、异动记录） */
    EmployeeDetailVO detail(Long userId);

    /** 更新档案（分区提交，只更新传入字段） */
    void updateProfile(Long userId, EmployeeProfileRequest request);

    /** 新增或续签合同 */
    Long saveContract(Long userId, ContractSaveRequest request);

    /** 删除合同（逻辑删） */
    void deleteContract(Long userId, Long contractId);

    /** 新增或修改证照 */
    Long saveCert(Long userId, CertSaveRequest request);

    /** 删除证照（逻辑删） */
    void deleteCert(Long userId, Long certId);

    /** 异动记录 */
    List<ChangeVO> listChanges(Long userId);

    /** 花名册导出（不含身份证、银行卡） */
    void export(HttpServletResponse response, EmployeePageQuery query);
}
