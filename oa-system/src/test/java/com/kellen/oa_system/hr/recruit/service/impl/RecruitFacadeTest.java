package com.kellen.oa_system.hr.recruit.service.impl;

import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.recruit.service.RecruitService;
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
 * 招聘模块拆分后的结构契约（BE-OPT-08 第 5/6）。
 *
 * <p>招聘的特殊之处：<b>它本来就没有 {@code @DataScope}，也没有只读事务</b>。
 * 这条「什么都没有」必须被显式断言住——否则后人看到薪酬/考勤都标了，
 * 顺手在这里也标一个 {@code @DataScope}，就会让招聘数据突然按部门收敛，
 * 而 HR 之间共享候选人库是本模块的既定业务前提。</p>
 *
 * <p>纯反射、不启 Spring 上下文、不连库。</p>
 */
class RecruitFacadeTest {

    /** 拆分前：15 处写事务，全部带 rollbackFor；没有一处只读事务 */
    private static final int EXPECTED_TRANSACTIONAL = 15;

    private static final int EXPECTED_INTERFACE_METHODS = 26;

    @Test
    @DisplayName("门面不直接使用 Mapper")
    void facadeHasNoMapperDependency() {
        List<String> offenders = new ArrayList<>();
        for (Field f : RecruitServiceImpl.class.getDeclaredFields()) {
            if (f.getType().getSimpleName().endsWith("Mapper")) {
                offenders.add(f.getName());
            }
        }
        assertTrue(offenders.isEmpty(), "门面不应直接依赖 Mapper，发现: " + offenders);
    }

    @Test
    @DisplayName("事务注解只在门面：15 处写事务，且都不是只读")
    void transactionAnnotationsStayOnFacade() {
        int total = 0;
        for (Method m : RecruitServiceImpl.class.getDeclaredMethods()) {
            Transactional txn = AnnotationUtils.findAnnotation(m, Transactional.class);
            if (txn == null) {
                continue;
            }
            total++;
            assertTrue(!txn.readOnly(), m.getName() + " 不应是只读事务（招聘无只读事务标注）");
        }
        assertEquals(EXPECTED_TRANSACTIONAL, total, "门面事务注解数变化，请确认是否符合预期");

        for (Class<?> collaborator : List.of(RecruitDemandService.class, RecruitPostService.class,
                RecruitCandidateService.class, RecruitInterviewService.class,
                RecruitOfferService.class, RecruitBoardService.class)) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertTrue(AnnotationUtils.findAnnotation(m, Transactional.class) == null,
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应自带事务注解");
            }
        }
    }

    @Test
    @DisplayName("招聘模块不使用 @DataScope（HR 共享候选人库，不按部门隔离）")
    void noDataScopeAnywhere() {
        for (Method m : RecruitServiceImpl.class.getDeclaredMethods()) {
            assertNull(AnnotationUtils.findAnnotation(m, DataScope.class),
                    "招聘门面不应标 @DataScope：" + m.getName()
                            + "（若确要按部门收敛，需整条链路改造并同步本用例）");
        }
        for (Class<?> collaborator : List.of(RecruitDemandService.class, RecruitCandidateService.class,
                RecruitOfferService.class, RecruitBoardService.class)) {
            for (Method m : collaborator.getDeclaredMethods()) {
                assertNull(AnnotationUtils.findAnnotation(m, DataScope.class),
                        collaborator.getSimpleName() + "#" + m.getName() + " 不应标 @DataScope");
            }
        }
    }

    @Test
    @DisplayName("接口方法全部实现，且一个不少")
    void allInterfaceMethodsImplemented() {
        long methodCount = Arrays.stream(RecruitService.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .count();
        assertEquals(EXPECTED_INTERFACE_METHODS, methodCount, "RecruitService 接口方法数变化，请同步本用例");
        for (Method m : RecruitService.class.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.isDefault()) {
                continue;
            }
            Method impl = null;
            for (Method candidate : RecruitServiceImpl.class.getDeclaredMethods()) {
                if (candidate.getName().equals(m.getName())) {
                    impl = candidate;
                }
            }
            assertNotNull(impl, "门面未实现: " + m.getName());
            assertEquals(m.getParameterCount(), impl.getParameterCount(), m.getName() + " 参数个数不一致");
        }
    }
}
