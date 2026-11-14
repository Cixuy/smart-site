package com.qst.smartbuildings.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    @com.baomidou.mybatisplus.annotation.TableField("role_id")
    private Long roleId;

    @com.baomidou.mybatisplus.annotation.TableField("menu_id")
    private Long menuId;
}
