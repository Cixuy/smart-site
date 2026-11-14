package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增菜单请求参数
 */
@Schema(description = "新增菜单请求参数")
public record MenuCreateRequest(
        @Schema(description = "父菜单ID(0为根)")
        Long parentId,
        @Schema(description = "菜单名称")
        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 50, message = "菜单名称长度不能超过50位")
        String menuName,
        @Schema(description = "类型：1-目录，2-菜单，3-按钮")
        @NotNull(message = "菜单类型不能为空")
        @Min(value = 1, message = "菜单类型必须为1、2或3")
        @Max(value = 3, message = "菜单类型必须为1、2或3")
        Integer menuType,
        @Schema(description = "权限标识(如device:add)")
        @Size(max = 100, message = "权限标识长度不能超过100位")
        String permissionKey,
        @Schema(description = "路由地址")
        @Size(max = 200, message = "路由地址长度不能超过200位")
        String path,
        @Schema(description = "组件路径")
        @Size(max = 255, message = "组件路径长度不能超过255位")
        String component,
        @Schema(description = "菜单图标")
        @Size(max = 100, message = "菜单图标长度不能超过100位")
        String icon,
        @Schema(description = "排序")
        Integer sort,
        @Schema(description = "是否显示：1-显示，0-隐藏")
        @Min(value = 0, message = "是否显示必须为0或1")
        @Max(value = 1, message = "是否显示必须为0或1")
        Integer isVisible
) {
}
