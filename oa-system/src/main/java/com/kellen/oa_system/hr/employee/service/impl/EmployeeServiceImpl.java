package com.kellen.oa_system.hr.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.common.util.IdCardUtil;
import com.kellen.oa_system.hr.common.enums.EmploymentStatusEnum;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.common.support.HrRoles;
import com.kellen.oa_system.hr.common.support.SensitiveMask;
import com.kellen.oa_system.hr.employee.dto.CertSaveRequest;
import com.kellen.oa_system.hr.employee.dto.ContractSaveRequest;
import com.kellen.oa_system.hr.employee.dto.EmployeePageQuery;
import com.kellen.oa_system.hr.employee.dto.EmployeeProfileRequest;
import com.kellen.oa_system.hr.employee.entity.EmployeeBank;
import com.kellen.oa_system.hr.employee.entity.EmployeeCert;
import com.kellen.oa_system.hr.employee.entity.EmployeeChange;
import com.kellen.oa_system.hr.employee.entity.EmployeeContract;
import com.kellen.oa_system.hr.employee.mapper.EmployeeBankMapper;
import com.kellen.oa_system.hr.employee.mapper.EmployeeCertMapper;
import com.kellen.oa_system.hr.employee.mapper.EmployeeChangeMapper;
import com.kellen.oa_system.hr.employee.mapper.EmployeeContractMapper;
import com.kellen.oa_system.hr.employee.service.EmployeeService;
import com.kellen.oa_system.hr.employee.vo.CertVO;
import com.kellen.oa_system.hr.employee.vo.ChangeVO;
import com.kellen.oa_system.hr.employee.vo.ContractVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeDetailVO;
import com.kellen.oa_system.hr.employee.vo.EmployeeVO;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.system.support.IdCardGuard;
import com.kellen.oa_system.security.OnlineUserRegistry;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 员工档案业务实现。
 *
 * <p>本类只维护「档案」相关字段与三张侧表；工号、密码、角色仍由用户管理模块负责，
 * 避免同一张 oa_user 出现两套写入逻辑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    /** 可见敏感原文的角色 */

    private static final List<Integer> ON_DUTY = List.of(2, 3, 4);

    private static final List<String> EXPORT_HEADERS = List.of(
            "工号", "姓名", "性别", "部门", "岗位", "任职状态", "入职日期", "司龄", "手机号", "邮箱", "直属上级");

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final EmployeeContractMapper contractMapper;
    private final EmployeeCertMapper certMapper;
    private final EmployeeBankMapper bankMapper;
    private final EmployeeChangeMapper changeMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final OnlineUserRegistry onlineUserRegistry;
    private final IdCardGuard idCardGuard;

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public PageResult<EmployeeVO> page(EmployeePageQuery query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        LambdaQueryWrapper<User> wrapper = buildWrapper(query);
        Page<User> page = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(toEmployeeVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public void export(HttpServletResponse response, EmployeePageQuery query) {
        LambdaQueryWrapper<User> wrapper = buildWrapper(query);
        // 导出上限：超过 5000 行请按部门分批，避免一次性把全表读进内存
        wrapper.last("LIMIT 5000");
        List<EmployeeVO> rows = toEmployeeVOs(userMapper.selectList(wrapper));
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (EmployeeVO vo : rows) {
            data.add(List.of(
                    nvl(vo.getWorkNumber()), nvl(vo.getRealName()),
                    vo.getGender() != null && vo.getGender() == 2 ? "女" : "男",
                    nvl(vo.getDeptName()), nvl(vo.getPositionName() != null ? vo.getPositionName() : vo.getPosition()),
                    nvl(vo.getEmploymentStatusLabel()),
                    vo.getEntryDate() == null ? "" : vo.getEntryDate().toString(),
                    nvl(vo.getSeniority()), nvl(vo.getMobile()), nvl(vo.getEmail()),
                    nvl(vo.getDirectLeaderName())));
        }
        ExcelExportUtil.write(response, "员工花名册.xlsx", "花名册", EXPORT_HEADERS, data);
    }

    /**
     * 组装花名册查询条件。
     *
     * <p>未指定任职状态时**默认只查在职**（2/3/4）：花名册的默认视图是在职员工，
     * 离职员工需要显式筛选，否则人数统计会被历史数据撑大。</p>
     */
    private LambdaQueryWrapper<User> buildWrapper(EmployeePageQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        hrDataScopeSupport.applyToUser(wrapper);

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getRealName, kw)
                    .or().like(User::getWorkNumber, kw)
                    .or().like(User::getMobile, kw));
        }
        if (query.getDeptId() != null) {
            wrapper.in(User::getDeptId, hrDataScopeSupport.subtreeIds(query.getDeptId()));
        }
        if (query.getPositionId() != null) {
            wrapper.eq(User::getPositionId, query.getPositionId());
        }
        if (query.getEmploymentStatus() != null) {
            wrapper.eq(User::getEmploymentStatus, query.getEmploymentStatus());
        } else {
            wrapper.in(User::getEmploymentStatus, ON_DUTY);
        }
        if (query.getEntryDateStart() != null) {
            wrapper.ge(User::getEntryDate, query.getEntryDateStart());
        }
        if (query.getEntryDateEnd() != null) {
            wrapper.le(User::getEntryDate, query.getEntryDateEnd());
        }
        wrapper.orderByAsc(User::getDeptId).orderByDesc(User::getId);
        return wrapper;
    }

    @Transactional(readOnly = true)
    @Override
    public EmployeeDetailVO detail(Long userId) {
        User user = requireUser(userId);
        assertCanView(user);

        boolean sensitive = canViewSensitive();
        EmployeeDetailVO vo = new EmployeeDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setMobile(sensitive ? user.getMobile() : SensitiveMask.mobile(user.getMobile()));
        vo.setEmail(user.getEmail());
        vo.setCompanyId(user.getCompanyId());
        vo.setDeptId(user.getDeptId());
        vo.setPositionId(user.getPositionId());
        vo.setPosition(user.getPosition());
        vo.setWorkNumber(user.getWorkNumber());
        vo.setEntryDate(user.getEntryDate());
        vo.setSeniority(seniority(user.getEntryDate()));
        vo.setEmploymentStatus(user.getEmploymentStatus());
        vo.setEmploymentStatusLabel(labelOf(user.getEmploymentStatus()));
        vo.setDirectLeaderId(user.getDirectLeaderId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setIdCard(sensitive ? user.getIdCard() : SensitiveMask.idCard(user.getIdCard()));
        vo.setBirthDate(user.getBirthDate());
        vo.setMaritalStatus(user.getMaritalStatus());
        vo.setNativePlace(user.getNativePlace());
        vo.setAddress(user.getAddress());
        vo.setEmergencyName(user.getEmergencyName());
        vo.setEmergencyPhone(sensitive ? user.getEmergencyPhone() : SensitiveMask.mobile(user.getEmergencyPhone()));
        vo.setWorkStartDate(user.getWorkStartDate());
        vo.setProbationEndDate(user.getProbationEndDate());
        vo.setCanViewSensitive(sensitive);

        // 组织名称批量补全（3 次查询，不做逐项关联）
        if (user.getDeptId() != null) {
            Department dept = departmentMapper.selectById(user.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getDeptName());
        }
        if (user.getCompanyId() != null) {
            // 公司名称由前端字典/组织接口补全，此处不额外查库（避免为一行数据多打一次 SQL）
            vo.setCompanyId(user.getCompanyId());
        }
        if (user.getPositionId() != null) {
            Position position = positionMapper.selectById(user.getPositionId());
            vo.setPositionName(position == null ? null : position.getPositionName());
        }
        if (user.getDirectLeaderId() != null) {
            User leader = userMapper.selectById(user.getDirectLeaderId());
            vo.setDirectLeaderName(leader == null ? null : leader.getRealName());
        }

        EmployeeBank bank = bankMapper.selectOne(new LambdaQueryWrapper<EmployeeBank>()
                .eq(EmployeeBank::getUserId, userId).last("LIMIT 1"));
        if (bank != null) {
            vo.setBankName(bank.getBankName());
            vo.setBankAccount(sensitive ? bank.getBankAccount() : SensitiveMask.bankAccount(bank.getBankAccount()));
            vo.setSocialNo(sensitive ? bank.getSocialNo() : SensitiveMask.bankAccount(bank.getSocialNo()));
            vo.setFundNo(sensitive ? bank.getFundNo() : SensitiveMask.bankAccount(bank.getFundNo()));
        }

        vo.setContracts(listContracts(userId));
        vo.setCerts(listCerts(userId));
        vo.setChanges(listChanges(userId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, EmployeeProfileRequest request) {
        User user = requireUser(userId);
        assertCanEdit(user);
        LocalDate today = LocalDate.now();

        User patch = new User();
        patch.setId(userId);
        // 清空身份证时需要单独把盲索引置 NULL（见下方身份证处理段）
        boolean idCardHashToClear = false;

        // ---------- 任职状态：走状态机 + 联动账号 ----------
        if (request.getEmploymentStatus() != null
                && !Objects.equals(request.getEmploymentStatus(), user.getEmploymentStatus())) {
            EmploymentStatusEnum.assertTransition(user.getEmploymentStatus(), request.getEmploymentStatus());
            if (!StringUtils.hasText(request.getChangeRemark())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "变更任职状态必须填写说明");
            }
            patch.setEmploymentStatus(request.getEmploymentStatus());
            EmploymentStatusEnum target = EmploymentStatusEnum.of(request.getEmploymentStatus());
            if (target.forbidsLogin()) {
                // 离职/退休/待入职：同一事务内禁用账号，避免「已离职还能登录」
                patch.setStatus(0);
            }
            writeChange(userId, changeTypeOf(target), today,
                    "employmentStatus=" + user.getEmploymentStatus(),
                    "employmentStatus=" + request.getEmploymentStatus(),
                    request.getChangeRemark());
        }

        // ---------- 岗位：同步冗余显示名 ----------
        if (request.getPositionId() != null) {
            Position position = positionMapper.selectById(request.getPositionId());
            if (position == null) {
                throw new BizException(ErrorCode.HR_POSITION_NOT_FOUND);
            }
            patch.setPositionId(request.getPositionId());
            patch.setPosition(position.getPositionName());
        }
        if (request.getDirectLeaderId() != null) {
            if (request.getDirectLeaderId().equals(userId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "直属上级不能是本人");
            }
            if (userMapper.selectById(request.getDirectLeaderId()) == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND);
            }
            patch.setDirectLeaderId(request.getDirectLeaderId());
        }
        if (request.getWorkStartDate() != null) {
            patch.setWorkStartDate(request.getWorkStartDate());
        }
        if (request.getProbationEndDate() != null) {
            patch.setProbationEndDate(request.getProbationEndDate());
        }
        if (request.getBirthDate() != null) {
            patch.setBirthDate(request.getBirthDate());
        }
        if (request.getMaritalStatus() != null) {
            patch.setMaritalStatus(request.getMaritalStatus());
        }
        if (request.getNativePlace() != null) {
            patch.setNativePlace(request.getNativePlace());
        }
        if (request.getAddress() != null) {
            patch.setAddress(request.getAddress());
        }
        if (request.getEmergencyName() != null) {
            patch.setEmergencyName(request.getEmergencyName());
        }
        if (request.getEmergencyPhone() != null) {
            patch.setEmergencyPhone(request.getEmergencyPhone());
        }
        if (request.getMobile() != null) {
            patch.setMobile(request.getMobile());
        }
        if (request.getEmail() != null) {
            patch.setEmail(request.getEmail());
        }
        if (request.getIdCard() != null) {
            // 「值没变 + 原值本身不合法」时完全跳过：既不校验也不重写，也不动盲索引。
            //
            // 为什么单独放行这一种情况：存量档案里可能有校验码不对的历史身份证
            // （老系统直接导入、纸质件录错一位）。若每次保存都强校验，这些人的档案会因为
            // 「身份证格式错误」而彻底改不了——连手机号都改不了，而他们恰恰最需要被修正。
            // 改其它字段时不动身份证即可正常保存；真要更正，填一个合法号码进来即可通过。
            //
            // 反过来，「值没变但合法」仍然照常写入：加密用随机 IV，每次写都会产生新密文，
            // 这正是「重复保存不会套娃加密」的验证点（verify_crypto 会检查密文变化且可解）。
            boolean sameValue = Objects.equals(
                    IdCardUtil.normalize(request.getIdCard()), IdCardUtil.normalize(user.getIdCard()));
            boolean legacyInvalid = sameValue && !IdCardUtil.isValid(request.getIdCard());
            if (!legacyInvalid) {
                // 改身份证必须同时重新算盲索引，否则查重会继续命中旧号（甚至永远查不到新人）。
                // 查重放在这里而不是只放前端：前端只能拦「同一页面里已知的」，
                // 跨页面/直接调接口拦不住。
                idCardGuard.assertNotDuplicated(request.getIdCard(), userId);
                patch.setIdCard(request.getIdCard());
                patch.setIdCardHash(idCardGuard.hashOfNullable(request.getIdCard()));
                if (!StringUtils.hasText(request.getIdCard())) {
                    // 清空身份证时哈希也要清。updateById 的空值策略会跳过 null 字段，
                    // 只靠实体更新会留下一个指向旧号码的哈希——那个人从此「身份证已被占用」，
                    // 而且占用者是空档案，谁也查不出来。
                    idCardHashToClear = true;
                }
                if (request.getBirthDate() == null) {
                    // 生日与性别本来就是身份证里的信息，填了身份证还让人再填一遍属重复劳动，
                    // 且两处不一致时无法判断哪个对。这里只在未显式填写时按身份证补算。
                    patch.setBirthDate(IdCardUtil.birthDateOf(request.getIdCard()));
                }
            }
        }
        userMapper.updateById(patch);
        if (idCardHashToClear) {
            // 单独一条 UPDATE：updateById 的 NOT_NULL 策略写不进 null。
            // 这里只动 id_card_hash 一列，不涉及加密字段，因此用 LambdaUpdateWrapper 是安全的
            //（加密字段走 wrapper 会绕过 TypeHandler，把明文写进库里）。
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getIdCardHash, null));
        }

        // 禁用账号后必须摘掉在线会话，否则令牌在有效期内仍可继续访问
        if (patch.getStatus() != null && patch.getStatus() == 0) {
            onlineUserRegistry.unregister(userId);
        }

        // ---------- 银行与社保：一人一条，存在则更新 ----------
        boolean bankTouched = request.getBankName() != null || request.getBankAccount() != null
                || request.getSocialNo() != null || request.getFundNo() != null;
        if (bankTouched) {
            EmployeeBank bank = bankMapper.selectOne(new LambdaQueryWrapper<EmployeeBank>()
                    .eq(EmployeeBank::getUserId, userId).last("LIMIT 1"));
            if (bank == null) {
                bank = new EmployeeBank();
                bank.setUserId(userId);
                bank.setBankName(request.getBankName());
                bank.setBankAccount(request.getBankAccount());
                bank.setSocialNo(request.getSocialNo());
                bank.setFundNo(request.getFundNo());
                bankMapper.insert(bank);
            } else {
                EmployeeBank update = new EmployeeBank();
                update.setId(bank.getId());
                update.setBankName(request.getBankName());
                update.setBankAccount(request.getBankAccount());
                update.setSocialNo(request.getSocialNo());
                update.setFundNo(request.getFundNo());
                bankMapper.updateById(update);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveContract(Long userId, ContractSaveRequest request) {
        requireUser(userId);
        assertCanEdit(requireUser(userId));
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "合同结束日期不能早于开始日期");
        }
        // 区间重叠校验：数据库唯一索引只能表达等值，区间交集只能在这里判
        List<EmployeeContract> existing = contractMapper.selectList(new LambdaQueryWrapper<EmployeeContract>()
                .eq(EmployeeContract::getUserId, userId));
        for (EmployeeContract old : existing) {
            if (request.getId() != null && request.getId().equals(old.getId())) {
                continue;
            }
            LocalDate oldStart = old.getStartDate();
            LocalDate oldEnd = old.getEndDate() == null ? LocalDate.of(9999, 12, 31) : old.getEndDate();
            LocalDate newEnd = request.getEndDate() == null ? LocalDate.of(9999, 12, 31) : request.getEndDate();
            boolean overlap = !request.getStartDate().isAfter(oldEnd) && !oldStart.isAfter(newEnd);
            if (overlap) {
                throw new BizException(ErrorCode.HR_CONTRACT_DATE_OVERLAP,
                        "与已有合同「" + old.getContractNo() + "」时间重叠");
            }
        }

        EmployeeContract entity;
        if (request.getId() == null) {
            entity = new EmployeeContract();
            entity.setUserId(userId);
            entity.setSignCount(Boolean.TRUE.equals(request.getRenew())
                    ? existing.stream().map(EmployeeContract::getSignCount)
                            .filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1
                    : 1);
        } else {
            entity = contractMapper.selectById(request.getId());
            if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "合同不存在或不属于该员工");
            }
        }
        entity.setContractNo(request.getContractNo());
        entity.setContractType(request.getContractType() == null ? 1 : request.getContractType());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setSignDate(request.getSignDate());
        entity.setFileIds(request.getFileIds());
        entity.setRemark(request.getRemark());
        entity.setStatus(1);
        if (request.getId() == null) {
            contractMapper.insert(entity);
        } else {
            contractMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void deleteContract(Long userId, Long contractId) {
        EmployeeContract entity = contractMapper.selectById(contractId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "合同不存在或不属于该员工");
        }
        contractMapper.deleteById(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCert(Long userId, CertSaveRequest request) {
        requireUser(userId);
        if (request.getExpireDate() != null && request.getIssueDate() != null
                && request.getExpireDate().isBefore(request.getIssueDate())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "证照有效期不能早于发证日期");
        }
        EmployeeCert entity;
        if (request.getId() == null) {
            entity = new EmployeeCert();
            entity.setUserId(userId);
        } else {
            entity = certMapper.selectById(request.getId());
            if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "证照不存在或不属于该员工");
            }
        }
        entity.setCertType(request.getCertType());
        entity.setCertName(request.getCertName());
        entity.setCertNo(request.getCertNo());
        entity.setIssueOrg(request.getIssueOrg());
        entity.setIssueDate(request.getIssueDate());
        entity.setExpireDate(request.getExpireDate());
        entity.setFileIds(request.getFileIds());
        entity.setRemark(request.getRemark());
        if (request.getId() == null) {
            certMapper.insert(entity);
        } else {
            certMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void deleteCert(Long userId, Long certId) {
        EmployeeCert entity = certMapper.selectById(certId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "证照不存在或不属于该员工");
        }
        certMapper.deleteById(certId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ChangeVO> listChanges(Long userId) {
        assertCanView(requireUser(userId));
        List<EmployeeChange> list = changeMapper.selectList(new LambdaQueryWrapper<EmployeeChange>()
                .eq(EmployeeChange::getUserId, userId)
                .orderByDesc(EmployeeChange::getEffectiveDate)
                .orderByDesc(EmployeeChange::getId));
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> operatorIds = list.stream().map(EmployeeChange::getOperatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = operatorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(operatorIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        List<ChangeVO> vos = new ArrayList<>(list.size());
        for (EmployeeChange c : list) {
            ChangeVO vo = new ChangeVO();
            vo.setId(c.getId());
            vo.setChangeType(c.getChangeType());
            vo.setChangeTypeLabel(changeTypeLabel(c.getChangeType()));
            vo.setEffectiveDate(c.getEffectiveDate());
            vo.setBeforeJson(c.getBeforeJson());
            vo.setAfterJson(c.getAfterJson());
            vo.setInstanceId(c.getInstanceId());
            vo.setRemark(c.getRemark());
            vo.setOperatorName(names.get(c.getOperatorId()));
            vo.setCreateTime(c.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private List<ContractVO> listContracts(Long userId) {
        List<EmployeeContract> list = contractMapper.selectList(new LambdaQueryWrapper<EmployeeContract>()
                .eq(EmployeeContract::getUserId, userId)
                .orderByDesc(EmployeeContract::getStartDate));
        List<ContractVO> vos = new ArrayList<>(list.size());
        for (EmployeeContract c : list) {
            ContractVO vo = new ContractVO();
            vo.setId(c.getId());
            vo.setContractNo(c.getContractNo());
            vo.setContractType(c.getContractType());
            vo.setContractTypeLabel(contractTypeLabel(c.getContractType()));
            vo.setStartDate(c.getStartDate());
            vo.setEndDate(c.getEndDate());
            vo.setSignCount(c.getSignCount());
            vo.setSignDate(c.getSignDate());
            vo.setFileIds(c.getFileIds());
            vo.setStatus(c.getStatus());
            vo.setDaysToExpire(c.getEndDate() == null ? null
                    : ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate()));
            vos.add(vo);
        }
        return vos;
    }

    private List<CertVO> listCerts(Long userId) {
        List<EmployeeCert> list = certMapper.selectList(new LambdaQueryWrapper<EmployeeCert>()
                .eq(EmployeeCert::getUserId, userId)
                .orderByAsc(EmployeeCert::getCertType)
                .orderByDesc(EmployeeCert::getId));
        List<CertVO> vos = new ArrayList<>(list.size());
        for (EmployeeCert c : list) {
            CertVO vo = new CertVO();
            vo.setId(c.getId());
            vo.setCertType(c.getCertType());
            vo.setCertTypeLabel(certTypeLabel(c.getCertType()));
            vo.setCertName(c.getCertName());
            vo.setCertNo(c.getCertNo());
            vo.setIssueOrg(c.getIssueOrg());
            vo.setIssueDate(c.getIssueDate());
            vo.setExpireDate(c.getExpireDate());
            vo.setFileIds(c.getFileIds());
            vo.setDaysToExpire(c.getExpireDate() == null ? null
                    : ChronoUnit.DAYS.between(LocalDate.now(), c.getExpireDate()));
            vos.add(vo);
        }
        return vos;
    }

    /** 转花名册行：部门、岗位、上级名称均批量补全 */
    private List<EmployeeVO> toEmployeeVOs(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = users.stream().map(User::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Collections.emptyMap()
                : departmentMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        Set<Long> positionIds = users.stream().map(User::getPositionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> positionNames = positionIds.isEmpty() ? Collections.emptyMap()
                : positionMapper.selectBatchIds(positionIds).stream()
                        .collect(Collectors.toMap(Position::getId, Position::getPositionName, (a, b) -> a));

        Set<Long> leaderIds = users.stream().map(User::getDirectLeaderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = new HashMap<>();
        if (!leaderIds.isEmpty()) {
            userMapper.selectBatchIds(leaderIds)
                    .forEach(u -> leaderNames.put(u.getId(), u.getRealName()));
        }

        List<EmployeeVO> vos = new ArrayList<>(users.size());
        for (User u : users) {
            EmployeeVO vo = new EmployeeVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setGender(u.getGender());
            vo.setMobile(SensitiveMask.mobile(u.getMobile()));
            vo.setEmail(u.getEmail());
            vo.setCompanyId(u.getCompanyId());
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(deptNames.get(u.getDeptId()));
            vo.setPositionId(u.getPositionId());
            vo.setPositionName(positionNames.get(u.getPositionId()));
            vo.setPosition(u.getPosition());
            vo.setWorkNumber(u.getWorkNumber());
            vo.setEntryDate(u.getEntryDate());
            vo.setSeniority(seniority(u.getEntryDate()));
            vo.setEmploymentStatus(u.getEmploymentStatus());
            vo.setEmploymentStatusLabel(labelOf(u.getEmploymentStatus()));
            vo.setDirectLeaderName(leaderNames.get(u.getDirectLeaderId()));
            vo.setStatus(u.getStatus());
            vo.setCreateTime(u.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 查看权限：本人 / 直属上级 / HR / 超管。
     *
     * <p>不满足时抛 403（响应 code=403，HTTP 200，符合项目约定），
     * 且不透露该员工是否存在以外的任何信息。</p>
     */
    private void assertCanView(User target) {
        LoginUser current = SecurityContextUtil.getCurrentUser();
        if (HrRoles.isHr(current.getRoles())) {
            return;
        }
        if (Objects.equals(current.getUserId(), target.getId())) {
            return;
        }
        if (Objects.equals(current.getUserId(), target.getDirectLeaderId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工档案");
    }

    private void assertCanEdit(User target) {
        LoginUser current = SecurityContextUtil.getCurrentUser();
        if (HrRoles.isHr(current.getRoles())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "仅人事或超级管理员可以维护员工档案");
    }

    private boolean canViewSensitive() {
        return HrRoles.isHr();
    }

    private void writeChange(Long userId, int changeType, LocalDate effectiveDate,
                             String before, String after, String remark) {
        EmployeeChange change = new EmployeeChange();
        change.setUserId(userId);
        change.setChangeType(changeType);
        change.setEffectiveDate(effectiveDate);
        change.setBeforeJson(before);
        change.setAfterJson(after);
        change.setRemark(remark);
        change.setOperatorId(SecurityContextUtil.getCurrentUserId());
        changeMapper.insert(change);
    }

    private static int changeTypeOf(EmploymentStatusEnum target) {
        return switch (target) {
            case PENDING_ONBOARD -> 1;
            case PROBATION, REGULAR -> 2;
            case PENDING_LEAVE -> 3;
            case LEFT -> 5;
            case RETIRED -> 6;
        };
    }

    private static String changeTypeLabel(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "入职";
            case 2 -> "转正";
            case 3 -> "调岗";
            case 4 -> "调薪";
            case 5 -> "离职";
            case 6 -> "退休";
            default -> "其他";
        };
    }

    private static String contractTypeLabel(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "固定期限";
            case 2 -> "无固定期限";
            case 3 -> "实习";
            case 4 -> "劳务";
            case 5 -> "返聘";
            default -> "其他";
        };
    }

    private static String certTypeLabel(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "学历";
            case 2 -> "学位";
            case 3 -> "职业资格";
            case 4 -> "职称";
            case 5 -> "特种作业";
            case 6 -> "培训证书";
            default -> "其他";
        };
    }

    private static String labelOf(Integer status) {
        if (status == null) {
            return null;
        }
        try {
            return EmploymentStatusEnum.of(status).getLabel();
        } catch (BizException e) {
            return "未知";
        }
    }

    /** 司龄：按 365.25 天折算，保留 1 位小数 */
    private static String seniority(LocalDate entryDate) {
        if (entryDate == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(entryDate, LocalDate.now());
        if (days < 0) {
            return "0.0 年";
        }
        return String.format(Locale.ROOT, "%.1f 年", days / 365.25);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
