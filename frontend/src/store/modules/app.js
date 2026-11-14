/** 应用级状态管理 */
import { authApi } from '@/api'

/**
 * 当前系统仅有一个项目，projectId 固定为 1。
 * localStorage 未设置时兜底为 1，避免后端必传参数校验报“缺少 projectId”。
 */
const DEFAULT_PROJECT_ID = 1

/**
 * 将菜单树所有路径规范化为绝对路径（递归拼接父路径）
 */
function normalizeMenuPaths(menus, parentPath = '') {
  if (!Array.isArray(menus)) return []
  return menus.map(menu => {
    const fullPath = menu.path
      ? (menu.path.startsWith('/') ? menu.path : `${parentPath}/${menu.path}`.replace(/\/+/g, '/'))
      : parentPath || '/'
    return {
      ...menu,
      path: fullPath,
      children: menu.children ? normalizeMenuPaths(menu.children, fullPath) : []
    }
  })
}

/**
 * 将 Swagger 规范的菜单格式转换为前端期望的菜单格式（递归拼接绝对路径）
 */
function normalizeSwaggerMenus(menus, parentPath = '') {
  if (!Array.isArray(menus)) return []
  return menus.map(m => {
    const ownPath = m.path || ''
    const fullPath = ownPath
      ? (ownPath.startsWith('/') ? ownPath : `${parentPath}/${ownPath}`.replace(/\/+/g, '/'))
      : parentPath || '/'
    return {
      id: m.id,
      path: fullPath,
      name: m.menuName || '',
      component: m.component || '',
      meta: {
        title: m.menuName || '',
        icon: m.icon || '',
        permission: m.permissionKey || ''
      },
      hidden: m.isVisible === 0,
      sort: m.sort || 0,
      children: m.children ? normalizeSwaggerMenus(m.children, fullPath) : []
    }
  })
}

const state = () => ({
  sidebarCollapsed: false,
  deviceInfo: 'desktop',
  menuList: [],
  permissions: [],
  roles: [],
  projectId: Number(localStorage.getItem('projectId')) || DEFAULT_PROJECT_ID
})

const getters = {
  sidebarCollapsed: (s) => s.sidebarCollapsed,
  deviceInfo: (s) => s.deviceInfo,
  menuList: (s) => s.menuList,
  permissions: (s) => s.permissions,
  roles: (s) => s.roles,
  projectId: (s) => s.projectId
}

const mutations = {
  TOGGLE_SIDEBAR(state) {
    state.sidebarCollapsed = !state.sidebarCollapsed
  },
  SET_DEVICE(state, device) {
    state.deviceInfo = device
  },
  SET_MENU_LIST(state, list) {
    state.menuList = list
  },
  SET_PERMISSIONS(state, perms) {
    state.permissions = perms
  },
  SET_ROLES(state, roles) {
    state.roles = roles
  },
  SET_PROJECT_ID(state, id) {
    state.projectId = id
    localStorage.setItem('projectId', id)
  }
}

const actions = {
  toggleSidebar({ commit }) {
    commit('TOGGLE_SIDEBAR')
  },
  async loadCurrentUser({ commit }) {
    try {
      const res = await authApi.getCurrentUser()
      const data = res.data || {}
      const { user, roles, permissions, menuTree } = data

      commit('SET_ROLES', roles || data.roles || [])
      commit('SET_PERMISSIONS', permissions || data.permissions || [])

      // 菜单：Swagger menus → 标准化，旧版 menuTree → 递归拼接绝对路径
      if (Array.isArray(data.menus)) {
        commit('SET_MENU_LIST', normalizeSwaggerMenus(data.menus))
      } else if (Array.isArray(menuTree) && menuTree.length) {
        commit('SET_MENU_LIST', normalizeMenuPaths(menuTree))
      } else {
        commit('SET_MENU_LIST', [])
      }
      return { user, roles, permissions, menuTree: data.menus || menuTree }
    } catch (e) {
      return Promise.reject(e)
    }
  }
}

export default { namespaced: true, state, getters, mutations, actions }
