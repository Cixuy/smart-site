package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.qst.smartbuildings.domain.SysUser;
import com.qst.smartbuildings.domain.SysUserRole;
import com.qst.smartbuildings.dto.CreateUserRequest;
import com.qst.smartbuildings.mapper.SysRoleMapper;
import com.qst.smartbuildings.mapper.SysUserMapper;
import com.qst.smartbuildings.mapper.SysUserRoleMapper;
import com.qst.smartbuildings.vo.ResetPwdResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @InjectMocks
    private UserManagementService service;

    @Test
    void createHashesPasswordAndCreatesRoleRelations() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(roleMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(101L);
            return 1;
        });

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("admin101");
        request.setPassword("Password1!");
        request.setRealName("张三");
        request.setPhone("13800138000");
        request.setRoleIds(List.of(1L));

        Long id = service.create(request);

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(id).isEqualTo(101L);
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(request.getPassword());
        assertThat(new BCryptPasswordEncoder().matches(request.getPassword(), userCaptor.getValue().getPassword())).isTrue();
        assertThat(userCaptor.getValue().getPwdExpireTime()).isNotNull();
        verify(userRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    void resetPasswordReturnsSixCharacterTemporaryPasswordAndForcesChange() {
        SysUser user = new SysUser();
        user.setId(101L);
        user.setIsDeleted(0);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        ResetPwdResult result = service.resetPassword(101L);

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(result.isForceChange()).isTrue();
        assertThat(result.getNewPassword()).hasSize(6);
        assertThat(new BCryptPasswordEncoder().matches(result.getNewPassword(), userCaptor.getValue().getPassword())).isTrue();
        assertThat(userCaptor.getValue().getPwdExpireTime()).isNotNull();
    }
}
