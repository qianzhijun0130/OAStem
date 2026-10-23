package com.kellen.oa_system.hr.common.support;

import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.SecurityContextUtil;

import java.util.Set;

/**
 * 人力资源敏感角色判定。
 *
 * <p>为什么单独抽出来：档案、薪资、绩效三处都要问同一个问题——
 * 「当前登录人算不算人事」。各写一遍的结果是权限口径漂移：
 * 某天给 {@code HR_MANAGER} 补了档案权限，却漏了薪资，于是「能改档案但看不到工资」。</p>
 *
 * <p>判定依据是<b>角色编码</b>而不是权限码：权限码可以被随意授给任意角色，
 * 而这三个角色是系统内置、界面上不允许删除的。</p>
 */
public final class HrRoles {

    /** 可查看敏感字段（身份证、银行卡、薪资）与全员的角色 */
    public static final Set<String> SENSITIVE_ROLES = Set.of("SUPER_ADMIN", "HR", "HR_MANAGER");

    private HrRoles() {
    }

    /** 当前登录人是否属于人事/超管 */
    public static boolean isHr() {
        LoginUser current = SecurityContextUtil.getCurrentUser();
        return current != null && current.getRoles() != null
                && current.getRoles().stream().anyMatch(SENSITIVE_ROLES::contains);
    }

    /** 指定角色集合里是否包含人事/超管 */
    public static boolean isHr(java.util.Collection<String> roles) {
        return roles != null && roles.stream().anyMatch(SENSITIVE_ROLES::contains);
    }
}
