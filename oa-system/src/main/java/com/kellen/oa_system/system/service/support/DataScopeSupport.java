package com.kellen.oa_system.system.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.kellen.oa_system.common.enums.DataScopeEnum;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.entity.UserRole;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限支持。
 *
 * <p>负责两件事：</p>
 * <ol>
 *   <li>计算当前登录用户的有效数据范围（{@link #prepare()}，由 {@code DataScopeAspect} 在
 *       被 {@code @DataScope} 标注的方法进入时调用）；</li>
 *   <li>把范围条件拼进查询包装器（{@link #applyTo}，由业务方法显式调用）。</li>
 * </ol>
 *
 * <p><b>范围计算口径</b>：多角色取其<b>最宽</b>范围（枚举 code 最小值）；持有超级管理员角色的
 * 用户直接视为「全部数据」；无角色/角色被停用时收敛为「仅本人」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeSupport {

    /** 超级管理员角色编码：不受数据权限约束 */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    /** 当前调用栈的数据权限快照（由切面负责设置与清理） */
    private static final ThreadLocal<DataScopeSnapshot> HOLDER = new ThreadLocal<>();

    /** 数据权限快照：范围 + 所属部门 + 当前用户 */
    public record DataScopeSnapshot(DataScopeEnum scope, Long deptId, Long userId) {
    }

    /**
     * 计算并缓存当前登录用户的数据范围。由切面调用，业务代码不直接调用。
     */
    public void prepare() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        if (userId == null) {
            // 无登录上下文（如定时任务、系统内部调用）→ 不施加数据权限
            HOLDER.set(null);
            return;
        }

        Set<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)).stream()
                .map(UserRole::getRoleId).collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(roleIds)) {
            HOLDER.set(new DataScopeSnapshot(DataScopeEnum.SELF, null, userId));
            return;
        }

        // 仅启用角色参与范围计算：被停用的角色不应继续放大可见范围
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .in(Role::getId, roleIds)
                .eq(Role::getStatus, 1));
        if (CollectionUtils.isEmpty(roles)) {
            HOLDER.set(new DataScopeSnapshot(DataScopeEnum.SELF, null, userId));
            return;
        }
        if (roles.stream().anyMatch(role -> SUPER_ADMIN_CODE.equals(role.getRoleCode()))) {
            HOLDER.set(new DataScopeSnapshot(DataScopeEnum.ALL, null, userId));
            return;
        }

        // 多角色取最宽：enum code 越小范围越大（1全部 < 2本部门 < 3仅本人）
        DataScopeEnum scope = roles.stream()
                .map(role -> DataScopeEnum.parse(role.getDataScope()))
                .min(Comparator.comparingInt(DataScopeEnum::getCode))
                .orElse(DataScopeEnum.SELF);

        User self = userMapper.selectById(userId);
        Long deptId = self == null ? null : self.getDeptId();
        HOLDER.set(new DataScopeSnapshot(scope, deptId, userId));
    }

    /**
     * 清空上下文。由切面在 finally 中调用，避免线程复用导致范围串号。
     */
    public void clear() {
        HOLDER.remove();
    }

    /**
     * 取当前调用栈的数据权限快照。
     *
     * <p>供<b>无法使用 {@link #applyTo} 通用签名</b>的业务自行拼装条件时使用——
     * 典型场景是「部门表」：它自身没有 {@code dept_id} 列，「本部门」的语义是
     * 「当前用户所在部门 + 该部门的全部下级部门」，而非对本表 id 做等值过滤。</p>
     *
     * @return 快照；未被 {@code @DataScope} 标注时返回 {@code null}（表示本就不受约束）
     */
    public DataScopeSnapshot currentSnapshot() {
        return HOLDER.get();
    }

    /**
     * 把数据权限条件拼进查询包装器。
     *
     * @param wrapper    目标查询包装器
     * @param deptColumn 用于「本部门」过滤的部门列，如 {@code User::getDeptId}
     * @param idColumn   用于「仅本人」过滤的主键列，如 {@code User::getId}
     */
    public <T> void applyTo(LambdaQueryWrapper<T> wrapper,
                            SFunction<T, ?> deptColumn,
                            SFunction<T, ?> idColumn) {
        DataScopeSnapshot snapshot = HOLDER.get();
        if (snapshot == null) {
            // 未被 @DataScope 标注（或切面未生效）→ 该查询本就不受数据权限约束，不施加限制。
            // 这里不抛异常：数据权限属于「收窄可见范围」的增强，不该让未接入的查询不可用。
            log.debug("数据权限上下文为空，跳过条件拼装（查询未标注 @DataScope？）");
            return;
        }
        switch (snapshot.scope()) {
            case ALL -> {
                // 全部数据：不加任何限制
            }
            case DEPT -> {
                if (snapshot.deptId() == null) {
                    // 当前用户未归属任何部门 → 无可见数据（用恒假条件表达，避免误放开）
                    log.debug("数据范围为本部门但用户无部门，收敛为不可见: userId={}", snapshot.userId());
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.in(deptColumn, List.of(snapshot.deptId()));
            }
            case SELF -> wrapper.eq(idColumn, snapshot.userId());
        }
    }
}
