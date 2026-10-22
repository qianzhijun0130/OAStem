package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.dto.UserPageQuery;
import com.kellen.oa_system.system.dto.UserSaveRequest;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.Menu;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.RoleMenu;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.enums.GenderEnum;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.MenuMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.RoleMenuMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import com.kellen.oa_system.system.service.UserService;
import com.kellen.oa_system.system.service.WorkNumberService;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import com.kellen.oa_system.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户领域服务实现。
 *
 * <p>权限查询链路：user → oa_user_role → oa_role（角色编码）
 * 以及 user → oa_user_role → oa_role_menu → oa_menu（权限码）。
 * 全部按 id 批量查询，避免在循环里逐条查库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 新增员工 / 重置密码时统一使用的初始密码 */
    public static final String DEFAULT_PASSWORD = "qwe@123456";

    /**
     * 新建员工未指定角色时自动绑定的默认角色编码。
     *
     * <p>「未分配角色」在系统里的实际后果是：能登录，但侧边栏空白、任何接口都 403 ——
     * 这不是「权限收敛」，而是「不可用」。新建员工默认给最基础的「普通员工」，
     * 更高权限由管理员在「编辑员工」里按需追加。</p>
     */
    private static final String DEFAULT_ROLE_CODE = "EMPLOYEE";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final DataScopeSupport dataScopeSupport;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final WorkNumberService workNumberService;

    @Override
    public LoginUser loadLoginUser(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getIsDeleted, 0));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<String> roles = getRoleCodesByUserId(user.getId());
        List<String> permissions = getMenuCodesByUserId(user.getId());
        return new LoginUser(user.getId(), user.getUsername(), user.getPassword(),
                user.getStatus(), roles, permissions);
    }

    @Override
    public List<String> getRoleCodesByUserId(Long userId) {
        Set<Long> roleIds = queryRoleIds(userId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptyList();
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (CollectionUtils.isEmpty(roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                .map(Role::getRoleCode)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getMenuCodesByUserId(Long userId) {
        Set<Long> roleIds = queryRoleIds(userId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptyList();
        }
        // 仅取启用角色（status=1）的权限码：被停用的角色不应再决定用户按钮权限
        Set<Long> enabledRoleIds = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>().in(Role::getId, roleIds).eq(Role::getStatus, 1))
                .stream().map(Role::getId).collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(enabledRoleIds)) {
            return Collections.emptyList();
        }
        List<RoleMenu> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, enabledRoleIds));
        if (CollectionUtils.isEmpty(roleMenus)) {
            return Collections.emptyList();
        }
        Set<Long> menuIds = roleMenus.stream().map(RoleMenu::getMenuId).collect(Collectors.toSet());
        List<Menu> menus = menuMapper.selectBatchIds(menuIds);
        if (CollectionUtils.isEmpty(menus)) {
            return Collections.emptyList();
        }
        return menus.stream()
                .filter(menu -> menu.getStatus() != null && menu.getStatus() == 1)
                .map(Menu::getMenuCode)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLoginInfo(Long userId, String ip) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userMapper.updateById(user);
    }

    @Override
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    @DataScope
    public PageResult<UserVO> pageUsers(UserPageQuery query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 数据权限：按当前用户的角色数据范围收敛可见用户（超管/全部数据不受限）
        dataScopeSupport.applyTo(wrapper, User::getDeptId, User::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getRealName, kw)
                    .or().like(User::getWorkNumber, kw));
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (query.getDeptId() != null) {
            wrapper.in(User::getDeptId, resolveDeptSubtree(query.getDeptId()));
        }
        if (query.getRoleId() != null) {
            List<Long> userIds = queryUserIdsByRole(query.getRoleId());
            if (userIds.isEmpty()) {
                return PageResult.empty((long) pageNo, (long) pageSize);
            }
            wrapper.in(User::getId, userIds);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<UserVO> vos = toUserVOs(page.getRecords());
        // 使用 MP 修正后的页码/页大小：maxLimit 已把过大 pageSize 截断到上限，
        // overflow 已把越界 pageNo 回落到第 1 页，回显必须与查询实际一致。
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<UserVO> listCandidates() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 只返回启用态用户，作为审批人 / 接收人候选。
        // 不套 @DataScope：原 getUserPageApi 受 system:user:* 限制会让非 HR 角色在
        // 转办 / 指定接收人时拿不到候选列表（403）。候选选择由页面级权限管控即可。
        wrapper.eq(User::getStatus, 1);
        wrapper.orderByDesc(User::getId);
        List<User> users = userMapper.selectList(wrapper);
        return toUserVOs(users);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveUser(UserSaveRequest request) {
        return request.getId() == null ? createUser(request) : updateUser(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        if (Objects.equals(id, SecurityContextUtil.getCurrentUserId())) {
            throw new BizException(ErrorCode.USER_CANNOT_OPERATE_SELF);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 唯一性已由数据库保证：uk_username 改为 (username, active_flag)，
        // 存活行 active_flag=1、已删行 active_flag=NULL，原账号在删除瞬间自动释放，
        // 应用层无需再改写业务值（见 开发文档/数据库/逻辑删除唯一索引企业级改造方案.md）
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        log.info("删除用户: id={}, username={}", id, user.getUsername());
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        if (Objects.equals(id, SecurityContextUtil.getCurrentUserId())
                && status != null && status != 1) {
            throw new BizException(ErrorCode.USER_CANNOT_OPERATE_SELF);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        userMapper.updateById(update);
        log.info("重置密码: id={}, username={}", id, user.getUsername());
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    /**
     * 取默认角色（普通员工）的主键。
     *
     * <p>找不到时直接报错而不是静默创建「无角色」账号：无角色用户能登录但看不到任何菜单、
     * 调任何接口都 403，排查成本远高于一条明确的配置错误提示。</p>
     */
    private Long requireDefaultRoleId() {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, DEFAULT_ROLE_CODE));
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND,
                    "系统缺少默认角色「普通员工」（" + DEFAULT_ROLE_CODE + "），请先在角色管理中创建后再新增员工");
        }
        // 停用不拦：停用角色仍可绑定，只是其菜单权限不生效（见 getMenuCodesByUserId 的过滤），
        // 这里仅留痕，便于排查「新员工登录后什么都没有」。
        if (role.getStatus() != null && role.getStatus() != 1) {
            log.warn("默认角色 {} 当前为停用状态，新员工仍将绑定该角色: roleId={}", DEFAULT_ROLE_CODE, role.getId());
        }
        return role.getId();
    }

    private Long createUser(UserSaveRequest request) {
        assertUsernameUnique(request.getUsername(), null);
        String workNumber = resolveWorkNumber(request.getWorkNumber(), request.getDeptId());
        assertWorkNumberUnique(workNumber, null);
        String rawPwd = StringUtils.hasText(request.getPassword()) ? request.getPassword() : DEFAULT_PASSWORD;

        User user = new User();
        applyEditableFields(user, request);
        user.setGender(request.getGender() == null ? GenderEnum.defaultCode() : request.getGender());
        // 入职日期：未填写时取表单提交当日，避免出现空入职日期
        user.setEntryDate(request.getEntryDate() == null ? LocalDate.now() : request.getEntryDate());
        user.setWorkNumber(workNumber);
        // 新增：currentDeptId 传 null，即要求所选部门必须为启用状态
        user.setCompanyId(resolveCompanyId(request.getDeptId(), null));
        user.setPassword(passwordEncoder.encode(rawPwd));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        userMapper.insert(user);
        // 未指定角色 → 自动绑定「普通员工」。放在服务端而不是只靠前端预选：
        // 直接调接口（脚本、导入、第三方对接）同样要遵守这条规则，否则会造出无任何权限的账号。
        List<Long> roleIds = CollectionUtils.isEmpty(request.getRoleIds())
                ? List.of(requireDefaultRoleId())
                : request.getRoleIds();
        bindRoles(user.getId(), roleIds);
        log.info("新增用户: id={}, username={}, workNumber={}, roles={}",
                user.getId(), user.getUsername(), workNumber, roleIds);
        return user.getId();
    }

    private Long updateUser(UserSaveRequest request) {
        if (request.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        User existing = userMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        assertUsernameUnique(request.getUsername(), request.getId());
        String workNumber = StringUtils.hasText(request.getWorkNumber())
                ? request.getWorkNumber().trim()
                : existing.getWorkNumber();
        assertWorkNumberUnique(workNumber, request.getId());

        User user = new User();
        user.setId(request.getId());
        user.setUsername(request.getUsername().trim());
        user.setRealName(request.getRealName().trim());
        user.setNickname(request.getNickname());
        user.setGender(request.getGender() == null ? existing.getGender() : request.getGender());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        // 编辑：部门未变更时放行（原部门可能已被停用），变更到新部门时才要求其为启用状态
        user.setCompanyId(resolveCompanyId(request.getDeptId(), existing.getDeptId()));
        user.setDeptId(request.getDeptId());
        user.setPosition(request.getPosition());
        user.setWorkNumber(workNumber);
        user.setEntryDate(request.getEntryDate() == null ? existing.getEntryDate() : request.getEntryDate());
        user.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        // 仅当填写了新密码时才覆盖
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.updateById(user);

        // 重新绑定角色（先删后插）。留空表示「本次不动角色」而不是「清空」：
        // 账号没有角色 = 能登录但侧边栏空白、接口全 403，属于不可用状态，
        // 不该因为一次没带 roleIds 的局部保存而产生。要换角色就显式传（至少一个）。
        if (CollectionUtils.isEmpty(request.getRoleIds())) {
            log.info("修改用户未携带角色，保留原有绑定: id={}", request.getId());
        } else {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, request.getId()));
            bindRoles(request.getId(), request.getRoleIds());
        }
        log.info("修改用户: id={}", request.getId());
        return request.getId();
    }

    private void applyEditableFields(User user, UserSaveRequest request) {
        user.setUsername(request.getUsername().trim());
        user.setRealName(request.getRealName().trim());
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setDeptId(request.getDeptId());
        user.setPosition(request.getPosition());
        user.setWorkNumber(request.getWorkNumber());
        user.setEntryDate(request.getEntryDate());
    }

    /**
     * 由所选部门反查所属公司，并校验该部门当前可用于分配。
     *
     * <p>员工归属的公司由其部门决定（{@code oa_department.company_id}），
     * 请求 DTO 刻意不暴露 {@code companyId}，避免前端传入与部门不一致的公司导致数据错乱。</p>
     *
     * <p><b>停用部门规则</b>：新增时必须选择启用状态的部门；编辑时若部门未发生变更则放行——
     * 否则会出现「部门一旦停用，其下已有用户就再也无法保存」的死结。</p>
     *
     * @param deptId        本次提交的部门 ID
     * @param currentDeptId 编辑场景下该用户当前的部门 ID；新增时传 {@code null}
     * @return 该部门所属的公司 ID
     * @throws BizException 部门不存在、未关联公司，或已停用且本次确实变更了部门
     */
    private Long resolveCompanyId(Long deptId, Long currentDeptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        if (dept.getCompanyId() == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND, "该部门未关联公司，请先在部门管理中维护");
        }
        boolean deptChanged = currentDeptId == null || !Objects.equals(deptId, currentDeptId);
        if (deptChanged && !Objects.equals(dept.getStatus(), 1)) {
            throw new BizException(ErrorCode.DEPT_DISABLED);
        }
        return dept.getCompanyId();
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        for (Long roleId : roleIds.stream().distinct().collect(Collectors.toList())) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    /**
     * 解析本次新增要使用的工号。
     *
     * <p>管理员手工填写时以其填写值优先（仅做唯一性校验）；留空则按部门自动分配，
     * 规则见 {@link WorkNumberService}。</p>
     */
    private String resolveWorkNumber(String requestWorkNumber, Long deptId) {
        if (StringUtils.hasText(requestWorkNumber)) {
            return requestWorkNumber.trim();
        }
        return workNumberService.generate(deptId);
    }

    private void assertUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username.trim())
                .eq(User::getIsDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.USERNAME_DUPLICATE);
        }
    }

    /**
     * 工号唯一性校验。
     *
     * <p>刻意包含<b>已逻辑删除</b>的用户：业务约定「员工离职、删除后工号不回收、不复用」，
     * 若沿用带 {@code @TableLogic} 的查询，已删除用户的工号会被放行、造成复用。</p>
     */
    private void assertWorkNumberUnique(String workNumber, Long excludeId) {
        if (!StringUtils.hasText(workNumber)) {
            return;
        }
        Long count = userMapper.countByWorkNumberIncludingDeleted(workNumber.trim(), excludeId);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.WORK_NUMBER_DUPLICATE);
        }
    }

    /** 部门过滤：把目标部门及其全部下级部门 ID 一并返回（内存遍历，部门量级很小） */
    private List<Long> resolveDeptSubtree(Long rootId) {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getId));
        if (CollectionUtils.isEmpty(all)) {
            return List.of(rootId);
        }
        Map<Long, Department> byId = all.stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (a, b) -> a, HashMap::new));

        List<Long> result = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        Set<Long> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (!visited.add(cur)) {
                continue;
            }
            result.add(cur);
            for (Department dept : all) {
                if (Objects.equals(dept.getParentId(), cur)) {
                    stack.push(dept.getId());
                }
            }
        }
        return result;
    }

    private List<Long> queryUserIdsByRole(Long roleId) {
        List<UserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, roleId));
        if (CollectionUtils.isEmpty(links)) {
            return List.of();
        }
        return links.stream().map(UserRole::getUserId).collect(Collectors.toList());
    }

    /** 把用户实体列表转换成带部门名、角色名的视图对象 */
    private List<UserVO> toUserVOs(List<User> users) {
        if (CollectionUtils.isEmpty(users)) {
            return List.of();
        }
        Set<Long> deptIds = users.stream().map(User::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = Map.of();
        if (!deptIds.isEmpty()) {
            List<Department> depts = departmentMapper.selectBatchIds(deptIds);
            if (!CollectionUtils.isEmpty(depts)) {
                deptNameMap = depts.stream()
                        .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
            }
        }

        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> userRoleMap = Map.of();
        Map<Long, String> roleNameMap = Map.of();
        if (!userIds.isEmpty()) {
            List<UserRole> links = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
            if (!CollectionUtils.isEmpty(links)) {
                Set<Long> roleIds = links.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
                List<Role> roles = roleMapper.selectBatchIds(roleIds);
                if (!CollectionUtils.isEmpty(roles)) {
                    roleNameMap = roles.stream()
                            .collect(Collectors.toMap(Role::getId, Role::getRoleName, (a, b) -> a));
                }
                userRoleMap = links.stream().collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId, Collectors.toList())));
            }
        }

        List<UserVO> vos = new ArrayList<>(users.size());
        for (User u : users) {
            List<Long> rids = userRoleMap.getOrDefault(u.getId(), List.of());
            String rnames = rids.stream().map(roleNameMap::get)
                    .filter(Objects::nonNull).collect(Collectors.joining("、"));
            vos.add(UserVO.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .realName(u.getRealName())
                    .nickname(u.getNickname())
                    .gender(u.getGender())
                    .mobile(u.getMobile())
                    .email(u.getEmail())
                    .companyId(u.getCompanyId())
                    .deptId(u.getDeptId())
                    .deptName(deptNameMap.get(u.getDeptId()))
                    .position(u.getPosition())
                    .workNumber(u.getWorkNumber())
                    .entryDate(u.getEntryDate())
                    .roleIds(rids)
                    .roleNames(rnames)
                    .status(u.getStatus())
                    .createTime(u.getCreateTime())
                    .build());
        }
        return vos;
    }

    private Set<Long> queryRoleIds(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (CollectionUtils.isEmpty(userRoles)) {
            return Collections.emptySet();
        }
        return new HashSet<>(userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList()));
    }
}
