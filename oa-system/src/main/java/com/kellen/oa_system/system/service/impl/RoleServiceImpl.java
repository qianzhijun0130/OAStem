package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.RolePageQuery;
import com.kellen.oa_system.system.dto.RoleSaveRequest;
import com.kellen.oa_system.system.entity.Menu;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.RoleMenu;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.MenuMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.RoleMenuMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import com.kellen.oa_system.system.service.RoleService;
import com.kellen.oa_system.system.vo.RoleOptionVO;
import com.kellen.oa_system.system.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色业务实现。
 *
 * <p>用户数通过 oa_user_role 按角色分组计数，分页场景下数据量极小，直接内存聚合。
 * 超级管理员（role_code = SUPER_ADMIN）的权限由系统保证始终为全量，禁止通过接口缩减。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    /** 超级管理员角色编码，其权限不可被接口修改 */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;

    @Override
    public PageResult<RoleVO> pageRoles(RolePageQuery query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getRoleName())) {
            wrapper.like(Role::getRoleName, query.getRoleName().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Role::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(Role::getSort).orderByAsc(Role::getId);

        Page<Role> page = roleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        if (CollectionUtils.isEmpty(page.getRecords())) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }

        List<Long> roleIds = page.getRecords().stream().map(Role::getId).collect(Collectors.toList());
        Map<Long, Long> userCountMap = countUsersByRoleIds(roleIds);

        List<RoleVO> vos = page.getRecords().stream().map(role -> RoleVO.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .roleType(role.getRoleType())
                .dataScope(role.getDataScope())
                .description(role.getDescription())
                .status(role.getStatus())
                .sort(role.getSort())
                .createTime(role.getCreateTime())
                .userCount(userCountMap.getOrDefault(role.getId(), 0L))
                .build()).collect(Collectors.toList());

        return PageResult.of(vos, page.getTotal(), (long) pageNo, (long) pageSize);
    }

    @Override
    public List<RoleOptionVO> listOptions() {
        // 下拉选项仅返回启用角色（status=1）：停用角色不应再出现在用户表单等角色选择器中
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getStatus, 1).orderByAsc(Role::getSort));
        if (CollectionUtils.isEmpty(roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> RoleOptionVO.builder()
                        .id(role.getId())
                        .roleName(role.getRoleName())
                        .roleCode(role.getRoleCode())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveRole(RoleSaveRequest request) {
        assertRoleCodeUnique(request.getRoleCode(), request.getId());

        Role role = new Role();
        role.setRoleName(request.getRoleName().trim());
        role.setRoleCode(request.getRoleCode().trim());
        role.setRoleType(request.getRoleType() == null ? 1 : request.getRoleType());
        role.setDataScope(request.getDataScope() == null ? 1 : request.getDataScope());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        role.setSort(request.getSort() == null ? 0 : request.getSort());

        if (request.getId() == null) {
            roleMapper.insert(role);
            log.info("新增角色: id={}, name={}", role.getId(), role.getRoleName());
            return role.getId();
        }
        Role existing = roleMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        role.setId(request.getId());
        roleMapper.updateById(role);
        log.info("修改角色: id={}, name={}", role.getId(), role.getRoleName());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "超级管理员角色不可删除");
        }
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(ErrorCode.ROLE_IN_USE);
        }
        // 唯一性已由数据库保证：uk_role_code 改为 (role_code, active_flag)，
        // 已删行 active_flag=NULL，原编码自动释放，应用层无需改写业务值
        roleMapper.deleteById(id);
        // 级联清理角色菜单关联，避免脏数据
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
        log.info("删除角色: id={}, name={}", id, role.getRoleName());
    }

    @Override
    public void updateRoleStatus(Long id, Integer status) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        Role update = new Role();
        update.setId(id);
        update.setStatus(status);
        roleMapper.updateById(update);
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        List<RoleMenu> links = roleMenuMapper.selectList(
                new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        if (CollectionUtils.isEmpty(links)) {
            return List.of();
        }
        return links.stream().map(RoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "超级管理员权限不可修改");
        }
        // 写入前先校验菜单是否存在：引用不存在的 menuId 会写进 oa_role_menu 形成孤儿行，
        // 必须整体拒绝（fail-fast），保留角色原有菜单不被误清空。
        if (!CollectionUtils.isEmpty(menuIds)) {
            List<Long> normalized = menuIds.stream()
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (!normalized.isEmpty()) {
                Set<Long> existingIds = menuMapper.selectList(
                                new LambdaQueryWrapper<Menu>().in(Menu::getId, normalized).select(Menu::getId))
                        .stream().map(Menu::getId).collect(Collectors.toSet());
                List<Long> missing = normalized.stream()
                        .filter(id -> !existingIds.contains(id))
                        .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    throw new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在: " + missing);
                }
            }
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        if (CollectionUtils.isEmpty(menuIds)) {
            return;
        }
        List<Long> withAncestors = expandAncestors(menuIds);
        List<RoleMenu> toInsert = withAncestors.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(menuId -> {
                    RoleMenu rm = new RoleMenu();
                    rm.setRoleId(roleId);
                    rm.setMenuId(menuId);
                    return rm;
                })
                .collect(Collectors.toList());
        for (RoleMenu rm : toInsert) {
            roleMenuMapper.insert(rm);
        }
        log.info("分配角色菜单: roleId={}, menuCount={}", roleId, toInsert.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cloneMenusFrom(Long roleId, Long sourceRoleId) {
        if (Objects.equals(roleId, sourceRoleId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "源角色不能与目标角色相同");
        }
        Role target = roleMapper.selectById(roleId);
        if (target == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (SUPER_ADMIN_CODE.equals(target.getRoleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "超级管理员权限不可修改");
        }
        Role source = roleMapper.selectById(sourceRoleId);
        if (source == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND, "源角色不存在: " + sourceRoleId);
        }
        List<Long> sourceMenuIds = getRoleMenuIds(sourceRoleId);
        // 复用 assignMenus：其内部已包含「目标存在 / 非超管 / 菜单存在性校验 / 祖先补全 / 全量覆盖」，
        // 克隆与手工授权走同一条写入路径，避免两套逻辑行为不一致。
        assignMenus(roleId, sourceMenuIds);
        log.info("克隆角色菜单: target={}({}), source={}({}), menuCount={}",
                target.getRoleCode(), roleId, source.getRoleCode(), sourceRoleId, sourceMenuIds.size());
    }

    /**
     * 把勾选的菜单 id 补全为其「自身 + 全部上级目录」。
     *
     * <p>侧边栏是按菜单树渲染的：getMenuTreeByUserId 先按角色菜单 id 查出节点、
     * 再拼成树。若只存了子菜单而没存它的上级目录，该子菜单会成为孤儿节点、
     * 根本挂不到树上——表现为「权限给了，但侧边栏里看不到这个页面」。
     * 因此上级目录必须一并授权。</p>
     *
     * <p>这一步放在服务端而不是前端传全量：前端权限树用 check-strictly（父子不联动）
     * 以避免回显时父节点被级联成全选，父节点是否勾选不再由前端保证。</p>
     */
    private List<Long> expandAncestors(List<Long> menuIds) {
        List<Menu> all = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .select(Menu::getId, Menu::getParentId));
        if (CollectionUtils.isEmpty(all)) {
            return menuIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        }
        Map<Long, Long> parentOf = new HashMap<>();
        for (Menu menu : all) {
            parentOf.put(menu.getId(), menu.getParentId());
        }

        Set<Long> result = new HashSet<>();
        for (Long menuId : menuIds) {
            if (menuId == null) {
                continue;
            }
            Long current = menuId;
            // 根节点 parent_id 为 0；用 visited 兜底防止脏数据成环导致死循环
            Set<Long> visited = new HashSet<>();
            while (current != null && current > 0 && visited.add(current)) {
                result.add(current);
                current = parentOf.get(current);
            }
        }
        return new ArrayList<>(result);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Map<Long, Long> countUsersByRoleIds(List<Long> roleIds) {
        List<UserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getRoleId, roleIds));
        if (CollectionUtils.isEmpty(links)) {
            return Map.of();
        }
        return links.stream().collect(Collectors.groupingBy(UserRole::getRoleId, Collectors.counting()));
    }

    private void assertRoleCodeUnique(String roleCode, Long excludeId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode.trim());
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        Long count = roleMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.ROLE_CODE_DUPLICATE);
        }
    }
}
