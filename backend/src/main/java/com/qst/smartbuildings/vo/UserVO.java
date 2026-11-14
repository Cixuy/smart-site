package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime pwdExpireTime;
    private Integer loginFailCount;
    private LocalDateTime createTime;
    private List<RoleOption> roles;
}
