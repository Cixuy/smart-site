/** 用户状态管理 */
import { authApi, userApi, roleApi, menuApi } from '@/api'
import { getToken, setToken, removeToken } from '@/utils/auth'
import defaultMenuTree from '@/config/menu'
// 2026-08-15 清理：移除全局告警 wsManager（utils/websocket.js）引用，
// 该连接无业务订阅且后端 /ws/realtime 未使用，详见 layout/index.vue 顶部注释。

/**
 * 将菜单树所有路径规范化为绝对路径（以 / 开头）
 * 解决 Sidebar 和 Router 各自解析路径不一致的问题
 * @param {Array} menus 菜单数组
 * @param {string} parentPath 父级路径
 * @returns {Array} 规范化后的菜单数组（新数组，不修改原数据）
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
 * 将 Swagger 规范的菜单格式转换为前端期望的菜单格式
 * Swagger: { id, parentId, menuName, menuType, permissionKey, path, component, icon, sort, isVisible, children }
 * Frontend: { id, path, name, component, meta: { title, icon, permission }, hidden, sort, children }
 * @param {Array} menus Swagger 菜单数组
 * @param {string} parentPath 父级路径，用于递归拼接绝对路径
 * @returns {Array} 前端格式化后的菜单数组
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

/** 从 data 中提取 userId，兼容 data.userId（Swagger）和 data.id（旧版） */
function extractUserId(data) {
  return data.userId || data.id || (data.user ? (data.user.userId || data.user.id) : undefined)
}

/** 检测 data 是否为 Swagger 新格式（包含 menus 字段） */
function isSwaggerFormat(data) {
  return Array.isArray(data.menus)
}

/**
 * 根据用户角色查询菜单权限，过滤默认菜单树
 * 用于后端未返回已过滤菜单时的前端兜底方案
 * @param {Array} roles 用户角色数组 [{id, roleName, roleCode}]
 * @param {Array} defaultTree 前端默认菜单树
 * @returns {Array|null} 过滤后的菜单树，失败或无角色时返回 null
 */
async function buildRoleFilteredMenu(roles, defaultTree) {
  if (!roles || roles.length === 0) return null
  try {
    // 1. 并发查询所有角色的菜单权限（兼容角色对象和角色字符串）
    const roleResults = await Promise.all(
      roles.map(r => {
        const roleId = typeof r === 'object' ? (r.id ?? r.roleId) : r
        if (roleId == null) return Promise.resolve({ data: { menuIds: [] } })
        return roleApi.getRoleMenus(roleId).catch(() => ({ data: { menuIds: [] } }))
      })
    )
    const allowedIds = new Set()
    roleResults.forEach(r => {
      (r.data?.menuIds || []).forEach(id => allowedIds.add(id))
    })
    if (allowedIds.size === 0) return null

    // 2. 获取菜单平铺列表，建立 id → name 映射
    const listRes = await menuApi.getList()
    const flatList = Array.isArray(listRes.data) ? listRes.data : (listRes.data?.list || [])
    const idToName = {}
    flatList.forEach(m => { idToName[m.id] = m.menuName })

    const allowedNames = new Set()
    allowedIds.forEach(id => {
      if (idToName[id]) allowedNames.add(idToName[id])
    })
    if (allowedNames.size === 0) return null

    // 3. 递归过滤菜单树（name 匹配，父节点命中时保留全部子节点）
    function filter(tree) {
      return tree
        .map(node => {
          // 当前节点直接命中 → 保留本节点及全部子节点（不再过滤子节点）
          if (allowedNames.has(node.name)) {
            return { ...node }
          }
          // 当前节点未命中，但子节点中可能有命中的 → 保留节点作为包装，继续过滤子节点
          if (node.children && node.children.length > 0) {
            const filteredChildren = filter(node.children)
            if (filteredChildren.length > 0) {
              return { ...node, children: filteredChildren }
            }
          }
          // 都不命中 → 丢弃
          return null
        })
        .filter(Boolean)
    }
    return filter(defaultTree)
  } catch (_) {
    return null
  }
}

const state = () => ({
  token: getToken(),
  userId: '',
  username: '',
  realName: '',
  email: '',
  phone: '',
  avatar: '',
  roles: [],
  permissions: []
})

const getters = {
  token: (s) => s.token,
  userId: (s) => s.userId,
  username: (s) => s.username,
  realName: (s) => s.realName,
  email: (s) => s.email,
  phone: (s) => s.phone,
  avatar: (s) => s.avatar,
  roles: (s) => s.roles,
  permissions: (s) => s.permissions
}

const mutations = {
  SET_TOKEN(state, token) {
    state.token = token
    setToken(token)
  },
  SET_USER_INFO(state, info) {
    Object.assign(state, info)
  },
  SET_ROLES(state, roles) {
    state.roles = roles
  },
  SET_PERMISSIONS(state, perms) {
    state.permissions = perms
  },
  CLEAR_ALL(state) {
    state.token = ''
    state.userId = ''
    state.username = ''
    state.realName = ''
    state.email = ''
    state.phone = ''
    state.avatar = ''
    state.roles = []
    state.permissions = []
    removeToken()
  }
}

const actions = {
  /** 登录 */
  async login({ commit }, loginForm) {
    const res = await authApi.login(loginForm)
    const data = res.data || {}
    // 后端返回字段为 data.token
    if (data.token) {
      commit('SET_TOKEN', data.token)
    }
    // 从登录响应中直接提取用户基本信息（作为 fallback）
    const uid = extractUserId(data)
    if (uid) {
      commit('SET_USER_INFO', {
        userId: uid,
        username: data.username || '',
        realName: data.realName || '',
        email: data.email || '',
        phone: data.phone || '',
        avatar: data.avatar || ''
      })
    }
    // 如果登录接口直接返回了菜单，一并存入（作为 getUserInfo 的 fallback）
    const menus = data.menus || data.menuTree
    if (menus) {
      const normalized = isSwaggerFormat(data)
        ? normalizeSwaggerMenus(data.menus)
        : normalizeMenuPaths(menus)
      commit('app/SET_MENU_LIST', normalized, { root: true })
    }
    if (data.roles) {
      commit('SET_ROLES', data.roles)
    }
    if (data.permissions) {
      commit('SET_PERMISSIONS', data.permissions)
    }
    return res
  },

  /** 获取用户信息 */
  async getUserInfo({ commit, dispatch, rootState }) {
    const res = await authApi.getCurrentUser()
    const data = res.data || {}

    // 调试日志：输出响应结构以便排查侧边栏为空的问题
    if (process.env.NODE_ENV === 'development') {
      console.log('[getUserInfo] 响应 keys:', Object.keys(data))
      const hasMenuTree = Array.isArray(data.menuTree) && data.menuTree.length > 0
      if (!hasMenuTree) {
        console.warn('[getUserInfo] [注意] 响应中无 menuTree 或为空，侧边栏将只显示首页')
      }
    }

    // 兼容两种响应格式，先提取用户信息、角色、权限
    let swaggerMenus = null    // 后端已过滤的 Swagger 格式菜单
    let backendMenuTree = null // 后端返回的旧版 menuTree

    if (data.user) {
      // 嵌套格式：{ user: { id/userId, username, ... }, roles: [...], permissions: [...], menuTree/menus: [...] }
      const uid = extractUserId(data.user)
      commit('SET_USER_INFO', {
        userId: uid,
        username: data.user.username || '',
        realName: data.user.realName || data.user.nickname || '',
        email: data.user.email || '',
        phone: data.user.phone || '',
        avatar: data.user.avatar || ''
      })
      if (data.roles !== undefined) {
        commit('SET_ROLES', data.roles)
      }
      if (data.permissions !== undefined) {
        commit('SET_PERMISSIONS', data.permissions)
      }
      if (isSwaggerFormat(data)) {
        swaggerMenus = data.menus
      } else if (data.menuTree) {
        backendMenuTree = data.menuTree
      }
    } else {
      // 扁平格式：{ id/userId, username, realName, ... } 或仅有 roles/permissions/menus/menuTree
      const uid = extractUserId(data)
      if (uid) {
        commit('SET_USER_INFO', {
          userId: uid,
          username: data.username || '',
          realName: data.realName || '',
          email: data.email || '',
          phone: data.phone || '',
          avatar: data.avatar || ''
        })
      }
      if (data.roles !== undefined) {
        commit('SET_ROLES', data.roles)
      }
      if (data.permissions !== undefined) {
        commit('SET_PERMISSIONS', data.permissions)
      }
      if (isSwaggerFormat(data)) {
        swaggerMenus = data.menus
      } else if (data.menuTree) {
        backendMenuTree = data.menuTree
      }
    }

    // getCurrentUser 不返回 roles/permissions（旧版），需从 /sys/user/{id} 补充获取
    const userId = extractUserId(data)
    if (userId && (data.roles === undefined || data.permissions === undefined)) {
      try {
        const userRes = await userApi.getById(userId)
        const userData = userRes.data || {}
        if (data.roles === undefined && userData.roles !== undefined) {
          commit('SET_ROLES', userData.roles)
        }
        if (data.permissions === undefined && userData.permissions !== undefined) {
          commit('SET_PERMISSIONS', userData.permissions)
        }
      } catch (_) {
        if (process.env.NODE_ENV === 'development') {
          console.warn('[getUserInfo] 获取用户 roles 失败')
        }
      }
    }

    // -------------------- 菜单设置：统一在角色确定后执行 --------------------
    if (swaggerMenus) {
      // 后端已返回 Swagger 格式菜单（通常已按角色过滤）
      commit('app/SET_MENU_LIST', normalizeSwaggerMenus(swaggerMenus), { root: true })
    } else if (backendMenuTree) {
      // 后端返回旧版 menuTree
      commit('app/SET_MENU_LIST', normalizeMenuPaths(backendMenuTree), { root: true })
    } else {
      // 后端未返回菜单 → 前端根据角色权限过滤默认菜单树
      const roles = rootState.user?.roles || data.roles || []

      // 系统管理员拥有全部菜单，跳过角色过滤
      const isSystemAdmin = roles.some(r =>
        r.roleCode === 'SYSTEM_ADMIN' || r.code === 'SYSTEM_ADMIN'
        || r.roleCode === 'admin' || r.code === 'admin'
        || r.isSystem === 1
      )
      if (isSystemAdmin) {
        commit('app/SET_MENU_LIST', normalizeMenuPaths(defaultMenuTree), { root: true })
      } else {
        const filtered = await buildRoleFilteredMenu(roles, defaultMenuTree)
        if (filtered && filtered.length > 0) {
          commit('app/SET_MENU_LIST', normalizeMenuPaths(filtered), { root: true })
        } else {
          commit('app/SET_MENU_LIST', normalizeMenuPaths(defaultMenuTree), { root: true })
        }
      }
    }

    return res.data
  },

  /** 更新个人信息 */
  async updateProfile({ commit, state }, form) {
    const data = {
      id: state.userId,
      realName: form.realName || '',
      email: form.email || '',
      phone: form.phone || ''
    }
    const res = await userApi.update(data)
    // 更新本地 store
    commit('SET_USER_INFO', {
      realName: form.realName || '',
      email: form.email || '',
      phone: form.phone || ''
    })
    return res
  },

  /** 登出 */
  async logout({ commit }) {
    try { await authApi.logout() } catch (_) { /* ignore */ }
    // 2026-08-15 清理：原 wsManager.disconnect() 随全局告警连接一并移除（无业务订阅）
    commit('CLEAR_ALL')
    // 清理动态注册的 Layout 路由，恢复占位 Root
    const router = (await import('@/router')).default
    if (router.hasRoute('Layout')) {
      router.removeRoute('Layout')
      router.addRoute({ path: '/', name: 'Root', redirect: '/login' })
    }
  },

  /** 清除所有状态 */
  clearAll({ commit }) {
    commit('CLEAR_ALL')
  }
}

export default { namespaced: true, state, getters, mutations, actions }
