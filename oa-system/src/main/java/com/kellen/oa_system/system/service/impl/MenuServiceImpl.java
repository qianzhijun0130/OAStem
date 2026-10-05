package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.system.dto.MenuSaveRequest;
import com.kellen.oa_system.system.entity.Menu;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.RoleMenu;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.MenuMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.RoleMenuMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import com.kellen.oa_system.system.service.MenuService;
import com.kellen.oa_system.system.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现。
 *
 * <p>菜单树构建：一次性查出全部菜单，在内存中按 parentId 分组装配，避免递归查库的 N+1。
 * 侧边栏树只取目录(1)与菜单(2)，全量树再额外包含按钮(3)用于权限分配。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 目录 */
    private static final int TYPE_DIRECTORY = 1;
    /** 菜单 */
    private static final int TYPE_MENU = 2;
    /** 按钮：不参与路由，无 path / component */
    private static final int TYPE_BUTTON = 3;

    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;

    @Override
    public List<MenuVO> getMenuTreeByUserId(Long userId) {
        Set<Long> menuIds = queryMenuIds(userId);
        if (CollectionUtils.isEmpty(menuIds)) {
            return List.of();
        }
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .in(Menu::getId, menuIds)
                .in(Menu::getMenuType, List.of(TYPE_DIRECTORY, TYPE_MENU))
                .eq(Menu::getVisible, 1)
                .eq(Menu::getStatus, 1)
                .orderByAsc(Menu::getSort));
        if (CollectionUtils.isEmpty(menus)) {
            return List.of();
        }
        return buildTree(menus);
    }

    @Override
    public List<MenuVO> getFullMenuTree() {
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .orderByAsc(Menu::getSort).orderByAsc(Menu::getId));
        if (CollectionUtils.isEmpty(menus)) {
            return List.of();
        }
        return buildFullTree(menus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveMenu(MenuSaveRequest request) {
        assertTypeFieldsValid(request);
        assertMenuCodeUnique(request.getMenuCode(), request.getId());

        Menu menu = new Menu();
        menu.setMenuName(request.getMenuName().trim());
        menu.setMenuCode(request.getMenuCode().trim());
        menu.setMenuType(request.getMenuType());
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setPath(request.getPath() == null ? null : request.getPath().trim());
        menu.setComponent(request.getComponent() == null ? null : request.getComponent().trim());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? 0 : request.getSort());
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menu.setStatus(request.getStatus() == null ? 1 : request.getStatus());

        if (request.getId() == null) {
            menuMapper.insert(menu);
            log.info("新增菜单: id={}, name={}", menu.getId(), menu.getMenuName());
            return menu.getId();
        }
        Menu existing = menuMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);
        }
        menu.setId(request.getId());
        menuMapper.updateById(menu);
        log.info("修改菜单: id={}, name={}", menu.getId(), menu.getMenuName());
        return menu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        Menu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);
        }
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
        // oa_role_menu.menu_id 无外键约束，若不显式清理会留下悬挂引用：
        // 角色权限分配页会渲染出「幽灵节点」，且角色授权项数虚高。
        // 与 RoleServiceImpl#deleteRole 的级联清理保持同一口径。
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, id));
        log.info("删除菜单: id={}, name={}", id, menu.getMenuName());
    }

    @Override
    public void updateMenuStatus(Long id, Integer status) {
        Menu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);
        }
        Menu update = new Menu();
        update.setId(id);
        update.setStatus(status);
        menuMapper.updateById(update);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Set<Long> queryMenuIds(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (CollectionUtils.isEmpty(userRoles)) {
            return Set.of();
        }
        Set<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        // 仅取启用角色（status=1）的菜单：被停用的角色不应再决定用户可见菜单
        Set<Long> enabledRoleIds = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>().in(Role::getId, roleIds).eq(Role::getStatus, 1))
                .stream().map(Role::getId).collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(enabledRoleIds)) {
            return Set.of();
        }
        List<RoleMenu> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, enabledRoleIds));
        if (CollectionUtils.isEmpty(roleMenus)) {
            return Set.of();
        }
        return roleMenus.stream().map(RoleMenu::getMenuId).collect(Collectors.toSet());
    }

    /** 侧边栏树：仅目录与菜单，按 parentId 装配 */
    private List<MenuVO> buildTree(List<Menu> menus) {
        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        for (Menu menu : menus) {
            nodeMap.put(menu.getId(), toVO(menu));
        }
        List<MenuVO> roots = new ArrayList<>();
        for (Menu menu : menus) {
            MenuVO current = nodeMap.get(menu.getId());
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0L) {
                roots.add(current);
                continue;
            }
            MenuVO parent = nodeMap.get(parentId);
            if (parent == null) {
                // 父节点被过滤掉（停用 / 隐藏 / 未授权）时不得提升为顶层：
                // 否则会出现「目录已消失、子页面却挂在侧边栏顶层」的结构错乱。
                // 子节点的可见性跟随父节点，整枝丢弃。
                log.debug("侧边栏构树丢弃节点: id={}, name={}（父节点 {} 不在可见集合内）",
                        menu.getId(), menu.getMenuName(), parentId);
                continue;
            }
            if (parent.getId().equals(menu.getId())) {
                // 脏数据自引用：容错为根节点，避免节点凭空消失
                roots.add(current);
                continue;
            }
            if (!(parent.getChildren() instanceof ArrayList)) {
                parent.setChildren(new ArrayList<>(parent.getChildren()));
            }
            parent.getChildren().add(current);
        }
        roots.sort(Comparator.comparing(vo -> vo.getSort() == null ? 0 : vo.getSort()));
        for (MenuVO root : roots) {
            sortChildren(root);
        }
        return roots;
    }

    /** 全量树：含按钮，按 parentId 装配 */
    private List<MenuVO> buildFullTree(List<Menu> menus) {
        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        for (Menu menu : menus) {
            nodeMap.put(menu.getId(), toVO(menu));
        }
        List<MenuVO> roots = new ArrayList<>();
        for (Menu menu : menus) {
            MenuVO current = nodeMap.get(menu.getId());
            MenuVO parent = nodeMap.get(menu.getParentId());
            if (parent == null || parent.getId().equals(menu.getId())) {
                roots.add(current);
            } else {
                if (!(parent.getChildren() instanceof ArrayList)) {
                    parent.setChildren(new ArrayList<>(parent.getChildren()));
                }
                parent.getChildren().add(current);
            }
        }
        roots.sort(Comparator.comparing(vo -> vo.getSort() == null ? 0 : vo.getSort()));
        for (MenuVO root : roots) {
            sortChildren(root);
        }
        return roots;
    }

    private void sortChildren(MenuVO node) {
        if (CollectionUtils.isEmpty(node.getChildren())) {
            return;
        }
        node.getChildren().sort(Comparator.comparing(vo -> vo.getSort() == null ? 0 : vo.getSort()));
        for (MenuVO child : node.getChildren()) {
            sortChildren(child);
        }
    }

    /**
     * 校验「菜单类型 ↔ 字段」的条件约束。
     *
     * <p>目录(1)与菜单(2)必须有路由地址，菜单(2)还必须有组件路径；按钮(3)不参与路由。
     * 该约束原先只写在注释里未实际生效，导致可落库「前端无法渲染」的菜单数据
     * （如 menuType=2 而 component 为空）。</p>
     */
    private void assertTypeFieldsValid(MenuSaveRequest request) {
        Integer type = request.getMenuType();
        if (type == null || type == TYPE_BUTTON) {
            // null 由 @NotNull 兜底；按钮无路由字段要求
            return;
        }
        if (!StringUtils.hasText(request.getPath())) {
            throw new BizException(ErrorCode.MENU_FIELDS_INVALID, "目录与菜单必须填写路由地址");
        }
        if (type == TYPE_MENU && !StringUtils.hasText(request.getComponent())) {
            throw new BizException(ErrorCode.MENU_FIELDS_INVALID, "菜单必须填写组件路径");
        }
    }

    private void assertMenuCodeUnique(String menuCode, Long excludeId) {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<Menu>()
                .eq(Menu::getMenuCode, menuCode.trim());
        if (excludeId != null) {
            wrapper.ne(Menu::getId, excludeId);
        }
        Long count = menuMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.MENU_CODE_DUPLICATE);
        }
    }

    private MenuVO toVO(Menu menu) {
        return MenuVO.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .menuName(menu.getMenuName())
                .menuCode(menu.getMenuCode())
                .menuType(menu.getMenuType())
                .path(menu.getPath())
                .component(menu.getComponent())
                .icon(menu.getIcon())
                .sort(menu.getSort())
                .visible(menu.getVisible())
                .status(menu.getStatus())
                .children(new ArrayList<>())
                .build();
    }
}
