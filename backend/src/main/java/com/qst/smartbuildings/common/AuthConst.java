package com.qst.smartbuildings.common;

public class AuthConst {
    //请求中的token名 (文档约定：Authorization: Bearer {accessToken})
    public static final String TOKEN_NAME = "Authorization";
    // Bearer 前缀
    public static final String BEARER_PREFIX = "Bearer ";
    //默认的content-type
    public static final String DEFAULT_CONTENT_TYPE = "application/json;charset=UTF-8";
    //    Redis中的token前缀
    public static final String USER_TOKEN_HEAD = "user_token_";
    //    登录过期时间
    public static final Integer TOKEN_EXPIRED_TIME = 60*60*24*7;
    // 放行接口（去掉 context-path /api 后的路径，由 AuthInterceptor 匹配）
    public static final String[] OPEN_URLS = new String[]{
            "/auth/login",          // 登录接口
            "/auth/logout",         // 登出接口
            "/auth/refreshToken",   // 刷新令牌接口
            "/captcha",             // 验证码接口（如果有）
            "/public/**",           // 其他公开接口（支持通配符）
            "/ai_image/**",         // ai图片接口
            "/workbench/notification/stream", // 通知SSE流（EventSource无法携带请求头，需放行）
            "/ai_image/**",         // ai图片接口
            "/ws/**"                // WebSocket 握手接口
    };
}
