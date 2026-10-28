package com.kellen.oa_system.hr.attendance.service.impl;

import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.attendance.service.AttendanceService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 考勤模块拆分后的结构契约（BE-OPT-08 第 4/6）。
 *
 * <p>考勤比前三个模块多两条必须锁死的东西：</p>
 * <ol>
 *   <li><b>{@code @DataScope} 必须在门面的 page / pageMonth / exportMonth 上</b>。
 *       与薪酬不同，考勤的取数层是「快照为空就抛错」（{@code AttendanceScopeSupport}），
 *       漏标会从「静默越权」变成「500」，但两者都是事故；而且错误信息里的 caller
 *       只能靠调用方传参，注解位置错了根本看不出是谁漏的。</li>
 *   <li><b>{@code resettleAfterCommit} 不得带 {@code @Transactional}</b>。
 *       它的职责是「注册到当前事务的 afterCommit」；一旦自己开了事务，
 *       提交时才触发重算的语义就没了（会在审批事务内直接执行，锁窗口又回到 BE-OPT-01 之前）。</li>
 * </ol>
 *
 * <p>纯反射、不启 Spring 上下文、不连库。</p>
 */
class AttendanceFacadeTest {

    /** 拆分前：7 处 readOnly + 11 处 rollbackFor */
    private static final int EXPECTED_TRANSACTIONAL = 18;

    private static final int EXPECTED_READ_ONLY = 7;

    private static final int EXPECTED_INTERFACE_METHODS = 21;

    @Test
    @DisplayName("门面不直接使用 Mapper")
    void facadeHasNoMapperDependency() {
        List<String> offenders = new ArrayList<>();
        for (Field f : AttendanceServiceImpl.class.getDeclaredFields()) {
            if (f.getType().getSimpleName().endsWith("Mapper")) {
                offenders.add(f.getName());
            }
        }
        assertTrue(offenders.isEmpty(), "门面不应直接依赖 Mapper，发现: " + offenders);
    }

    @Test
    @DisplayName("事务注解只在门面，数量与拆分前一致（11 读写 + 7 只读）")
    void transactionAnnotationsStayOnFacade() {
        int total = 0;
        int readOnly = 0;
        for (Method m : AttendanceServiceImpl.class.getDeclaredMethods()) {
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
        assertEquals(EXPECTED_READ_ONLY, readOnly, "只读事务数量变化（拆分前 7 处）");

        for (Class<?> collaborator : List.of(AttendanceShiftService.class, AttendanceScheduleService.class,
                AttendanceClockService.class, AttendanceQueryService.class,
                AttendanceSettleService.class, AttendanceRecalcService.class,
                AttendanceLockService.class, AttendanceExportService.class)) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertTrue(AnnotationUtils.findAnnotation(m, Transactional.class) == null,
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应自带事务注解");
            }
        }
    }

    @Test
    @DisplayName("@DataScope 在门面三个查询入口，协作类不得重复标注")
    void dataScopeStaysOnFacade() {
        for (String name : List.of("page", "pageMonth", "exportMonth")) {
            Method target = find(AttendanceServiceImpl.class, name);
            assertNotNull(target, "门面缺少方法: " + name);
            assertNotNull(AnnotationUtils.findAnnotation(target, DataScope.class),
                    name + " 必须保留 @DataScope：数据权限快照由切面在该方法上写入");
        }
        for (Class<?> collaborator : List.of(AttendanceQueryService.class, AttendanceExportService.class)) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertNull(AnnotationUtils.findAnnotation(m, DataScope.class),
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应再标 @DataScope");
            }
        }
    }

    @Test
    @DisplayName("resettleAfterCommit 不得事务化（否则 afterCommit 语义失效）")
    void afterCommitHookStaysOutsideTransaction() {
        Method target = find(AttendanceServiceImpl.class, "resettleAfterCommit");
        assertNotNull(target, "门面缺少 resettleAfterCommit");
        assertNull(AnnotationUtils.findAnnotation(target, Transactional.class),
                "resettleAfterCommit 不能带 @Transactional：它负责把重算挂到已提交事务之后");
        // 三个重算入口一个都不能少（审批回调与销假流程直接依赖它们）
        for (String name : List.of("invalidateSettlement", "resettle", "resettleLeaveRange")) {
            assertNotNull(find(AttendanceServiceImpl.class, name), "门面缺少重算入口: " + name);
        }
    }

    @Test
    @DisplayName("接口方法全部实现，且一个不少")
    void allInterfaceMethodsImplemented() {
        long methodCount = Arrays.stream(AttendanceService.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .count();
        assertEquals(EXPECTED_INTERFACE_METHODS, methodCount, "AttendanceService 接口方法数变化，请同步本用例");
        for (Method m : AttendanceService.class.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.isDefault()) {
                continue;
            }
            Method impl = find(AttendanceServiceImpl.class, m.getName());
            assertNotNull(impl, "门面未实现: " + m.getName());
            assertEquals(m.getParameterCount(), impl.getParameterCount(), m.getName() + " 参数个数不一致");
        }
    }

    private static Method find(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }
}
