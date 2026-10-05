package com.kellen.oa_system.system.aspect;

import com.kellen.oa_system.system.service.support.DataScopeSupport;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面。
 *
 * <p>拦截被 {@code @DataScope} 标注的方法：进入前计算当前用户的数据范围并放入上下文，
 * 退出时（含异常路径）清理，避免线程复用导致范围串号。</p>
 *
 * <p>只负责「准备上下文」，条件拼装由业务方法通过
 * {@link DataScopeSupport#applyTo} 显式完成——原因见 {@code DataScope} 注解说明。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DataScopeSupport dataScopeSupport;

    @Around("@annotation(com.kellen.oa_system.common.annotation.DataScope)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        dataScopeSupport.prepare();
        try {
            return joinPoint.proceed();
        } finally {
            dataScopeSupport.clear();
        }
    }
}
