package com.codejudge.web.interceptor;

import com.codejudge.common.constant.AuthConstants;
import com.codejudge.common.context.UserContext;
import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.ResultCode;
import com.codejudge.common.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 * <p>
 * 只做两件事：解析 token 写入 UserContext、请求结束后清理。
 * 权限校验（是否管理员）交给业务层或单独的注解，不在这里堆 if。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器的 CORS 预检请求不带 token，必须放行，否则所有跨域请求都会 401
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(AuthConstants.TOKEN_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(AuthConstants.TOKEN_PREFIX)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        String token = header.substring(AuthConstants.TOKEN_PREFIX.length());

        try {
            UserContext.set(jwtUtil.parse(token));
        } catch (ExpiredJwtException e) {
            // 过期和非法分开返回：前端可以据 1005 静默跳登录页，1004 则提示重新登录
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理：Tomcat 线程是复用的，不清理会导致下一个请求读到上一个用户
        UserContext.clear();
    }
}
