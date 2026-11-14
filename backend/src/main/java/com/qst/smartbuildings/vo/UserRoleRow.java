package com.qst.smartbuildings.vo;

import lombok.Data;

@Data
public class UserRoleRow {
    private Long userId;
    private Long roleId;
    private String roleName;
    private String roleCode;
}
