package com.codejudge.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。
 * <p>
 * 对应 application.yml 中的 jwt.* 节点。
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥。HS256 要求不少于 32 字节（256 bit），否则 jjwt 启动即抛异常。
     */
    private String secret;

    /**
     * 过期时间（秒）。
     */
    private long expire = 86400;
}
