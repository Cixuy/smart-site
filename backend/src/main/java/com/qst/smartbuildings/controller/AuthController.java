package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.common.AuthConst;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.dto.ChangePasswordDTO;
import com.qst.smartbuildings.dto.LoginDTO;
import com.qst.smartbuildings.dto.UserVO;
import com.qst.smartbuildings.annotation.OperLog;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理", description = "登录、登出、Token 刷新等认证接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 Token 和用户信息")
    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody LoginDTO loginDTO) {
        try {
            UserVO userVO = userService.login(loginDTO);
            return Result.ok(userVO);
        } catch (RuntimeException e) {
            return Result.error(SystemCodeEnum.SYSTEM_LOGIN_ERROR, e.getMessage());
        }
    }

    @Operation(summary = "用户登出", description = "使当前 Token 失效")
    @Parameter(in = ParameterIn.HEADER, name = "Authorization", required = true,
            description = "身份认证令牌。格式: Bearer {accessToken}")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        userService.logout(token);
        return Result.ok();
    }

    @Operation(summary = "刷新令牌", description = "使用当前 Token 换取新 AccessToken")
    @Parameter(in = ParameterIn.HEADER, name = "Authorization", required = true,
            description = "身份认证令牌。格式: Bearer {accessToken}")
    @PostMapping("/refreshToken")
    public Result<UserVO> refreshToken(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            UserVO userVO = userService.refreshToken(token);
            return Result.ok(userVO);
        } catch (RuntimeException e) {
            return Result.error(SystemCodeEnum.SYSTEM_LOGIN_ERROR, e.getMessage());
        }
    }

    @Operation(summary = "获取当前用户信息", description = "根据请求头中的 Token 返回当前登录用户信息（含角色+权限+菜单树）")
    @Parameter(in = ParameterIn.HEADER, name = "Authorization", required = true,
            description = "身份认证令牌。格式: Bearer {accessToken}")
    @GetMapping("/currentUser")
    public Result<UserVO> getCurrentUser(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            UserVO userVO = userService.getUserInfo(token);
            return Result.ok(userVO);
        } catch (RuntimeException e) {
            return Result.error(SystemCodeEnum.SYSTEM_LOGIN_ERROR, e.getMessage());
        }
    }

    @Operation(summary = "修改密码", description = "修改当前登录用户的密码，修改成功后需重新登录")
    @Parameter(in = ParameterIn.HEADER, name = "Authorization", required = true,
            description = "身份认证令牌。格式: Bearer {accessToken}")
    @PutMapping("/password")
    @OperLog(title = "安全认证", operType = "修改密码")
    public Result<Void> changePassword(HttpServletRequest request, @RequestBody ChangePasswordDTO dto) {
        try {
            String token = extractToken(request);
            userService.changePassword(token, dto);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.error(SystemCodeEnum.SYSTEM_LOGIN_ERROR, e.getMessage());
        }
    }

    /**
     * 从请求头提取 Token（去掉 Bearer 前缀）
     */
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader(AuthConst.TOKEN_NAME);
        if (token != null && token.startsWith(AuthConst.BEARER_PREFIX)) {
            token = token.substring(AuthConst.BEARER_PREFIX.length());
        }
        return token;
    }
}
