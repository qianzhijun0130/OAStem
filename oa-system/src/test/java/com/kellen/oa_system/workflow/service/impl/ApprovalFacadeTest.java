package com.kellen.oa_system.workflow.service.impl;

import com.kellen.oa_system.workflow.callback.ApprovalCallbackRegistry;
import com.kellen.oa_system.workflow.service.ApprovalService;
import com.kellen.oa_system.workflow.support.ApprovalLifecycleSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批模块拆分后的结构契约（BE-OPT-08 第 6/6，收尾）。
 *
 * <p>审批比前五个模块多一条<b>最关键的</b>约束：<b>终态只能有一个收敛点</b>。
 * 审批驳回、发起人撤销、管理员终止、超时自动驳回四条路径都要经过
 * {@link ApprovalLifecycleSupport}，因为它同时还承担业务回写
 * （请假/加班/出差/补卡的业务表状态）。</p>
 *
 * <p>如果有人在新入口里自己写一句 {@code update status}，那条路径的业务表就不会被回写，
 * 表现是「审批已通过，但请假单还在审批中」——只在某一类操作后出现，而且编译、
 * 类型检查、构建全部通过。所以这里用反射断言：<b>除了 LifecycleSupport，
 * 任何类都不许持有 {@link ApprovalCallbackRegistry}</b>。</p>
 *
 * <p>纯反射、不启 Spring 上下文、不连库。</p>
 */
class ApprovalFacadeTest {

    /** 拆分前：15 处写事务 + 4 处只读 */
    private static final int EXPECTED_TRANSACTIONAL = 19;

    private static final int EXPECTED_READ_ONLY = 4;

    private static final int EXPECTED_INTERFACE_METHODS = 22;

    /** 拆分后的全部协作类（门面除外），用于「不许自己开事务」「不许自己分发回调」两类断言 */
    private static final List<Class<?>> COLLABORATORS = List.of(
            ApprovalStartService.class, ApprovalQueryService.class, ApprovalInstanceQueryService.class,
            ApprovalActionService.class, ApprovalDetailService.class, ApprovalAdminService.class,
            ApprovalCcService.class, ApprovalTimeoutService.class);

    @Test
    @DisplayName("门面不直接使用 Mapper")
    void facadeHasNoMapperDependency() {
        List<String> offenders = new ArrayList<>();
        for (Field f : ApprovalServiceImpl.class.getDeclaredFields()) {
            if (f.getType().getSimpleName().endsWith("Mapper")) {
                offenders.add(f.getName());
            }
        }
        assertTrue(offenders.isEmpty(), "门面不应直接依赖 Mapper，发现: " + offenders);
    }

    @Test
    @DisplayName("事务注解只在门面：15 写 + 4 只读，协作类 0 处")
    void transactionAnnotationsStayOnFacade() {
        int total = 0;
        int readOnly = 0;
        for (Method m : ApprovalServiceImpl.class.getDeclaredMethods()) {
            Transactional txn = AnnotationUtils.findAnnotation(m, Transactional.class);
            if (txn == null) {
                continue;
            }
            total++;
            if (txn.readOnly()) {
                readOnly++;
            }
        }
        assertEquals(EXPECTED_TRANSACTIONAL, total, "门面事务注解数变化，请确认是否符合预期");
        assertEquals(EXPECTED_READ_ONLY, readOnly, "只读事务数量变化（拆分前 4 处）");

        for (Class<?> collaborator : COLLABORATORS) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertTrue(AnnotationUtils.findAnnotation(m, Transactional.class) == null,
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应自带事务注解");
            }
        }
    }

    @Test
    @DisplayName("终态收敛点唯一：只有 ApprovalLifecycleSupport 持有回调注册表")
    void terminalTransitionHasSingleOwner() {
        // 正向：收敛点必须还在
        boolean lifecycleHoldsRegistry = Arrays.stream(ApprovalLifecycleSupport.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == ApprovalCallbackRegistry.class);
        assertTrue(lifecycleHoldsRegistry,
                "ApprovalLifecycleSupport 必须持有 ApprovalCallbackRegistry：业务回写挂在终态收敛点上");

        // 反向：任何协作类/门面都不许自己持有（否则就是第二个分发点）
        List<Class<?>> others = new ArrayList<>(COLLABORATORS);
        others.add(ApprovalServiceImpl.class);
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : others) {
            for (Field f : type.getDeclaredFields()) {
                if (f.getType() == ApprovalCallbackRegistry.class) {
                    offenders.add(type.getSimpleName() + "#" + f.getName());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "回调分发只允许发生在 ApprovalLifecycleSupport，发现其它持有者: " + offenders);
    }

    @Test
    @DisplayName("接口方法全部实现，且一个不少")
    void allInterfaceMethodsImplemented() {
        long methodCount = Arrays.stream(ApprovalService.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .count();
        assertEquals(EXPECTED_INTERFACE_METHODS, methodCount, "ApprovalService 接口方法数变化，请同步本用例");
        for (Method m : ApprovalService.class.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.isDefault()) {
                continue;
            }
            Method impl = null;
            for (Method candidate : ApprovalServiceImpl.class.getDeclaredMethods()) {
                if (candidate.getName().equals(m.getName())) {
                    impl = candidate;
                }
            }
            assertNotNull(impl, "门面未实现: " + m.getName());
            assertEquals(m.getParameterCount(), impl.getParameterCount(), m.getName() + " 参数个数不一致");
        }
    }
}
