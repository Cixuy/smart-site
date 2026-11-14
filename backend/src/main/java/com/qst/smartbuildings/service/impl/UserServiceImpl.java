package com.qst.smartbuildings.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qst.smartbuildings.common.AuthConst;
import com.qst.smartbuildings.common.AuthPrincipal;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.exception.NoLoginException;
import com.qst.smartbuildings.domain.User;
import com.qst.smartbuildings.dto.ChangePasswordDTO;
import com.qst.smartbuildings.dto.LoginDTO;
import com.qst.smartbuildings.dto.UserVO;
import com.qst.smartbuildings.mapper.SysRoleMapper;
import com.qst.smartbuildings.mapper.UserMapper;
import com.qst.smartbuildings.service.LogService;
import com.qst.smartbuildings.service.UserService;
import com.qst.smartbuildings.util.ClientInfoUtil;
import com.qst.smartbuildings.util.RedisClient;
import com.qst.smartbuildings.util.TokenUtil;
import com.qst.smartbuildings.util.WebContextUtil;
import com.qst.smartbuildings.vo.UserRoleRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private LogService logService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public UserVO login(LoginDTO loginDTO) {
        String username = null;
        if (loginDTO != null && StringUtils.hasText(loginDTO.getUsername())) {
            username = loginDTO.getUsername().trim();
        }
        try {
            // 1. 参数校验
            if (!StringUtils.hasText(username)) {
                throw new RuntimeException("请输入用户名");
            }
            if (!StringUtils.hasText(loginDTO.getPassword())) {
                throw new RuntimeException("请输入密码");
            }

            // 2. 查询用户
            User user = getByUsername(username);
            if (user == null) {
                throw new RuntimeException("账号或密码错误");
            }

            // 3. 校验密码
            boolean pwdOk = passwordEncoder.matches(loginDTO.getPassword(), user.getPassword());
            if (!pwdOk) {
                throw new RuntimeException("账号或密码错误");
            }

            // 4. 校验账号状态
            if (user.getStatus() == null || user.getStatus() == 0) {
                throw new RuntimeException("账户被禁用!");
            }
            if (user.getStatus() == 2) {
                throw new LockedAccountException("账户已锁定，请联系管理员!");
            }

            // 5. 生成 Token 并存入 Redis
            String token = createAndStoreToken(user);

            // 6. 更新最后登录时间
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getLastLoginTime, LocalDateTime.now()));

            // 7. 记录登录成功日志
            recordLoginLog(username, 0, "登录成功");

            // 8. 构建返回 VO
            UserVO userVO = UserVO.fromUser(user);
            userVO.setToken(token);
            return userVO;
        } catch (RuntimeException e) {
            // 记录登录失败日志：锁定账号记 2，其余记 1
            int status = e instanceof LockedAccountException ? 2 : 1;
            recordLoginLog(username, status, e.getMessage());
            throw e;
        }
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            String redisKey = AuthConst.USER_TOKEN_HEAD + token;
            redisClient.delete(redisKey);
        }
    }

    @Override
    public UserVO refreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException(SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
        }

        // 从 Redis 获取当前用户信息
        String redisKey = AuthConst.USER_TOKEN_HEAD + token;
        Object cached = redisClient.get(redisKey);
        if (cached == null) {
            throw new RuntimeException(SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
        }

        // 解析用户信息
        User user = JSON.parseObject(cached.toString(), User.class);
        if (user == null || user.getId() == null) {
            throw new RuntimeException("登录认证失败");
        }

        // 删除旧 Token
        redisClient.delete(redisKey);

        // 生成新 Token
        String newToken = createAndStoreToken(user);

        UserVO userVO = UserVO.fromUser(user);
        userVO.setToken(newToken);
        return userVO;
    }

    @Override
    public UserVO getUserInfo(String token) {
        if (!StringUtils.hasText(token)) {
            throw new NoLoginException(SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
        }

        String redisKey = AuthConst.USER_TOKEN_HEAD + token;
        Object cached = redisClient.get(redisKey);
        if (cached == null) {
            throw new NoLoginException(SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
        }

        User user = JSON.parseObject(cached.toString(), User.class);
        if (user == null) {
            throw new NoLoginException("登录认证失败");
        }

        return UserVO.fromUser(user);
    }

    @Override
    public AuthPrincipal resolvePrincipal(String token) {
        UserVO vo = getUserInfo(token); // token 无效时抛 NoLoginException
        Set<String> roles = new HashSet<>();
        List<UserRoleRow> rows = roleMapper.selectRolesByUserIds(Collections.singletonList(vo.getId()));
        if (rows != null) {
            for (UserRoleRow r : rows) {
                if (StringUtils.hasText(r.getRoleCode())) {
                    roles.add(r.getRoleCode().toUpperCase());
                }
                if (StringUtils.hasText(r.getRoleName())) {
                    roles.add(r.getRoleName());
                }
            }
        }
        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserId(vo.getId());
        principal.setUsername(vo.getUsername());
        principal.setRealName(vo.getRealName());
        principal.setRoles(roles);
        return principal;
    }

    @Override
    public void changePassword(String token, ChangePasswordDTO dto) {
        // 1. 参数校验
        if (dto == null || !StringUtils.hasText(dto.getOldPassword())) {
            throw new RuntimeException("请输入旧密码");
        }
        if (!StringUtils.hasText(dto.getNewPassword())) {
            throw new RuntimeException("请输入新密码");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        // 2. 通过 Token 获取当前用户
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException(SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
        }
        String redisKey = AuthConst.USER_TOKEN_HEAD + token;
        Object cached = redisClient.get(redisKey);
        if (cached == null) {
            throw new RuntimeException(SystemCodeEnum.SYSTEM_NOT_LOGIN.getMsg());
        }
        User user = JSON.parseObject(cached.toString(), User.class);
        if (user == null || user.getId() == null) {
            throw new RuntimeException("登录认证失败");
        }

        // 3. 校验旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // 4. 加密新密码并更新数据库
        String newEncodedPwd = passwordEncoder.encode(dto.getNewPassword());
        user.setPassword(newEncodedPwd);
        userMapper.updateById(user);

        // 5. 删除当前 Token，强制重新登录
        redisClient.delete(redisKey);
    }

    @Override
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    // ==================== 私有方法 ====================

    /**
     * 记录登录日志（IP/浏览器/操作系统取自当前请求上下文）
     */
    private void recordLoginLog(String username, int status, String msg) {
        try {
            var request = WebContextUtil.getRequest();
            logService.recordLoginLog(username == null ? "" : username,
                    ClientInfoUtil.getClientIp(request),
                    ClientInfoUtil.getBrowser(request),
                    ClientInfoUtil.getOs(request),
                    status, msg);
        } catch (Exception ignored) {
            // 日志记录失败不应影响登录流程
        }
    }

    /**
     * 登录日志专用异常：账号锁定
     */
    private static class LockedAccountException extends RuntimeException {
        LockedAccountException(String message) {
            super(message);
        }
    }

    /**
     * 生成 Token 并存入 Redis
     */
    private String createAndStoreToken(User user) {
        String token = TokenUtil.generateToken();
        String redisKey = AuthConst.USER_TOKEN_HEAD + token;
        // 将用户信息 JSON 存入 Redis，设置过期时间
        redisClient.set(redisKey, JSON.toJSONString(user), AuthConst.TOKEN_EXPIRED_TIME, TimeUnit.SECONDS);
        return token;
    }
}
