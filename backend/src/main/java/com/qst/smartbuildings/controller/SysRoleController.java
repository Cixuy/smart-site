package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.dto.RoleCreateRequest;
import com.qst.smartbuildings.dto.RoleMenuRequest;
import com.qst.smartbuildings.dto.RoleUpdateRequest;
import com.qst.smartbuildings.annotation.OperLog;
import com.qst.smartbuildings.service.SysRoleService;
import com.qst.smartbuildings.vo.RoleOptionVO;
import com.qst.smartbuildings.vo.RolePageVO;
import com.qst.smartbuildings.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "角色管理", description = "角色增删改查、角色菜单权限分配接口")
@Validated
@RestController
@RequestMapping("/sys/role")
public class SysRoleController {
    private final SysRoleService roleService;

    public SysRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "分页查询角色列表", description = "支持按角色名称、角色编码模糊查询")
    @GetMapping("/page")
    public Result<RolePageVO> page(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "角色名称(模糊)") @RequestParam(required = false) String roleName,
            @Parameter(description = "角色编码(模糊)") @RequestParam(required = false) String roleCode) {
        return Result.ok(roleService.page(pageNum, pageSize, roleName, roleCode));
    }

    @Operation(summary = "查询所有角色", description = "用于用户分配角色时的下拉选择")
    @GetMapping("/all")
    public Result<Map<String, List<RoleOptionVO>>> all() {
        return Result.ok(Map.of("list", roleService.all()));
    }

    @Operation(summary = "查询角色详情", description = "返回角色基本信息")
    @GetMapping("/{id}")
    public Result<RoleVO> get(@Parameter(description = "角色ID") @PathVariable Long id) {
        return Result.ok(roleService.get(id));
    }

    @Operation(summary = "新增角色", description = "创建新的角色")
    @PostMapping
    @OperLog(title = "角色管理", operType = "新增")
    public Result<Void> add(@Valid @RequestBody RoleCreateRequest request) {
        roleService.add(request);
        return Result.ok();
    }

    @Operation(summary = "修改角色", description = "修改角色基本信息")
    @PutMapping
    @OperLog(title = "角色管理", operType = "修改")
    public Result<Void> update(@Valid @RequestBody RoleUpdateRequest request) {
        roleService.update(request);
        return Result.ok();
    }

    @Operation(summary = "删除角色(批量)", description = "逻辑删除。内置角色(is_system=1)不可删除")
    @DeleteMapping("/{ids}")
    @OperLog(title = "角色管理", operType = "删除")
    public Result<Void> delete(@Parameter(description = "角色ID, 多个用逗号分隔") @PathVariable String ids) {
        roleService.delete(ids);
        return Result.ok();
    }

    @Operation(summary = "查询角色已分配的菜单权限", description = "返回角色拥有的菜单ID集合")
    @GetMapping("/{id}/menus")
    public Result<Map<String, List<Long>>> getMenus(@Parameter(description = "角色ID") @PathVariable Long id) {
        return Result.ok(Map.of("menuIds", roleService.getMenuIds(id)));
    }

    @Operation(summary = "分配角色菜单权限", description = "全量覆盖该角色的菜单权限")
    @PutMapping("/{id}/menus")
    @OperLog(title = "角色管理", operType = "分配菜单")
    public Result<Void> assignMenus(@Parameter(description = "角色ID") @PathVariable Long id, @Valid @RequestBody RoleMenuRequest request) {
        roleService.assignMenus(id, request.menuIds());
        return Result.ok();
    }
}