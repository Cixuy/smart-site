package com.qst.smartbuildings.service;

import com.qst.smartbuildings.common.AuthPrincipal;
import com.qst.smartbuildings.domain.User;
import com.qst.smartbuildings.dto.ChangePasswordDTO;
import com.qst.smartbuildings.dto.LoginDTO;
import com.qst.smartbuildings.dto.UserVO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录
     * @param loginDTO 登录请求（用户名 + 密码）
     * @return 用户信息 VO（含 token）
     */
    UserVO login(LoginDTO loginDTO);

    /**
     * 用户登出
     * @param token 当前登录 Token
     */
    void logout(String token);

    /**
     * 刷新令牌（使用当前 Token 换取新 AccessToken）
     * @param token 当前 Token
     * @return 用户信息 VO（含新 token）
     */
    UserVO refreshToken(String token);

    /**
     * 根据 Token 获取当前用户信息
     * @param token 登录 Token
     * @return 用户信息 VO（不含 token）
     */
    UserVO getUserInfo(String token);

    /**
     * 一次拦截：根据 Token 解析出完整的已认证主体（用户ID + 角色）。
     * 供 AuthInterceptor 在请求入口调用并写入 UserContext，业务层不再重复解析 token。
     * @param token 登录 Token
     * @return 已认证主体；token 无效时抛 NoLoginException
     */
    AuthPrincipal resolvePrincipal(String token);

    /**
     * 修改密码
     * @param token 当前登录 Token
     * @param dto 旧密码 + 新密码
     */
    void changePassword(String token, ChangePasswordDTO dto);

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户实体，未找到返回 null
     */
    User getByUsername(String username);
}
