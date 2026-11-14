package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.SysUser;
import com.qst.smartbuildings.domain.SysUserRole;
import com.qst.smartbuildings.entity.SysRole;
import com.qst.smartbuildings.dto.CreateUserRequest;
import com.qst.smartbuildings.dto.UpdateUserRequest;
import com.qst.smartbuildings.exception.UserManagementException;
import com.qst.smartbuildings.mapper.SysRoleMapper;
import com.qst.smartbuildings.mapper.SysUserMapper;
import com.qst.smartbuildings.mapper.SysUserRoleMapper;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.ResetPwdResult;
import com.qst.smartbuildings.vo.RoleOption;
import com.qst.smartbuildings.vo.UserRoleRow;
import com.qst.smartbuildings.vo.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserManagementService {
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public UserManagementService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    public PageResult<UserVO> page(long pageNum, long pageSize, String username, String realName, String phone, Integer status) {
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0)
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(realName), SysUser::getRealName, realName)
                .like(StringUtils.hasText(phone), SysUser::getPhone, phone)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime));
        Map<Long, List<RoleOption>> rolesByUser = rolesByUserId(page.getRecords().stream().map(SysUser::getId).toList());
        List<UserVO> records = page.getRecords().stream()
                .map(user -> toUserVo(user, rolesByUser.getOrDefault(user.getId(), Collections.emptyList())))
                .toList();
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    public UserVO detail(Long id) {
        SysUser user = requireActiveUser(id);
        return toUserVo(user, rolesByUserId(List.of(id)).getOrDefault(id, Collections.emptyList()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateUserRequest request) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername())) > 0) {
            throw new UserManagementException("账号已存在");
        }
        List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
        verifyRoles(roleIds);

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setLoginFailCount(0);
        user.setIsDeleted(0);
        // A password that has already expired makes the authentication module require a first-login change.
        user.setPwdExpireTime(LocalDateTime.now());
        userMapper.insert(user);
        replaceRoles(user.getId(), roleIds);
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateUserRequest request) {
        SysUser existing = requireActiveUser(request.getId());
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new UserManagementException("用户状态必须为0或1");
        }

        SysUser update = new SysUser();
        update.setId(existing.getId());
        update.setRealName(request.getRealName());
        update.setPhone(request.getPhone());
        update.setEmail(request.getEmail());
        update.setStatus(request.getStatus());
        userMapper.updateById(update);

        if (request.getRoleIds() != null) {
            List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
            verifyRoles(roleIds);
            replaceRoles(existing.getId(), roleIds);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, int status) {
        requireActiveUser(id);
        SysUser update = new SysUser();
        update.setId(id);
        if (status == 2) {
            update.setStatus(1);
            update.setLoginFailCount(0);
        } else {
            update.setStatus(status);
        }
        userMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResetPwdResult resetPassword(Long id) {
        requireActiveUser(id);
        String temporaryPassword = generateTemporaryPassword();
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(temporaryPassword));
        update.setPwdExpireTime(LocalDateTime.now());
        update.setLoginFailCount(0);
        userMapper.updateById(update);
        return new ResetPwdResult(temporaryPassword, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new UserManagementException("至少需要指定一个用户");
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new UserManagementException("用户ID不能为空");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, distinctIds)
                .eq(SysUser::getIsDeleted, 0));
        if (count != distinctIds.size()) {
            throw new UserManagementException("存在不存在或已删除的用户");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .in(SysUser::getId, distinctIds)
                .set(SysUser::getIsDeleted, 1)
                .set(SysUser::getStatus, 0));
    }

    private SysUser requireActiveUser(Long id) {
        if (id == null) {
            throw new UserManagementException("用户ID不能为空");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SysUser::getIsDeleted, 0));
        if (user == null) {
            throw new UserManagementException("用户不存在或已删除");
        }
        return user;
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private void verifyRoles(List<Long> roleIds) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getIsDeleted, 0));
        if (count != roleIds.size()) {
            throw new UserManagementException("角色不存在或已删除");
        }
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new UserManagementException("至少需要分配一个角色");
        }
        List<Long> result = roleIds.stream().filter(Objects::nonNull).collect(Collectors.collectingAndThen(
                Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        if (result.isEmpty()) {
            throw new UserManagementException("角色ID不能为空");
        }
        return result;
    }

    private Map<Long, List<RoleOption>> rolesByUserId(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<RoleOption>> result = new HashMap<>();
        for (UserRoleRow row : roleMapper.selectRolesByUserIds(userIds)) {
            result.computeIfAbsent(row.getUserId(), ignored -> new java.util.ArrayList<>())
                    .add(new RoleOption(row.getRoleId(), row.getRoleName(), row.getRoleCode()));
        }
        return result;
    }

    private UserVO toUserVo(SysUser user, List<RoleOption> roles) {
        UserVO result = new UserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        result.setPhone(user.getPhone());
        result.setEmail(user.getEmail());
        result.setStatus(user.getStatus());
        result.setLastLoginTime(user.getLastLoginTime());
        result.setPwdExpireTime(user.getPwdExpireTime());
        result.setLoginFailCount(user.getLoginFailCount());
        result.setCreateTime(user.getCreateTime());
        result.setRoles(roles);
        return result;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            password.append(PASSWORD_ALPHABET.charAt(secureRandom.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
