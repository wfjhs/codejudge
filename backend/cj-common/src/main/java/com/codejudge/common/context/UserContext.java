package com.codejudge.common.context;

import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.ResultCode;

/**
 * 当前登录用户上下文。
 * <p>
 * 由 JwtInterceptor 在请求进入时写入，请求结束时<b>必须</b>清理，
 * 否则在线程复用（Tomcat 线程池）场景下会把上一个请求的用户身份带给下一个请求。
 * <p>
 * 注意：开启虚拟线程后，每个虚拟线程有独立的 ThreadLocal，但仍需在 finally 中 remove，
 * 因为虚拟线程对象可能被复用（例如通过 Thread.ofVirtual().factory() 的池化实现）。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前用户，未登录直接抛 401。
     */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
