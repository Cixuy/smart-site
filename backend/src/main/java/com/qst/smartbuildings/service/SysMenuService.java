package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.qst.smartbuildings.domain.SysUserRole;
import com.qst.smartbuildings.dto.MenuCreateRequest;
import com.qst.smartbuildings.dto.MenuUpdateRequest;
import com.qst.smartbuildings.entity.SysMenu;
import com.qst.smartbuildings.entity.SysRoleMenu;
import com.qst.smartbuildings.exception.MenuException;
import com.qst.smartbuildings.mapper.SysMenuMapper;
import com.qst.smartbuildings.mapper.SysRoleMenuMapper;
import com.qst.smartbuildings.mapper.SysUserRoleMapper;
import com.qst.smartbuildings.vo.MenuTreeVO;
import com.qst.smartbuildings.vo.MenuVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 菜单树服务：查询完整菜单树结构，用于角色权限分配和前端路由渲染
 */
@Service
public class SysMenuService {
    private final SysMenuMapper menuMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SysMenuService(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /**
     * 返回完整菜单树结构
     */
    public List<MenuTreeVO> tree() {
        // 1. 查询所有未删除菜单（@TableLogic 已过滤 is_deleted=1），按排序升序
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));

        if (menus.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 转换为 VO 并建立 id -> VO 映射
        List<MenuTreeVO> voList = menus.stream().map(this::toVO).toList();
        Map<Long, MenuTreeVO> nodeMap = voList.stream()
                .collect(Collectors.toMap(MenuTreeVO::getId, vo -> vo));

        // 3. 按 parentId 挂载子节点，同时收集根节点
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO node : voList) {
            Long pid = node.getParentId();
            MenuTreeVO parent = pid == null ? null : nodeMap.get(pid);
            if (parent != null) {
                // 挂到父节点下
                List<MenuTreeVO> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                }
                children.add(node);
            } else {
                // 找不到父节点则视为根节点（parentId 为 null 或 0 或父节点不存在）
                roots.add(node);
            }
        }

        return roots;
    }

    /**
     * 返回菜单平铺列表，支持按名称模糊和类型筛选
     *
     * @param menuName 菜单名称（模糊，可选）
     * @param menuType 菜单类型：1-目录，2-菜单，3-按钮（可选）
     */
    public List<MenuVO> list(String menuName, Integer menuType) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .like(StringUtils.isNotBlank(menuName), SysMenu::getMenuName, menuName)
                .eq(menuType != null, SysMenu::getMenuType, menuType)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId);

        List<SysMenu> menus = menuMapper.selectList(wrapper);
        return menus.stream().map(this::toListVO).toList();
    }

    /**
     * 查询菜单详情
     *
     * @param id 菜单ID
     * @return 菜单详情（MenuVO）
     */
    public MenuVO getDetailById(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new MenuException("菜单不存在");
        }
        return toListVO(menu);
    }

    /**
     * 新增菜单
     *
     * @param req 新增菜单请求参数
     */
    public void add(MenuCreateRequest req) {
        SysMenu menu = new SysMenu();
        menu.setParentId(req.parentId() == null ? 0L : req.parentId());
        menu.setMenuName(req.menuName());
        menu.setMenuType(req.menuType());
        menu.setPermissionKey(req.permissionKey());
        menu.setPath(req.path());
        menu.setComponent(req.component());
        menu.setIcon(req.icon());
        menu.setSort(req.sort() == null ? 0 : req.sort());
        menu.setIsVisible(req.isVisible() == null ? 1 : req.isVisible());
        // createTime / updateTime 由 MyMetaObjectHandler 自动填充
        menuMapper.insert(menu);

    }

    /**
     * 修改菜单
     *
     * @param req 修改菜单请求参数
     */
    public void update(MenuUpdateRequest req) {
        SysMenu menu = menuMapper.selectById(req.id());
        if (menu == null) {
            throw new MenuException("菜单不存在");
        }
        Boolean parentChanged = req.parentId() != null && !req.parentId().equals(menu.getParentId());
        if (parentChanged && isChildOfSelf(req.id(), req.parentId())) {
            throw new MenuException("不能将菜单移动到其自身或其子菜单下");
        }
        menu.setParentId(req.parentId() == null ? menu.getParentId() : req.parentId());
        menu.setMenuName(req.menuName());
        menu.setMenuType(req.menuType());
        menu.setPermissionKey(req.permissionKey());
        menu.setPath(req.path());
        menu.setComponent(req.component());
        menu.setIcon(req.icon());
        menu.setSort(req.sort() == null ? menu.getSort() : req.sort());
        menu.setIsVisible(req.isVisible() == null ? menu.getIsVisible() : req.isVisible());
        // 显式刷新更新时间，反映本次修改（strictUpdateFill 仅在字段为空时填充，故此处显式赋值）
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);

    }

    /**
     * 删除菜单（逻辑删除）
     * <p>
     * 若该菜单下仍存在子菜单，则不允许删除，需先删除子菜单。
     * </p>
     *
     * @param id 菜单ID
     */
    public void delete(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new MenuException("菜单不存在");
        }
        // 存在子菜单时不允许删除
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new MenuException("存在子菜单，请先删除子菜单", 400);
        }
        // 逻辑删除（@TableLogic 会将 is_deleted 置为 1）
        menuMapper.deleteById(id);
    }

    /**
     * 判断 target 是否为 self 自身或其子孙节点。
     * <p>用于防止把菜单移动到自己的子节点下造成环。</p>
     */
    private boolean isChildOfSelf(Long selfId, Long targetId) {
        Long cursor = targetId;
        int depth = 0;
        // 最多向上追溯 100 层，防止循环引用导致的死循环
        while (cursor != null && cursor != 0L && depth++ < 100) {
            if (cursor.equals(selfId)) {
                return true;
            }
            SysMenu parent = menuMapper.selectById(cursor);
            if (parent == null) {
                return false;
            }
            cursor = parent.getParentId();
        }
        return false;
    }


    /**
     * 循环遍历 sys_menu.path，找出与当前请求路径匹配的所有菜单ID。
     * <p>用于判断请求是否需要做菜单权限拦截：只要命中任一菜单 path，
     * 则在通过 Token 校验后，还必须校验当前用户是否拥有其中任一菜单。</p>
     *
     * @param requestURI 当前请求路径（已去掉 context-path）
     * @return 命中的菜单ID列表；未命中返回空列表
     */
    public List<Long> matchedMenuIds(String requestURI) {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .isNotNull(SysMenu::getPath)
                .ne(SysMenu::getPath, ""));
        List<Long> matched = new ArrayList<>();
        for (SysMenu menu : menus) {
            String path = menu.getPath();
            if (StringUtils.isNotBlank(path) && pathMatcher.match(path, requestURI)) {
                matched.add(menu.getId());
            }
        }
        return matched;
    }

    /**
     * 判断用户是否拥有其中任一菜单（含子节点）。
     * <p>权限链路：{@code 用户( sys_user_role ) -> 角色( sys_role_menu ) -> 菜单ID}。</p>
     *
     * @param userId  当前登录用户ID
     * @param menuIds 待匹配的菜单ID集合
     * @return true 表示用户拥有其中任一菜单访问权限
     */
    public boolean hasAnyMenuId(Long userId, List<Long> menuIds) {
        if (userId == null || menuIds == null || menuIds.isEmpty()) {
            return false;
        }
        // 1. 用户绑定的角色ID
        List<Long> roleIds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        // 2. 角色-菜单关联的菜单ID
        List<Long> grantedMenuIds = sysRoleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .distinct()
                .toList();
        if (grantedMenuIds.isEmpty()) {
            return false;
        }
        // 3. 交集判断：用户被授权的菜单中是否包含任一待匹配菜单ID
        return grantedMenuIds.stream().anyMatch(menuIds::contains);
    }

    private MenuTreeVO toVO(SysMenu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPermissionKey(menu.getPermissionKey());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setIsVisible(menu.getIsVisible());
        return vo;
    }

    private MenuVO toListVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPermissionKey(menu.getPermissionKey());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setIsVisible(menu.getIsVisible());
        vo.setCreateTime(menu.getCreateTime());
        return vo;
    }
}


