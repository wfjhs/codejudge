package com.codejudge.common.result;

import lombok.Getter;

/**
 * 统一响应码。
 * <p>
 * 约定：
 * <ul>
 *     <li>0 表示成功</li>
 *     <li>4xx / 5xx 沿用 HTTP 语义，便于前端统一拦截</li>
 *     <li>1xxx 起为业务错误码，按模块分段：1xxx 用户，2xxx 题目，3xxx 提交</li>
 * </ul>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),

    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录状态已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    ERROR(500, "系统繁忙，请稍后重试"),

    // ===== 用户模块 1xxx =====
    USERNAME_EXISTS(1001, "用户名已被注册"),
    LOGIN_FAILED(1002, "用户名或密码错误"),
    USER_NOT_FOUND(1003, "用户不存在"),
    TOKEN_INVALID(1004, "登录凭证无效"),
    TOKEN_EXPIRED(1005, "登录已过期，请重新登录"),

    // ===== 题目模块 2xxx =====
    PROBLEM_NOT_FOUND(2001, "题目不存在"),

    // ===== 提交模块 3xxx =====
    SUBMIT_TOO_FREQUENT(3001, "提交过于频繁，请稍后再试"),
    SUBMISSION_NOT_FOUND(3002, "提交记录不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
