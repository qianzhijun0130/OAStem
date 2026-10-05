package com.kellen.oa_system.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志埋点注解。
 *
 * <p>只在**关键写操作**上标注（新增/修改/删除/发布/审批等），由
 * {@code OperationLogAspect} 统一采集并落库。刻意不做全量拦截：
 * 查询类接口量大且无审计价值，全量记录只会把日志表冲成噪音。</p>
 *
 * <pre>{@code
 * @OperationLog(module = "公告管理", operation = "发布公告")
 * public Result<Void> publish(@PathVariable Long id) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 所属模块，用于日志分类与筛选，如「公告管理」「用户管理」 */
    String module();

    /**
     * 操作描述，用于说明这条日志做了什么。
     *
     * <p>支持 SpEL 占位符 {@code #{...}}，写法与 {@code @PreAuthorize} 一致，
     * 可直接引用方法参数名，如 {@code "删除公告 #{#id}"}。</p>
     */
    String operation();

    /** 是否记录请求参数。含密码/密钥的方法应显式置 false */
    boolean saveParams() default true;

    /** 是否记录响应结果。返回体可能很大，默认关闭 */
    boolean saveResult() default false;
}
