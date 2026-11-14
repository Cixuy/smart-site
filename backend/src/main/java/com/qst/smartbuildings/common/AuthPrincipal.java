package com.qst.smartbuildings.common;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 已认证主体。
 * <p>由 {@code AuthInterceptor} 在请求入口一次性完成 {@code token -> 用户ID + 角色} 的解析后写入
 * {@link UserContext}，下游业务层（如 SprayServiceImpl）直接读取，不再自行解析 token / 查询 Redis / 查库。</p>
 */
public class AuthPrincipal {

    private Long userId;
    private String username;
    private String realName;
    private Set<String> roles = new HashSet<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Set<String> getRoles() {
        return roles == null ? Collections.emptySet() : roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles == null ? new HashSet<>() : roles;
    }
}
