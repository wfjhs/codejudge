package com.codejudge.common.context;

/**
 * 当前登录用户（不可变）。
 *
 * @param id       用户 ID
 * @param username 用户名
 * @param role     0-普通用户 1-管理员
 */
public record CurrentUser(Long id, String username, Integer role) {

    public boolean isAdmin() {
        return role != null && role == 1;
    }
}
