package com.codejudge.common.util;

import com.codejudge.common.context.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具。
 * <p>
 * 刻意做成普通 Java 类而不是 Spring Bean —— 这样 cj-common 不依赖 Spring，
 * 在 cj-web 里通过 @Bean 装配。分层更干净，也方便写单元测试。
 * <p>
 * 采用 HS256 对称签名：单体应用无需非对称；密钥由配置注入，长度必须 &gt;= 32 字节。
 */
public class JwtUtil {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtUtil(String secret, long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /**
     * 签发 token。subject 存用户 ID，另外带上 username 和 role，
     * 这样鉴权时无需再查库（用空间换时间，代价是角色变更要等 token 过期才生效）。
     */
    public String generate(Long userId, String username, Integer role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                // 显式指定 HS256：不指定的话 jjwt 会按密钥长度自动升级算法，
                // 一旦密钥长度变化，算法跟着变，多实例部署时可能校验失败
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 token 并还原为用户上下文。
     *
     * @throws io.jsonwebtoken.ExpiredJwtException token 过期
     * @throws JwtException                        签名错误或格式非法
     */
    public CurrentUser parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new CurrentUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", Integer.class)
        );
    }

    /**
     * 判断 token 是否仍然有效（不抛异常版本，供非鉴权场景使用）。
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
