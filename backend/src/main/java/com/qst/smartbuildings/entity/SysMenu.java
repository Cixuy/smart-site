package com.qst.smartbuildings.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单权限实体类
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    /** 菜单ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单ID（0表示根节点） */
    @TableField("parent_id")
    private Long parentId;

    /** 菜单名称 */
    @TableField("menu_name")
    private String menuName;

    /** 类型：1-目录，2-菜单，3-按钮 */
    @TableField("menu_type")
    private Integer menuType;

    /** 权限标识（如：device:add） */
    @TableField("permission_key")
    private String permissionKey;

    /** 路由地址 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 菜单图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 是否显示（1-显示，0-隐藏） */
    @TableField("is_visible")
    private Integer isVisible;

    /** 逻辑删除（0-正常，1-删除） */
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
