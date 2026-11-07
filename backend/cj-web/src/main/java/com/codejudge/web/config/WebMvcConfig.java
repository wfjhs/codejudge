package com.codejudge.web.config;

import com.codejudge.web.interceptor.AdminInterceptor;
import com.codejudge.web.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：拦截器注册 + 跨域。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 顺序很重要：JwtInterceptor 先执行，把用户写进 UserContext，
        // AdminInterceptor 才能读到当前用户的角色。order 越小越先执行。
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 登录、注册本身不需要 token
                .excludePathPatterns(
                        "/api/auth/**",
                        "/error"
                )
                .order(1);

        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/**")
                .order(2);
    }

    /**
     * 跨域白名单。
     * <p>
     * 本地开发前端走 Vite 的 proxy 其实已经绕开了跨域，这里配白名单是为了方便
     * 用 Postman / IDEA HTTP Client 直连，以及偶尔绕过 proxy 调试。
     * <p>
     * 生产环境由 Nginx 同域转发，不需要这段配置。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
