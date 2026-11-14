package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.dto.RoleCreateRequest;
import com.qst.smartbuildings.dto.RoleUpdateRequest;
import com.qst.smartbuildings.entity.SysRole;
import com.qst.smartbuildings.entity.SysRoleMenu;
import com.qst.smartbuildings.exception.RoleException;
import com.qst.smartbuildings.mapper.SysRoleMapper;
import com.qst.smartbuildings.mapper.SysRoleMenuMapper;
import com.qst.smartbuildings.vo.RoleOptionVO;
import com.qst.smartbuildings.vo.RolePageVO;
import com.qst.smartbuildings.vo.RoleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class SysRoleService {
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public SysRoleService(SysRoleMapper roleMapper, SysRoleMenuMapper roleMenuMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    public RolePageVO page(int pageNum, int pageSize, String roleName, String roleCode) {
        Page<SysRole> page = roleMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysRole>()
                        .like(roleName != null && !roleName.isBlank(), SysRole::getRoleName, roleName)
                        .like(roleCode != null && !roleCode.isBlank(), SysRole::getRoleCode, roleCode)
                        .orderByAsc(SysRole::getSort)
                        .orderByDesc(SysRole::getId));
        return new RolePageVO(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toVO).toList());
    }

    public List<RoleOptionVO> all() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .orderByAsc(SysRole::getSort))
                .stream().map(role -> new RoleOptionVO(role.getId(), role.getRoleName(), role.getRoleCode()))
                .toList();
    }

    public RoleVO get(Long id) {
        return toVO(find(id));
    }

    public void add(RoleCreateRequest request) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.roleCode())) > 0) {
            throw new RoleException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDataScope(request.dataScope() == null ? 1 : request.dataScope());
        role.setSort(request.sort() == null ? 0 : request.sort());
        role.setRemark(request.remark());
        role.setIsSystem(0);
        role.setIsDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        role.setCreateTime(now);
        role.setUpdateTime(now);
        roleMapper.insert(role);
    }

    public void update(RoleUpdateRequest request) {
        SysRole role = find(request.id());
        if (request.roleName() != null) role.setRoleName(request.roleName());
        if (request.dataScope() != null) role.setDataScope(request.dataScope());
        if (request.sort() != null) role.setSort(request.sort());
        if (request.remark() != null) role.setRemark(request.remark());
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(role);
    }

    @Transactional
    public void delete(String ids) {
        List<Long> roleIds = parseIds(ids);
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) throw new RoleException("角色不存在");
        if (roles.stream().anyMatch(role -> Objects.equals(role.getIsSystem(), 1))) {
            throw new RoleException("内置角色不可删除");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds));
        roleMapper.update(null, new LambdaUpdateWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .set(SysRole::getIsDeleted, 1)
                .set(SysRole::getUpdateTime, LocalDateTime.now()));
    }

    public List<Long> getMenuIds(Long roleId) {
        find(roleId);
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = find(roleId);
        if (Objects.equals(role.getIsSystem(), 1)) throw new RoleException("内置角色不可修改菜单权限");
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            menuIds.stream().filter(Objects::nonNull).distinct().forEach(menuId -> {
                SysRoleMenu item = new SysRoleMenu();
                item.setRoleId(roleId);
                item.setMenuId(menuId);
                roleMenuMapper.insert(item);
            });
        }
    }

    private SysRole find(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new RoleException("角色不存在");
        return role;
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setSort(role.getSort());
        vo.setRemark(role.getRemark());
        vo.setIsSystem(role.getIsSystem());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) throw new RoleException("角色ID不能为空");
        try {
            List<Long> result = java.util.Arrays.stream(ids.split(","))
                    .map(String::trim).filter(value -> !value.isEmpty()).map(Long::valueOf).distinct().toList();
            if (result.isEmpty()) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            throw new RoleException("角色ID格式错误");
        }
    }
}
