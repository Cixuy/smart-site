package com.qst.smartbuildings.common;

/**
 * 当前请求已认证主体上下文（基于 ThreadLocal）。
 * <p>由 {@code AuthInterceptor#preHandle} 写入、{@code AuthInterceptor#afterCompletion} 清理，
 * 业务层通过 {@link #get()} / {@link #getUserId()} / {@link #hasRole(String)} 直接读取，
 * 避免在每个业务类里重复解析 token。</p>
 */
public class UserContext {

    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthPrincipal get() {
        return CURRENT.get();
    }

    /** 请求结束时必须调用，防止 Tomcat 线程池串号。 */
    public static void clear() {
        CURRENT.remove();
    }

    public static Long getUserId() {
        AuthPrincipal p = CURRENT.get();
        return p == null ? null : p.getUserId();
    }

    public static boolean hasRole(String role) {
        AuthPrincipal p = CURRENT.get();
        if (p == null) {
            return false;
        }
        return p.getRoles().contains(role);
    }
}
