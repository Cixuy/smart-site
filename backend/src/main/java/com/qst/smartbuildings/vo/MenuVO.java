package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单平铺列表视图对象
 */
@Data
@Schema(description = "菜单列表项")
public class MenuVO {

    @Schema(description = "菜单ID")
    private Long id;

    @Schema(description = "父菜单ID")
    private Long parentId;

    @Schema(description = "菜单名称")
    private String menuName;

    @Schema(description = "类型：1-目录，2-菜单，3-按钮")
    private Integer menuType;

    @Schema(description = "权限标识（如：device:add）")
    private String permissionKey;

    @Schema(description = "路由地址")
    private String path;

    @Schema(description = "组件路径")
    private String component;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否显示（1-显示，0-隐藏）")
    private Integer isVisible;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
