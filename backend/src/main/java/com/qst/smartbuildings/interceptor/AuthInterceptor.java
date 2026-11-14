package com.qst.smartbuildings.interceptor;

import com.alibaba.fastjson2.JSON;
import com.qst.smartbuildings.common.AuthConst;
import com.qst.smartbuildings.common.AuthPrincipal;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.common.UserContext;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.SysMenuService;
import com.qst.smartbuildings.service.UserService;
import com.qst.smartbuildings.util.RedisClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * 登录认证拦截器
 * <p>
 * 校验请求头中的 Authorization: Bearer {accessToken}，从 Redis 中获取对应登录用户信息。
 * 放行规则：
 * 1. OPTIONS 预检请求 → 直接放行
 * 2. AuthConst.OPEN_URLS 中配置的放行路径 → 直接放行
 * 3. 额外放行 /public/**（对应 OPEN_URLS 中的 /api/public/**，去 context-path 后匹配）
 * 4. 其他请求 → 校验 Token
 * <p>
 * 菜单权限拦截：白名单放行后，循环遍历 sys_menu.path 判断当前请求是否命中某条菜单路径
 * （由 {@link SysMenuService} 提供，避免在拦截器中直接依赖 Mapper）。若命中，则在 Token
 * 校验通过后，还需校验当前用户是否拥有该菜单（菜单ID），否则拒绝访问。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private UserService userService;

    @Autowired
    private SysMenuService sysMenuService;

    @Value("${auth.enabled:true}")
    private boolean authEnabled;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 1. 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 未启用认证时，注入系统管理员主体后直接放行（便于免登录联调）
        if (!authEnabled) {
            UserContext.set(mockPrincipal());
            return true;
        }

        // 3. 获取请求路径（去掉 context-path）
        String requestURI = request.getServletPath();
        if (!StringUtils.hasText(requestURI)) {
            requestURI = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (StringUtils.hasText(contextPath) && requestURI.startsWith(contextPath)) {
                requestURI = requestURI.substring(contextPath.length());
            }
        }

        // 4. 放行白名单路径（AuthConst.OPEN_URLS）
        for (String openUrl : AuthConst.OPEN_URLS) {
            if (pathMatcher.match(openUrl, requestURI)) {
                return true;
            }
        }

        // 4.1 循环遍历 sys_menu.path：判断当前请求是否命中某条菜单路径，决定是否需要菜单权限拦截
        List<Long> matchedMenuIds = sysMenuService.matchedMenuIds(requestURI);
        boolean menuProtected = !matchedMenuIds.isEmpty();

        // 5. 校验 Token（Authorization: Bearer {accessToken}）
        String token = request.getHeader(AuthConst.TOKEN_NAME);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
            return false;
        }
        // 去掉 Bearer 前缀
        if (token.startsWith(AuthConst.BEARER_PREFIX)) {
            token = token.substring(AuthConst.BEARER_PREFIX.length());
        }

        // 6. 从 Redis 中查询 Token
        String redisKey = AuthConst.USER_TOKEN_HEAD + token;
        Object cached = redisClient.get(redisKey);
        if (cached == null) {
            writeUnauthorized(response, SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
            return false;
        }

        // 7. Token 有效，解析主体并写入 UserContext（一次拦截，下游业务直接读取）
        AuthPrincipal principal = userService.resolvePrincipal(token);
        // 7.1 菜单权限拦截：请求命中某条菜单 path，但该用户未被分配该菜单 → 拒绝
        if (menuProtected && !sysMenuService.hasAnyMenuId(principal.getUserId(), matchedMenuIds)) {
            writeUnauthorized(response, SystemCodeEnum.SYSTEM_NO_AUTH.getMsg());
            return false;
        }
        UserContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束清理 ThreadLocal，防止 Tomcat 线程池复用导致用户串号
        UserContext.clear();
    }

    /**
     * 认证关闭时的系统管理员主体（拥有 SYSTEM_ADMIN 角色，可免登录联调需权限的接口）。
     */
    private AuthPrincipal mockPrincipal() {
        AuthPrincipal p = new AuthPrincipal();
        p.setUserId(1L);
        p.setUsername("admin");
        p.setRealName("系统管理员");
        p.setRoles(java.util.Set.of("SYSTEM_ADMIN"));
        return p;
    }

    /**
     * 返回未认证的 JSON 响应
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(JSON.toJSONString(
                Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN, msg)
        ));
    }
}
