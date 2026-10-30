package com.kellen.oa_system.hr.performance.service.impl;

import com.kellen.oa_system.common.annotation.DataScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绩效模块拆分后的结构契约（BE-OPT-08）。
 *
 * <p>比培训那次多一条：<b>{@code @DataScope} 必须留在门面</b>。数据权限快照是切面写在
 * ThreadLocal 里的，协作类的 {@code visibleUserIds()} 依赖它——注解一旦被挪到别处或漏掉，
 * 运行时才会以「空快照 IllegalStateException」或更糟的「静默全量」暴露，编译期毫无提示。</p>
 *
 * <p>纯反射、不启 Spring 上下文、不连库。</p>
 */
class PerfFacadeTest {

    /**
     * 实测计数：9 处 rollbackFor（8 个原有写动作 + 结果导入）+ 9 处 readOnly
     * （8 个原有查询 + 导入模板下载）。BIZ-OPT-10 加了导入这对方法。
     */
    private static final int EXPECTED_FACADE_TRANSACTIONAL = 18;

    @Test
    @DisplayName("门面不直接使用 Mapper")
    void facadeHasNoMapperDependency() {
        List<String> offenders = new ArrayList<>();
        for (Field f : PerfScoreServiceImpl.class.getDeclaredFields()) {
            if (f.getType().getSimpleName().endsWith("Mapper")) {
                offenders.add(f.getName());
            }
        }
        assertTrue(offenders.isEmpty(), "门面不应直接依赖 Mapper，发现: " + offenders);
    }

    @Test
    @DisplayName("事务注解只在门面，数量与实测一致（9 读写 + 9 只读）")
    void transactionAnnotationsStayOnFacade() {
        int total = 0;
        int readOnly = 0;
        for (Method m : PerfScoreServiceImpl.class.getDeclaredMethods()) {
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
        assertEquals(9, readOnly, "只读事务数量变化（当前 9 处）");

        for (Class<?> collaborator : List.of(PerfTargetService.class, PerfScoreWriteService.class,
                PerfAppealService.class, PerfQueryService.class, PerfImportService.class,
                com.kellen.oa_system.hr.performance.support.PerfImportLoader.class,
                com.kellen.oa_system.hr.performance.support.PerfImportWriter.class)) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertTrue(AnnotationUtils.findAnnotation(m, Transactional.class) == null,
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应自带事务注解");
            }
        }
    }

    @Test
    @DisplayName("@DataScope 必须留在门面（否则数据范围快照为空）")
    void dataScopeStaysOnFacade() {
        for (String name : List.of("pageSheets", "pageTeam")) {
            Method target = null;
            for (Method m : PerfScoreServiceImpl.class.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    target = m;
                }
            }
            assertNotNull(target, "门面缺少方法: " + name);
            assertNotNull(AnnotationUtils.findAnnotation(target, DataScope.class),
                    name + " 必须保留 @DataScope：数据权限快照由切面在该方法上写入");
        }
    }
}
