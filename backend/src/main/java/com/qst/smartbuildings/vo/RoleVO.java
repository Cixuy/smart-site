package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleVO {
    private Long id;
    private String roleCode;
    private String roleName;
    private Integer dataScope;
    private Integer sort;
    private String remark;
    private Integer isSystem;
    private LocalDateTime createTime;
}
