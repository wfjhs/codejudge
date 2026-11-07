package com.codejudge.web.config;

import com.codejudge.common.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 基础 Bean 装配。
 * <p>
 * 这里只用了 spring-security-crypto 的 BCrypt 实现，没有引入 Spring Security 的过滤器链——
 * 鉴权由自己写的 JwtInterceptor 完成，链路更短、更容易讲清楚。
 */
@Configuration
public class AppConfig {

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties.getSecret(), properties.getExpire());
    }

    /**
     * BCrypt 强度 10：单次加密约 60-80ms，是安全性与登录耗时的常见折中。
     * 强度越高越难暴力破解，但登录接口的 CPU 开销也越大。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
