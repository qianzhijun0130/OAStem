package com.kellen.oa_system.hr.salary.service.impl;

import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.salary.service.SalaryService;
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
 * 薪酬模块拆分后的结构契约（BE-OPT-08 第 3/6）。
 *
 * <p>薪酬比前两个模块多一条必须锁死的东西：<b>{@code @DataScope} 只能且必须在门面</b>。
 * 切面把可见范围写进 ThreadLocal，协作类读它判定是否要按用户过滤；
 * 注解一旦被挪到协作类或漏掉，{@code isUnrestricted()} 恒为 true，
 * 结果是任何持 {@code hr:salary:calc} 的账号都能看到全公司工资条——编译期、类型检查、构建全都发现不了。
 * 这正是 BE-OPT-02 修掉的越权缺口，所以这里用反射把它钉住。</p>
 *
 * <p>纯反射、不启 Spring 上下文、不连库。</p>
 */
class SalaryFacadeTest {

    /** 拆分前：7 处 readOnly（薪资项/政策/档案历史/列表/详情/我的工资条×2）+ 8 处 rollbackFor */
    private static final int EXPECTED_FACADE_TRANSACTIONAL = 15;

    private static final int EXPECTED_READ_ONLY = 7;

    /** 拆分前的接口方法数，防止拆分时悄悄少实现一个（编译能过，但前端会 404） */
    private static final int EXPECTED_INTERFACE_METHODS = 18;

    @Test
    @DisplayName("门面不直接使用 Mapper")
    void facadeHasNoMapperDependency() {
        List<String> offenders = new ArrayList<>();
        for (Field f : SalaryServiceImpl.class.getDeclaredFields()) {
            if (f.getType().getSimpleName().endsWith("Mapper")) {
                offenders.add(f.getName());
            }
        }
        assertTrue(offenders.isEmpty(), "门面不应直接依赖 Mapper，发现: " + offenders);
    }

    @Test
    @DisplayName("事务注解只在门面，数量与拆分前一致（8 读写 + 7 只读）")
    void transactionAnnotationsStayOnFacade() {
        int total = 0;
        int readOnly = 0;
        for (Method m : SalaryServiceImpl.class.getDeclaredMethods()) {
            Transactional txn = AnnotationUtils.findAnnotation(m, Transactional.class);
            if (txn == null) {
                continue;
            }
            total++;
            if (txn.readOnly()) {
                readOnly++;
            }
        }
        assertEquals(EXPECTED_FACADE_TRANSACTIONAL, total, "门面事务注解数变化，请确认是否符合预期");
        assertEquals(EXPECTED_READ_ONLY, readOnly, "只读事务数量变化（拆分前 7 处）");

        for (Class<?> collaborator : List.of(SalaryConfigService.class, SalaryProfileService.class,
                SalaryCalcService.class, SalaryRecordService.class, SalaryPayslipService.class)) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertTrue(AnnotationUtils.findAnnotation(m, Transactional.class) == null,
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应自带事务注解");
            }
        }
    }

    @Test
    @DisplayName("@DataScope 必须在门面 pageRecords（否则整块数据过滤被跳过）")
    void dataScopeStaysOnFacade() {
        Method target = null;
        for (Method m : SalaryServiceImpl.class.getDeclaredMethods()) {
            if (m.getName().equals("pageRecords")) {
                target = m;
            }
        }
        assertNotNull(target, "门面缺少方法: pageRecords");
        assertNotNull(AnnotationUtils.findAnnotation(target, DataScope.class),
                "pageRecords 必须保留 @DataScope：数据权限快照由切面在该方法上写入");

        // 反向：协作类不得重复标注（会让人误以为过滤发生在内层）
        for (Method m : SalaryRecordService.class.getDeclaredMethods()) {
            assertTrue(AnnotationUtils.findAnnotation(m, DataScope.class) == null,
                    "SalaryRecordService#" + m.getName() + " 不应再标 @DataScope");
        }
    }

    @Test
    @DisplayName("接口方法全部实现，且一个不少")
    void allInterfaceMethodsImplemented() {
        long methodCount = Arrays.stream(SalaryService.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .count();
        assertEquals(EXPECTED_INTERFACE_METHODS, methodCount, "SalaryService 接口方法数变化，请同步本用例");
        // implements 已由编译期保证，这里只防「少了一个方法但恰好编译通过」（默认方法）
        for (Method m : SalaryService.class.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.isDefault()) {
                continue;
            }
            boolean implemented = Arrays.stream(SalaryServiceImpl.class.getDeclaredMethods())
                    .anyMatch(impl -> impl.getName().equals(m.getName())
                            && impl.getParameterCount() == m.getParameterCount());
            assertTrue(implemented, "门面未实现: " + m.getName());
        }
    }
}
