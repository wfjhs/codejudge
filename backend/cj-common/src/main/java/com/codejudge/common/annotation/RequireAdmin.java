package com.codejudge.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要管理员权限的接口。
 * <p>
 * 可以标在方法上，也可以标在类上（类上表示该 Controller 的所有接口都需要管理员）。
 * 由 {@code AdminInterceptor} 在 HandlerMethod 层面读取并校验。
 * <p>
 * <b>为什么用注解 + 拦截器，而不是 AOP 切面？</b>
 * <ul>
 *     <li>拦截器在 Spring MVC 的 DispatcherServlet 里就执行了，比 AOP 代理更早，
 *         权限不通过时连 Controller 方法都不会进入，也省掉一层代理开销</li>
 *     <li>拦截器能直接拿到 {@code HandlerMethod}，读取注解和 AOP 一样方便</li>
 *     <li>AOP 适合做"方法执行前后"的增强（日志、事务、限流），权限判断属于"能不能进",
 *         语义上更适合拦截器</li>
 * </ul>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
