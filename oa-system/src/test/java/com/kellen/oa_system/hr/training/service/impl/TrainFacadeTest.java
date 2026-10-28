package com.kellen.oa_system.hr.training.service.impl;

import com.kellen.oa_system.hr.training.service.TrainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 培训模块拆分后的结构契约（BE-OPT-08）。
 *
 * <p>拆大类的风险不是「编译不过」，而是拆着拆着把两件事弄坏：</p>
 * <ol>
 *   <li><b>门面又开始直接查库</b>——一旦有人在门面里补一行 Mapper 调用，分层就退化了，
 *       而且这种退化没人会 review 出来；</li>
 *   <li><b>事务注解漂到内层</b>——内层类互相调用时注解不生效，出问题要逐处推理边界。</li>
 * </ol>
 *
 * <p>这两条都用反射钉死。纯反射、不启 Spring 上下文、不连库。</p>
 */
class TrainFacadeTest {

    /** 拆分前 TrainServiceImpl 的事务注解数：9（saveCourse/deleteCourse/saveSession/cancelSession/enroll/enrollBatch/cancel/checkin/score） */
    private static final int EXPECTED_FACADE_TRANSACTIONAL = 9;

    @Test
    @DisplayName("门面不直接使用 Mapper（分层契约）")
    void facadeHasNoMapperDependency() {
        List<String> offenders = new ArrayList<>();
        for (Field f : TrainServiceImpl.class.getDeclaredFields()) {
            if (f.getType().getSimpleName().endsWith("Mapper")) {
                offenders.add(f.getName() + ":" + f.getType().getSimpleName());
            }
        }
        assertTrue(offenders.isEmpty(), "门面不应直接依赖 Mapper，发现: " + offenders);
    }

    @Test
    @DisplayName("事务注解只留在门面，且数量与拆分前一致")
    void transactionAnnotationsStayOnFacade() {
        assertEquals(EXPECTED_FACADE_TRANSACTIONAL, countTransactional(TrainServiceImpl.class),
                "门面事务注解数与拆分前不一致，可能漏标或多标");
        for (Class<?> collaborator : List.of(TrainCourseService.class, TrainSessionService.class,
                TrainEnrollService.class, TrainEnrollQueryService.class, TrainReportService.class)) {
            assertEquals(0, countTransactional(collaborator),
                    collaborator.getSimpleName() + " 不应自带事务注解（会与门面事务叠加/自调用失效）");
        }
    }

    @Test
    @DisplayName("门面实现全部接口方法且方法体只做委派")
    void facadeImplementsAndOnlyDelegates() {
        // 编译期已保证实现完整，这里锁住「方法数不被悄悄删减」。
        // 过滤 synthetic/bridge：泛型返回值会在字节码里生成桥方法，不是接口方法
        long methodCount = java.util.Arrays.stream(TrainService.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .count();
        assertEquals(17, methodCount, "TrainService 接口方法数变化（原 17 个），请同步本用例");
        for (Method m : TrainServiceImpl.class.getDeclaredMethods()) {
            assertFalse(m.getName().startsWith("to"), "门面不应残留 VO 组装方法: " + m.getName());
        }
    }

    private int countTransactional(Class<?> type) {
        int count = 0;
        for (Method m : type.getDeclaredMethods()) {
            if (AnnotationUtils.findAnnotation(m, Transactional.class) != null) {
                count++;
            }
        }
        return count;
    }
}
