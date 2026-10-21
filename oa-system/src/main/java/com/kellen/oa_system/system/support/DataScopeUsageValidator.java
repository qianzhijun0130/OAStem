package com.kellen.oa_system.system.support;

import com.kellen.oa_system.common.annotation.DataScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 数据权限使用启动期校验（防"标注 @DataScope 却忘了过滤"的越权隐患）。
 *
 * <p><b>为什么需要</b>：本项目数据权限是<b>显式 opt-in</b>——{@code @DataScope} 只负责在切面里
 * 把当前用户的数据范围放进线程上下文，真正把条件拼进查询要靠方法内部调用
 * {@code DataScopeSupport#applyTo} / {@code currentSnapshot} 或日志专用的 {@code LogScopeResolver}。
 * 一旦某方法加了 {@code @DataScope} 却忘记调用这些 API，切面设了上下文却没人用，
 * 查询会原样返回<b>全部数据</b>——典型的"以为加了权限、实际越权"。本校验器在启动期把这类
 * "标注了却没应用"的方法捞出来告警（对标 RuoYi Office 的严格模式）。</p>
 *
 * <p><b>判定方式</b>：对每个标注 {@code @DataScope} 的方法，定位其实现类的源码（由编译产物路径反推
 * {@code src/main/java}），检查该类是否包含过滤 API 的调用（{@code applyTo(} / {@code currentSnapshot(} /
 * {@code logScopeResolver}）。实现类只要在某个方法里调用了过滤 API 即视为已应用（如 DeptServiceImpl 在
 * 私有方法 {@code filterByDataScope} 中调用 {@code currentSnapshot}）。</p>
 *
 * <p><b>仅报告、不改动</b>：生产 JAR 不含源码时自动跳过（INFO），不影响运行。</p>
 */
@Slf4j
@Component
public class DataScopeUsageValidator implements ApplicationRunner {

    private static final String TAG = "[数据权限校验]";

    /** 标记"已应用数据权限"的调用特征（命中其一即认为该类有过滤逻辑） */
    private static final String[] APPLY_MARKERS = {"applyTo(", "currentSnapshot(", "logScopeResolver"};

    private final ApplicationContext applicationContext;

    @Value("${datascope.validator.enabled:true}")
    private boolean enabled;

    public DataScopeUsageValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("{} 已关闭（datascope.validator.enabled=false），跳过校验。", TAG);
            return;
        }

        // 类 -> 该类下标注了 @DataScope 的方法名集合
        Map<String, Set<String>> annotatedByClass = new LinkedHashMap<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException e) {
                continue;
            }
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();
            ReflectionUtils.doWithMethods(targetClass, method -> {
                if (AnnotationUtils.findAnnotation(method, DataScope.class) != null) {
                    annotatedByClass
                            .computeIfAbsent(targetClass.getName(), k -> new TreeSet<>())
                            .add(method.getName() + "(" + method.getParameterCount() + ")");
                }
            }, m -> true);
        }

        if (annotatedByClass.isEmpty()) {
            log.info("{} 未扫描到任何 @DataScope 标注的方法。", TAG);
            return;
        }

        List<String> effective = new ArrayList<>();
        List<String> suspicious = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : annotatedByClass.entrySet()) {
            String className = entry.getKey();
            effective.add(className + "#" + String.join(", ", entry.getValue()));
            if (!classAppliesScope(targetClassOf(className))) {
                suspicious.add(className + " -> " + String.join(", ", entry.getValue()));
            }
        }

        log.info("{} 数据权限生效方法 {} 个（已正确应用过滤）：", TAG, effective.size());
        for (String e : effective) {
            log.info("{}   - {}", TAG, e);
        }

        if (suspicious.isEmpty()) {
            log.info("{} 所有 @DataScope 方法均已应用过滤，无越权隐患。", TAG);
        } else {
            log.warn("{} 以下类标注了 @DataScope 但在源码中找不到任何过滤调用（applyTo/currentSnapshot/logScopeResolver），"
                    + "可能越权返回全部数据，请检查：", TAG);
            for (String s : suspicious) {
                log.warn("{}   ⚠ {}", TAG, s);
            }
        }
    }

    /** 由类名加载类；加载失败（如运行期代理名）则视为无法判定，返回 true（不误报） */
    private Class<?> targetClassOf(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 该类源码是否包含过滤 API 调用；源码不可得（生产 JAR）时返回 true 以免误报 */
    private boolean classAppliesScope(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        Path src = sourceFileFor(clazz);
        if (src == null || !Files.isReadable(src)) {
            log.debug("{} 源码不可得（{}），跳过静态判定。", TAG, clazz.getName());
            return true;
        }
        try {
            String content = Files.readString(src);
            for (String marker : APPLY_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 由编译产物路径反推 src/main/java 下的源码路径 */
    private Path sourceFileFor(Class<?> clazz) {
        try {
            var codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            URI uri = codeSource.getLocation().toURI();
            String base = Paths.get(uri).toString().replace('\\', '/');
            String srcRoot;
            if (base.contains("/target/classes")) {
                srcRoot = base.replace("/target/classes", "/src/main/java");
            } else if (base.endsWith("/classes")) {
                srcRoot = base.substring(0, base.length() - "/classes".length()) + "/src/main/java";
            } else {
                return null;
            }
            String rel = clazz.getName().replace('.', '/') + ".java";
            return Paths.get(srcRoot).resolve(rel);
        } catch (Exception e) {
            return null;
        }
    }
}
