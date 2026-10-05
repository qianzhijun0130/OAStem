package com.kellen.oa_system.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注「该查询受数据权限约束」。
 *
 * <p>被标注的方法在进入时会由 {@code DataScopeAspect} 计算当前登录用户的数据范围
 * 并放入线程上下文；方法内部再通过 {@code DataScopeSupport#applyTo} 把条件拼进
 * 查询包装器。之所以不直接由切面改写查询条件，是因为待拼装的
 * {@code LambdaQueryWrapper} 通常是方法内部的局部变量，切面无法获取——
 * 强行实现只能退化为改写 SQL 的隐式魔法，与本项目「不引入隐式行为」的约定冲突。</p>
 *
 * <p>用法：</p>
 * <pre>
 * &#64;DataScope
 * public PageResult&lt;UserVO&gt; pageUsers(UserPageQuery query) {
 *     LambdaQueryWrapper&lt;User&gt; wrapper = new LambdaQueryWrapper&lt;&gt;();
 *     // ... 其余条件
 *     dataScopeSupport.applyTo(wrapper, User::getDeptId, User::getId);
 *     // ...
 * }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
}
