package com.kellen.oa_system.system.support;

import com.kellen.oa_system.system.entity.Menu;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.mapper.MenuMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 权限码启动期严格校验（对标 RuoYi Office 的权限码严格模式）。
 *
 * <p><b>要解决什么</b>：后端用 {@code @PreAuthorize("hasAuthority('system:user:add')")} 声明接口权限，
 * 而真正能授予用户的权限码来自 {@code oa_menu.menu_code}（经角色关联装入 JWT）。二者靠人力对齐，
 * 一旦某处拼写错误、或新增接口忘了在菜单表登记权限码，该接口对<strong>所有人</strong>（含超管）都会
 * 静默返回 403——运行时毫无报错，排查成本极高。本校验器在应用启动期把"代码里声明了的码"与
 * "菜单表/角色表里实际存在的码"做差集，把这类隐患前置成一条醒目的启动日志（开发期可配置为直接中止启动）。</p>
 *
 * <p><b>扫描范围</b>：遍历全部 Spring Bean，对每个 Bean（含 AOP 代理背后的目标类）及其接口、父类上带
 * {@link PreAuthorize} 的方法/类，解析 SpEL 中的 {@code hasAuthority / hasAnyAuthority / hasRole / hasAnyRole}
 * 字面量。动态拼接的权限码（如 {@code hasAuthority(#code)}）无字面量，正则匹配不到，自然不纳入校验——这是符合预期的。</p>
 *
 * <p><b>与超管的关系</b>：超管的"全量权限"由 {@code RoleServiceImpl} 保证其角色关联全部菜单，本质上仍依赖
 * 菜单表里存在对应 {@code menu_code}。因此"声明了但表里没有"的码对超管同样不可达，校验器将其一并标出是正确的。</p>
 *
 * <p><b>配置项</b>（见 application.properties）：
 * <ul>
 *   <li>{@code permission.validation.enabled}：总开关，默认 true；</li>
 *   <li>{@code permission.validation.fail-on-missing}：缺失码是否中止启动。默认 false（仅 ERROR 告警，不阻断）；显式 true 进入严格模式；</li>
 *   <li>{@code permission.validation.exclude-codes}：逗号分隔的豁免码，确属有意为之、不入库的码可放这里。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class PermissionCodeValidator implements ApplicationRunner {

    private static final Pattern HAS_AUTHORITY = Pattern.compile(
            "hasAuthority\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern HAS_ANY_AUTHORITY = Pattern.compile(
            "hasAnyAuthority\\s*\\(\\s*([^)]*)\\s*\\)");
    private static final Pattern HAS_ROLE = Pattern.compile(
            "hasRole\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern HAS_ANY_ROLE = Pattern.compile(
            "hasAnyRole\\s*\\(\\s*([^)]*)\\s*\\)");

    private final ApplicationContext applicationContext;
    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final Environment environment;

    @Value("${permission.validation.enabled:true}")
    private boolean enabled;
    @Value("${permission.validation.fail-on-missing:}")
    private String failOnMissingRaw;
    @Value("${permission.validation.exclude-codes:}")
    private String excludeCodesRaw;

    /** 启动日志统一前缀，便于在海量启动日志里一眼定位 */
    private static final String TAG = "[权限码校验]";

    public PermissionCodeValidator(ApplicationContext applicationContext,
                                   MenuMapper menuMapper,
                                   RoleMapper roleMapper,
                                   Environment environment) {
        this.applicationContext = applicationContext;
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("{} 已关闭（permission.validation.enabled=false），跳过校验。", TAG);
            return;
        }

        Set<String> declaredPerms = new HashSet<>();
        Set<String> declaredRoles = new HashSet<>();
        scanDeclaredCodes(declaredPerms, declaredRoles);

        Set<String> dbPerms = loadMenuCodes();
        Set<String> dbRoles = loadRoleCodes();

        Set<String> exclude = parseExcludeCodes();
        declaredPerms.removeAll(exclude);

        Set<String> missingPerms = difference(declaredPerms, dbPerms);
        Set<String> missingRoles = difference(declaredRoles, dbRoles);

        log.info("{} 扫描到 @PreAuthorize 声明权限码 {} 个、角色码 {} 个；"
                        + "菜单表权限码 {} 个、角色表角色码 {} 个。",
                TAG, declaredPerms.size(), declaredRoles.size(), dbPerms.size(), dbRoles.size());

        boolean hasViolation = !missingPerms.isEmpty() || !missingRoles.isEmpty();
        if (!hasViolation) {
            log.info("{} 全部声明的权限码/角色码均能在菜单表/角色表中找到，无静默 403 隐患。", TAG);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(TAG).append(" 发现声明的权限码在库中不存在（将永远 403）：\n");
        if (!missingPerms.isEmpty()) {
            sb.append("  缺失权限码(").append(missingPerms.size()).append("): ")
                    .append(sorted(missingPerms)).append("\n");
        }
        if (!missingRoles.isEmpty()) {
            sb.append("  缺失角色码(").append(missingRoles.size()).append("): ")
                    .append(sorted(missingRoles)).append("\n");
        }

        boolean fail = resolveFailOnMissing();
        if (fail) {
            // 开发期严格模式：直接中止启动，逼开发者当场补菜单/角色数据或修正拼写
            throw new IllegalStateException(sb.append(TAG).append(" 严格模式已开启，启动中止。").toString());
        }
        // 生产/默认：以 ERROR 级别醒目提示，但不阻断启动
        log.error(sb.toString().trim());
    }

    /**
     * 遍历所有 Bean，解析其中的 @PreAuthorize 字面量权限码/角色码。
     *
     * <p>用 {@link AnnotationUtils#findAnnotation} 而非方法直接取注解：它能沿类继承链与接口
     * 合并查找（@PreAuthorize 非 @Inherited），覆盖"注解写在接口方法、实现类未重写"的情况。</p>
     */
    private void scanDeclaredCodes(Set<String> perms, Set<String> roles) {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException e) {
                // 个别 Bean（如某些懒加载/环境相关的）可能在此时拿不到，跳过不影响权限校验
                continue;
            }
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = AopUtils.isAopProxy(bean)
                    ? AopUtils.getTargetClass(bean)
                    : bean.getClass();

            // 类级别 @PreAuthorize
            PreAuthorize classAnn = AnnotationUtils.findAnnotation(targetClass, PreAuthorize.class);
            if (classAnn != null) {
                parseExpression(classAnn.value(), perms, roles);
            }
            // 方法级别 @PreAuthorize（含接口方法与父类方法）
            ReflectionUtils.doWithMethods(targetClass, method -> {
                PreAuthorize ann = AnnotationUtils.findAnnotation(method, PreAuthorize.class);
                if (ann != null) {
                    parseExpression(ann.value(), perms, roles);
                }
            }, method -> true);
        }
    }

    /** 解析一段 SpEL 表达式，提取其中的权限码与角色码字面量（忽略动态拼接的变量形式） */
    private void parseExpression(String expr, Set<String> perms, Set<String> roles) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        Matcher m;
        m = HAS_AUTHORITY.matcher(expr);
        while (m.find()) {
            perms.add(m.group(1).trim());
        }
        m = HAS_ANY_AUTHORITY.matcher(expr);
        while (m.find()) {
            for (String token : splitQuotedList(m.group(1))) {
                perms.add(token);
            }
        }
        m = HAS_ROLE.matcher(expr);
        while (m.find()) {
            roles.add(m.group(1).trim());
        }
        m = HAS_ANY_ROLE.matcher(expr);
        while (m.find()) {
            for (String token : splitQuotedList(m.group(1))) {
                roles.add(token);
            }
        }
    }

    /** 把 hasAnyAuthority('a','b') 这类内部列表拆成单个码（兼容单/双引号与空格） */
    private List<String> splitQuotedList(String inner) {
        List<String> result = new ArrayList<>();
        if (inner == null || inner.isBlank()) {
            return result;
        }
        for (String token : inner.split(",")) {
            String t = token.trim();
            if (t.length() >= 2 && (t.startsWith("'") && t.endsWith("'")
                    || t.startsWith("\"") && t.endsWith("\""))) {
                t = t.substring(1, t.length() - 1).trim();
            }
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }

    private Set<String> loadMenuCodes() {
        Set<String> codes = new HashSet<>();
        for (Menu menu : menuMapper.selectList(null)) {
            if (menu.getMenuCode() != null && !menu.getMenuCode().isBlank()) {
                codes.add(menu.getMenuCode().trim());
            }
        }
        return codes;
    }

    private Set<String> loadRoleCodes() {
        Set<String> codes = new HashSet<>();
        for (Role role : roleMapper.selectList(null)) {
            if (role.getRoleCode() != null && !role.getRoleCode().isBlank()) {
                codes.add(role.getRoleCode().trim());
            }
        }
        return codes;
    }

    /** A - B，返回 A 中有而 B 中没有的元素（按忽略大小写？权限码区分大小写，故区分） */
    private Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private Set<String> parseExcludeCodes() {
        Set<String> exclude = new HashSet<>();
        if (excludeCodesRaw == null || excludeCodesRaw.isBlank()) {
            return exclude;
        }
        for (String token : excludeCodesRaw.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) {
                exclude.add(t);
            }
        }
        return exclude;
    }

    /**
     * 是否缺失即中止启动。默认 false（仅以 ERROR 告警，不阻断启动），避免开发期因历史遗留缺口
     * 直接把后端拒之门外；把 {@code permission.validation.fail-on-missing=true} 显式打开即可进入
     * 严格模式（对标 RuoYi Office，启动即暴露并阻断）。
     */
    private boolean resolveFailOnMissing() {
        if (failOnMissingRaw != null && !failOnMissingRaw.isBlank()) {
            return Boolean.parseBoolean(failOnMissingRaw.trim());
        }
        return false;
    }

    private List<String> sorted(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        list.sort(null);
        return list;
    }
}
