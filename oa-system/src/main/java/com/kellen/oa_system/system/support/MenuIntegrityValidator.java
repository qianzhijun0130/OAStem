package com.kellen.oa_system.system.support;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单完整性启动期校验（孤儿菜单清理的自动化手段，对标 RuoYi Office 的菜单完整性检查）。
 *
 * <p><b>要抓的隐患</b>（均为"脏数据"，且删除菜单时若漏清关联会真实产生）：
 * <ol>
 *   <li><b>断裂父引用</b>：菜单的 {@code parent_id} 既非 0 又指向一个不存在的菜单，
 *       构树时会被错误提升为顶层，出现"父目录消失、子节点却挂在根"的结构错乱；</li>
 *   <li><b>悬挂角色菜单</b>：{@code oa_role_menu.menu_id} 指向一个不存在的菜单
 *       （直接改库或早期删除菜单未级联清理时会出现），角色授权页会渲染出"幽灵节点"、授权项数虚高；</li>
 *   <li><b>未被任何角色引用的菜单</b>（信息级）：可能是超管专用或待分配，仅提示不报错。</li>
 * </ol>
 * </p>
 *
 * <p><b>与超管一致性的关系</b>：超管（role_code=SUPER_ADMIN）经 {@code RoleServiceImpl} 保证角色关联全部菜单、
 * 经 {@code DataScopeSupport}/{@code LogScopeResolver} 保证数据范围=ALL。本校验器额外确认"超管是否真的覆盖了全部菜单"，
 * 把它从隐性约定变成启动期可读的断言，避免某次菜单录入忘了给超管角色挂上、导致超管也看不到新功能。</p>
 *
 * <p><b>仅报告、不改动</b>：本项目菜单表无 {@code is_deleted}，删除是物理删除且高风险，故本校验器只打印结论，
 * 真实清理由人工确认后通过菜单管理接口执行（与"缺陷只记录不修复"的项目约定一致）。</p>
 */
@Slf4j
@Component
public class MenuIntegrityValidator implements ApplicationRunner {

    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";
    private static final String TAG = "[菜单完整性校验]";

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @Value("${menu.integrity-check.enabled:true}")
    private boolean enabled;

    public MenuIntegrityValidator(MenuMapper menuMapper,
                                  RoleMapper roleMapper,
                                  RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("{} 已关闭（menu.integrity-check.enabled=false），跳过校验。", TAG);
            return;
        }

        List<Menu> menus = menuMapper.selectList(null);
        Set<Long> ids = new HashSet<>();
        for (Menu m : menus) {
            ids.add(m.getId());
        }

        // 1) 断裂父引用
        List<Menu> brokenParent = new ArrayList<>();
        for (Menu m : menus) {
            Long pid = m.getParentId();
            if (pid != null && pid != 0L && !ids.contains(pid)) {
                brokenParent.add(m);
            }
        }

        // 2) 悬挂角色菜单
        Set<Long> danglingRoleMenu = new HashSet<>();
        for (RoleMenu rm : roleMenuMapper.selectList(null)) {
            if (!ids.contains(rm.getMenuId())) {
                danglingRoleMenu.add(rm.getMenuId());
            }
        }

        // 3) 未被任何角色引用的菜单 + 超管覆盖情况
        Set<Long> referenced = new HashSet<>();
        for (RoleMenu rm : roleMenuMapper.selectList(null)) {
            referenced.add(rm.getMenuId());
        }
        List<Menu> deadMenus = new ArrayList<>();
        for (Menu m : menus) {
            if (!referenced.contains(m.getId())) {
                deadMenus.add(m);
            }
        }
        Set<Long> saMissing = computeSuperAdminMissing(ids);
        boolean superAdminCoversAll = saMissing.isEmpty();

        log.info("{} 菜单 {} 个、角色菜单关联 {} 条。",
                TAG, menus.size(), roleMenuMapper.selectCount(null));

        boolean hasError = !brokenParent.isEmpty() || !danglingRoleMenu.isEmpty();
        if (hasError) {
            StringBuilder sb = new StringBuilder(TAG).append(" 发现菜单完整性问题：\n");
            if (!brokenParent.isEmpty()) {
                sb.append("  断裂父引用(").append(brokenParent.size()).append("): ")
                        .append(brokenParent.stream()
                                .map(m -> "id=" + m.getId() + " " + m.getMenuName() + "(parentId=" + m.getParentId() + ")")
                                .toList())
                        .append("\n");
            }
            if (!danglingRoleMenu.isEmpty()) {
                sb.append("  悬挂角色菜单(menu_id 不存在)(").append(danglingRoleMenu.size()).append("): ")
                        .append(danglingRoleMenu).append("\n");
            }
            log.error(sb.toString().trim());
        } else {
            log.info("{} 菜单结构完整：无断裂父引用、无悬挂角色菜单引用。", TAG);
        }

        if (deadMenus.isEmpty()) {
            log.info("{} 全部菜单均被至少一个角色引用。", TAG);
        } else {
            log.info("{} 未被任何角色引用的菜单 {} 个（可能为超管专用或待分配，信息提示不报错）：{}",
                    TAG, deadMenus.size(),
                    deadMenus.stream().map(m -> "id=" + m.getId() + " " + m.getMenuName()).toList());
        }
        if (superAdminCoversAll) {
            log.info("{} 超管(SUPER_ADMIN)角色已覆盖全部菜单。", TAG);
        } else {
            log.warn("{} 超管(SUPER_ADMIN)角色未覆盖全部菜单，缺失菜单 id：{}（已由 SuperAdminMenuSynchronizer 启动时补齐）。",
                    TAG, sorted(saMissing));
        }
    }

    /** 返回超管角色未关联的菜单 id（空集合表示已覆盖全部）。超管角色不存在时返回全部菜单 id。 */
    private Set<Long> computeSuperAdminMissing(Set<Long> menuIds) {
        Set<Long> missing = new HashSet<>(menuIds);
        if (menuIds.isEmpty()) {
            return missing;
        }
        Role superAdmin = roleMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, SUPER_ADMIN_CODE));
        if (superAdmin == null) {
            return missing;
        }
        Set<Long> saMenus = new HashSet<>();
        for (RoleMenu rm : roleMenuMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getRoleId, superAdmin.getId()))) {
            saMenus.add(rm.getMenuId());
        }
        missing.removeAll(saMenus);
        return missing;
    }

    private List<Long> sorted(Set<Long> set) {
        List<Long> list = new ArrayList<>(set);
        list.sort(null);
        return list;
    }
}
