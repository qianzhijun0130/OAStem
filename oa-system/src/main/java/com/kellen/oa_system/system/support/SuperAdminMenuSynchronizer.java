package com.kellen.oa_system.system.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.system.entity.Menu;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.RoleMenu;
import com.kellen.oa_system.system.mapper.MenuMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.RoleMenuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 超管菜单同步器（让"超管权限始终全量"从注释约定变成启动期事实）。
 *
 * <p><b>背景</b>：{@code RoleServiceImpl#assignMenus} 对 SUPER_ADMIN 直接抛
 * {@code FORBIDDEN}（"超级管理员权限不可修改"），目的是防止接口误缩超管权限。但副作用是——
 * 之后新增的菜单永远不会被挂到超管角色上。类注释声称"超管权限由系统保证始终为全量"，
 * 实际却因冻结而随菜单增长逐渐"缺斤少两"：新页面超管在真实登录下既看不到也 403。</p>
 *
 * <p><b>本组件的职责</b>：启动期把超管角色的菜单关联补齐为"全部菜单"。只<b>新增缺失项、绝不删除</b>，
 * 因此与"接口不可缩超管"的语义不冲突（接口仍禁改，这里只做补齐），且幂等、可回滚
 * （误加的行直接 delete oa_role_menu where role_id=超管 and menu_id in(...) 即可）。</p>
 *
 * <p><b>与 MenuIntegrityValidator 的分工</b>：校验器只读报告"超管是否覆盖全部菜单"；本组件负责把缺口补上。
 * 若把 {@code menu.super-admin.auto-sync} 关掉，则只剩校验器的告警，不做任何改动。</p>
 */
@Slf4j
@Component
public class SuperAdminMenuSynchronizer implements ApplicationRunner {

    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";
    private static final String TAG = "[超管菜单同步]";

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @Value("${menu.super-admin.auto-sync:true}")
    private boolean autoSync;

    public SuperAdminMenuSynchronizer(MenuMapper menuMapper,
                                      RoleMapper roleMapper,
                                      RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoSync) {
            log.info("{} 已关闭（menu.super-admin.auto-sync=false），跳过同步。", TAG);
            return;
        }
        Role superAdmin = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, SUPER_ADMIN_CODE));
        if (superAdmin == null) {
            log.warn("{} 未找到 SUPER_ADMIN 角色，跳过同步。", TAG);
            return;
        }

        Set<Long> allMenuIds = new HashSet<>();
        for (Menu m : menuMapper.selectList(new LambdaQueryWrapper<Menu>().select(Menu::getId))) {
            allMenuIds.add(m.getId());
        }
        if (allMenuIds.isEmpty()) {
            return;
        }

        Set<Long> linked = new HashSet<>();
        for (RoleMenu rm : roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getRoleId, superAdmin.getId()))) {
            linked.add(rm.getMenuId());
        }

        Set<Long> missing = new HashSet<>(allMenuIds);
        missing.removeAll(linked);
        if (missing.isEmpty()) {
            log.info("{} 超管已覆盖全部 {} 个菜单，无需同步。", TAG, allMenuIds.size());
            return;
        }

        int added = 0;
        for (Long menuId : missing) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(superAdmin.getId());
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
            added++;
        }
        log.info("{} 已为超管补挂 {} 个缺失菜单（共 {} 个）：{}",
                TAG, added, allMenuIds.size(), sorted(missing));
    }

    private List<Long> sorted(Set<Long> set) {
        List<Long> list = new java.util.ArrayList<>(set);
        list.sort(null);
        return list;
    }
}
