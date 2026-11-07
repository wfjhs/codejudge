package com.codejudge.common.constant;

/**
 * 认证相关常量。
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** 携带 token 的请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 普通用户 */
    public static final int ROLE_USER = 0;

    /** 管理员 */
    public static final int ROLE_ADMIN = 1;
}
