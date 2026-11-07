package com.codejudge.web.interceptor;

import com.codejudge.common.annotation.RequireAdmin;
import com.codejudge.common.context.CurrentUser;
import com.codejudge.common.context.UserContext;
import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器。
 * <p>
 * 读取 {@link RequireAdmin} 注解（方法级优先，其次类级），校验当前用户是否为管理员。
 * <p>
 * <b>必须注册在 JwtInterceptor 之后</b>：依赖 JwtInterceptor 已经把用户写入 UserContext。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检不带 token，此时 UserContext 是空的，直接放行避免误报 401
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 非 Controller 方法（静态资源、错误页等）不需要鉴权
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法上的注解优先于类上的：这样类上标了 @RequireAdmin，
        // 个别方法也可以用更细的规则覆盖（目前只实现了"需要管理员"一种）
        RequireAdmin annotation = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        }
        if (annotation == null) {
            return true;
        }

        // require() 在未登录时抛 401；这里再判断角色
        CurrentUser user = UserContext.require();
        if (!user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return true;
    }
}
