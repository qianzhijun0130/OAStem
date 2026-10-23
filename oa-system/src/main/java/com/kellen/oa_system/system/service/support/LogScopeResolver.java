package com.kellen.oa_system.system.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.enums.DataScopeEnum;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 日志可见范围解析器：把「当前登录人能看到谁的日志」这一步从业务查询里独立出来。
 *
 * <p><b>为什么日志要单独一套范围，而不能直接复用 {@code @DataScope}</b>：
 * 日志表的行归属人是「操作人 {@code user_id}」，而 {@code oa_user} 的行归属人是「部门 {@code dept_id}」，
 * 两者无法用同一套列去过滤。{@code applyTo} 要求目标表自带「部门列 + 主键列」，日志表两个都没有。
 * 因此这里先把范围解析成 <b>可见 user_id 集合</b>，再由业务方以 {@code user_id IN (...)} 落进查询。</p>
 *
 * <p><b>三档口径</b>（由角色体系与用户-部门归属关系推导）：</p>
 * <ol>
 *   <li><b>超级管理员</b> → 不限。判定依据是数据范围为 {@code ALL}
 *       （{@code DataScopeSupport#prepare} 中只有持有 {@code SUPER_ADMIN} 才会得到 ALL）；</li>
 *   <li><b>部门负责人</b> → 本部门 + 其<b>全部下级部门</b>的成员。判定依据是
 *       「持有该日志的查询权限码」且数据范围为 {@code DEPT}；</li>
 *   <li><b>普通用户</b> → 仅本人。</li>
 * </ol>
 *
 * <p><b>安全要点</b>：未持有查询权限码者一律收敛为「仅本人」，绝不因为其角色的
 * {@code data_scope} 较宽而获得他人日志 —— 「能配置多大数据范围」与「能看审计日志」是两件事。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogScopeResolver {

    private final DataScopeSupport dataScopeSupport;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    /**
     * 日志可见范围。
     *
     * @param unlimited 是否不限（超级管理员）
     * @param userIds   可见的操作人 id 集合；{@code unlimited=false} 时有效，至少含当前用户
     */
    public record LogScope(boolean unlimited, Set<Long> userIds) {

        /** 不限（超级管理员） */
        public static LogScope all() {
            return new LogScope(true, Set.of());
        }

        /** 仅本人 */
        public static LogScope only(Long userId) {
            return new LogScope(false, userId == null ? Set.of() : Set.of(userId));
        }

        /** 是否允许访问指定 userId 的日志；unlimited 时恒为 true */
        public boolean allows(Long targetUserId) {
            return unlimited || userIds.contains(targetUserId);
        }
    }

    /**
     * 解析当前登录人的日志可见范围。
     *
     * @param queryAuthority 访问该类日志所需的功能权限码（如 {@code system:operation-log:query}）。
     *                       未持有它的人只能看到自己的日志。
     * @return 可见范围；调用方须把它作为<b>强制条件</b>写进查询，前端传入的筛选参数只能在其上收窄
     */
    public LogScope resolve(String queryAuthority) {
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        DataScopeSupport.DataScopeSnapshot snapshot = dataScopeSupport.currentSnapshot();
        if (snapshot == null) {
            // 未被 @DataScope 标注或切面未生效：按最小权限收敛，绝不误放开
            log.debug("日志范围：数据权限上下文为空，收敛为仅本人 userId={}", currentUserId);
            return LogScope.only(currentUserId);
        }
        if (snapshot.scope() == DataScopeEnum.ALL) {
            return LogScope.all();
        }
        if (!hasAuthority(queryAuthority)) {
            return LogScope.only(currentUserId);
        }
        if (snapshot.scope() == DataScopeEnum.SELF || snapshot.deptId() == null) {
            return LogScope.only(currentUserId);
        }
        Set<Long> visible = collectDeptSubtreeUserIds(snapshot.deptId());
        // 保险：部门归属异常（如部门被停用/数据不一致）时至少仍能看到自己的日志
        visible.add(currentUserId);
        log.debug("日志范围：部门负责人 userId={} 可见成员 {} 人", currentUserId, visible.size());
        return new LogScope(false, visible);
    }

    /** 判断当前认证主体是否持有指定权限码 */
    private boolean hasAuthority(String code) {
        if (code == null) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> code.equals(granted.getAuthority()));
    }

    /**
     * 收集「本部门 + 全部下级部门」的存活成员 id。
     *
     * <p>部门量级小（通常数百），一次查全表后在内存逐轮收敛，不做递归查库；
     * 轮数由集合是否继续增长决定，脏数据造成的环不会导致死循环。</p>
     */
    private Set<Long> collectDeptSubtreeUserIds(Long rootDeptId) {
        List<Department> allDepartments = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getParentId));
        Set<Long> deptIds = new HashSet<>();
        deptIds.add(rootDeptId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Department dept : allDepartments) {
                Long parentId = dept.getParentId();
                if (parentId != null && deptIds.contains(parentId) && deptIds.add(dept.getId())) {
                    changed = true;
                }
            }
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .in(User::getDeptId, deptIds)
                        .eq(User::getIsDeleted, 0))
                .stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }
}
