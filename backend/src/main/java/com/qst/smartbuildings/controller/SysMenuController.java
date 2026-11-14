package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.dto.MenuCreateRequest;
import com.qst.smartbuildings.dto.MenuUpdateRequest;
import com.qst.smartbuildings.annotation.OperLog;
import com.qst.smartbuildings.service.SysMenuService;
import com.qst.smartbuildings.vo.MenuTreeVO;
import com.qst.smartbuildings.vo.MenuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * 菜单管理：查询菜单树
 */
@Tag(name = "菜单管理", description = "菜单树查询等接口")
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController {
    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @Operation(summary = "查询菜单树", description = "返回完整菜单树结构，用于角色权限分配和前端路由渲染")
    @GetMapping("/tree")
    public Result<Map<String, List<MenuTreeVO>>> tree() {
        return Result.ok(Map.of("tree", menuService.tree()));
    }

    @Operation(summary = "查询菜单列表(平铺)", description = "返回菜单平铺列表，支持按名称模糊和类型筛选")
    @GetMapping("/list")
    public Result<Map<String, List<MenuVO>>> list(
            @Parameter(description = "菜单名称（模糊）") @RequestParam(required = false) String menuName,
            @Parameter(description = "类型：1-目录，2-菜单，3-按钮") @RequestParam(required = false) Integer menuType) {
        return Result.ok(Map.of("list", menuService.list(menuName, menuType)));
    }

    @Operation(summary = "查询菜单详情", description = "根据菜单ID查询菜单详情")
    @GetMapping("/{id}")
    public Result<MenuVO> detail(@Parameter(description = "菜单ID") @PathVariable Long id) {
        return Result.ok(menuService.getDetailById(id));
    }

    @Operation(summary = "删除菜单", description = "逻辑删除，存在子菜单时不可删除")
    @DeleteMapping("/{id}")
    @OperLog(title = "菜单管理", operType = "删除")
    public Result<Void> delete(@Parameter(description = "菜单ID") @PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }


    @Operation(summary = "新增菜单", description = "新增一条菜单记录")
    @PostMapping
    @OperLog(title = "菜单管理", operType = "新增")
    public Result<Void> add(@RequestBody @Valid MenuCreateRequest req) {
        menuService.add(req);
        return Result.ok();
    }

    @Operation(summary = "修改菜单", description = "根据菜单ID修改菜单信息")
    @PutMapping
    @OperLog(title = "菜单管理", operType = "修改")
    public Result<Void> update(@RequestBody @Valid MenuUpdateRequest req) {
        menuService.update(req);
        return Result.ok();
    }
}


