import { createRouter, createWebHistory } from 'vue-router'
import store from '@/store'
import { getToken } from '@/utils/auth'

/** 静态路由 - 无需权限 */
export const constantRoutes = [
  {
    path: '/',
    name: 'Root',
    redirect: '/login',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false, hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', requiresAuth: false, hidden: true }
  }
]

/** 动态路由映射表 - 菜单 component 字段对应的视图组件 */
export const componentMap = {
  // 工作台
  'home': () => import('@/views/home/index.vue'),

  // 设备资产管理
  'asset/device': () => import('@/views/asset/device/index.vue'),
  'asset/type': () => import('@/views/asset/type/index.vue'),
  'asset/sensor': () => import('@/views/asset/sensor/index.vue'),
  'asset/point': () => import('@/views/asset/point/index.vue'),
  'asset/location': () => import('@/views/asset/location/index.vue'),

  // 塔吊监控
  'monitor/towerCrane': () => import('@/views/monitor/towerCrane/index.vue'),
  'monitor/towerCrane/alarms': () => import('@/views/monitor/towerCrane/alarms.vue'),
  'monitor/towerCrane/thresholds': () => import('@/views/monitor/towerCrane/thresholds.vue'),

  // 升降机监控
  'monitor/hoist': () => import('@/views/monitor/hoist/index.vue'),
  // 2026-08-15 删除：升降机"历史数据"子页面 records.vue 已移除，历史查询并入主页面运行曲线
  'monitor/hoist/thresholds': () => import('@/views/monitor/hoist/thresholds.vue'),
  // 2026-08-15 新增：升降机联锁告警列表子页面（GET /api/hoist/realtime/interlock-alarms）
  'monitor/hoist/alarms': () => import('@/views/monitor/hoist/alarms.vue'),

  // 视频监控
  'monitor/video': () => import('@/views/monitor/video/index.vue'),

  // 环境监测
  'monitor/environment': () => import('@/views/monitor/environment/index.vue'),
  'monitor/environment/thresholds': () => import('@/views/monitor/environment/thresholds.vue'),
  // 2026-08-15 新增：环境日统计报表子页面（daily-stat / daily-stat/trend）
  'monitor/environment/dailyStat': () => import('@/views/monitor/environment/dailyStat.vue'),
  // 2026-08-15 新增：环境告警列表子页面（GET /api/env-monitor/alarms）
  'monitor/environment/alarms': () => import('@/views/monitor/environment/alarms.vue'),

  // AI智能识别
  'ai/alarms': () => import('@/views/ai/index.vue'),

  // 告警管理
  'alarm/list': () => import('@/views/alarm/index.vue'),
  'alarm/statistics': () => import('@/views/alarm/statistics.vue'),

  // 喷淋降尘（运行状态与控制已合并为一个模块，见 spray/status.vue）
  'spray/status': () => import('@/views/spray/status.vue'),
  'spray/log': () => import('@/views/spray/log.vue'),
  'spray/schedule': () => import('@/views/spray/schedule.vue'),

  // 数据大屏
  'screen/list': () => import('@/views/screen/index.vue'),
  'screen/editor': () => import('@/views/screen/editor.vue'),

  // 可视化场景
  'scene/list': () => import('@/views/scene/index.vue'),
  'scene/editor': () => import('@/views/scene/editor.vue'),

  // 对话智能体（2026-08-17 由 Coze智能体 改名）
  'agent/chat': () => import('@/views/agent/index.vue'),

  // 系统管理
  'system/user': () => import('@/views/system/user/index.vue'),
  'system/role': () => import('@/views/system/role/index.vue'),
  'system/log/login': () => import('@/views/system/log/login.vue'),
  'system/log/oper': () => import('@/views/system/log/oper.vue'),

  // 个人中心（独立页面，不在侧边栏菜单中）
  'profile': () => import('@/views/profile/index.vue')
}

/**
 * 将后端菜单树转换为 Vue Router 路由（扁平化，绝对路径）
 * 返回的路由会作为 Layout 组件的 children
 */
export function generateRoutes(menuTree) {
  const children = []

  function traverse(menus, parentPath = '') {
    menus.forEach(menu => {
      if (menu.hidden) return

      const routePath = menu.path.startsWith('/')
        ? menu.path
        : `${parentPath}/${menu.path}`.replace(/\/+/g, '/')

      const component = menu.component ? componentMap[menu.component] : null

      // 路由 name 必须以路径为基准保持全局唯一：
      // 菜单树中不同菜单可能同名（如"操作日志"），Vue Router 中同名路由后注册的会替换先注册的，导致页面 404
      const routeName = `route_${routePath.replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '_').replace(/^_+|_+$/g, '')}`

      const route = {
        path: routePath,
        name: routeName,
        component,
        redirect: menu.redirect || undefined,
        meta: {
          title: menu.meta?.title || menu.name,
          icon: menu.meta?.icon || menu.icon,
          requiresAuth: true,
          keepAlive: menu.meta?.keepAlive ?? true,
          affix: menu.meta?.affix ?? false,
          hidden: menu.hidden ?? false
        }
      }

      // 只有包含组件的路由才加入 children
      if (component) {
        children.push(route)
      }

      if (menu.children && menu.children.length > 0) {
        traverse(menu.children, routePath)
      }
    })
  }

  traverse(menuTree)
  return children
}

/** Layout 组件 */
const Layout = () => import('@/layout/index.vue')

/** 异步路由 - 已改为完全动态生成 */
export const asyncRoutes = []

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [...constantRoutes],
  scrollBehavior: () => ({ top: 0 })
})

/** 白名单 - 无需 token 即可访问 */
const whiteList = ['/login', '/404']

/** 标记动态路由是否已注册 */
let dynamicRoutesAdded = false

/** 注册动态路由（幂等），返回 true 表示本次确实注册了路由 */
function addDynamicRoutes() {
  if (router.hasRoute('Layout')) return false

  const menuTree = [...(store.state.app.menuList || [])]

  // 调试日志
  if (process.env.NODE_ENV === 'development') {
    console.log('[addDynamicRoutes] 菜单树条目数:', menuTree.length)
    console.log('[addDynamicRoutes] 菜单路径:', menuTree.map(m => m.path || m.name))
  }

  // 确保 /home 始终存在且位于首位，不依赖后端菜单数据
  const hasHome = menuTree.some(m => m.component === 'home' || m.path === '/home')
  if (!hasHome) {
    menuTree.unshift({
      path: '/home',
      name: 'Home',
      component: 'home',
      meta: { title: '首页', icon: 'home', keepAlive: false, affix: false },
      hidden: false,
      children: []
    })
    // 通过 mutation 同步回 store，确保 Sidebar 响应式更新
    store.commit('app/SET_MENU_LIST', menuTree)
  }

  const children = generateRoutes(menuTree)

  if (process.env.NODE_ENV === 'development') {
    console.log('[addDynamicRoutes] 生成的路由数:', children.length)
    console.log('[addDynamicRoutes] 路由路径:', children.map(r => r.path))
  }

  // 移除占位 Root，替换为 Layout 布局路由
  router.removeRoute('Root')
  router.addRoute({
    path: '/',
    name: 'Layout',
    component: Layout,
    redirect: '/home',
    children: [
      ...children,
      {
        path: '/profile',
        name: 'Profile',
        component: componentMap['profile'],
        meta: { title: '个人中心', requiresAuth: true, hidden: true }
      },
      // 监控子页面 —— componentMap 中有但菜单树中无对应条目的二级页面
      {
        path: '/monitor/towerCrane/alarms',
        name: 'TowerCraneAlarms',
        component: componentMap['monitor/towerCrane/alarms'],
        meta: { title: '塔吊告警列表', requiresAuth: true, hidden: true }
      },
      {
        path: '/monitor/towerCrane/thresholds',
        name: 'TowerCraneThresholds',
        component: componentMap['monitor/towerCrane/thresholds'],
        meta: { title: '塔吊阈值配置', requiresAuth: true, hidden: true }
      },
      {
        // 2026-08-15 删除：HoistRecords 路由已随 records.vue 子页面一并移除
        path: '/monitor/hoist/thresholds',
        name: 'HoistThresholds',
        component: componentMap['monitor/hoist/thresholds'],
        meta: { title: '升降机阈值配置', requiresAuth: true, hidden: true }
      },
      {
        // 2026-08-15 新增：升降机联锁告警列表子页面（隐藏路由，不展示在侧边栏菜单）
        path: '/monitor/hoist/alarms',
        name: 'HoistAlarms',
        component: componentMap['monitor/hoist/alarms'],
        meta: { title: '升降机联锁告警列表', requiresAuth: true, hidden: true }
      },
      {
        path: '/monitor/environment/thresholds',
        name: 'EnvThresholds',
        component: componentMap['monitor/environment/thresholds'],
        meta: { title: '环境阈值配置', requiresAuth: true, hidden: true }
      },
      {
        // 2026-08-15 新增：环境日统计报表子页面（隐藏路由，不展示在侧边栏菜单）
        path: '/monitor/environment/daily-stat',
        name: 'EnvDailyStat',
        component: componentMap['monitor/environment/dailyStat'],
        meta: { title: '环境日统计报表', requiresAuth: true, hidden: true }
      },
      {
        // 2026-08-15 新增：环境告警列表子页面（隐藏路由，不展示在侧边栏菜单）
        path: '/monitor/environment/alarms',
        name: 'EnvAlarms',
        component: componentMap['monitor/environment/alarms'],
        meta: { title: '环境告警列表', requiresAuth: true, hidden: true }
      },
      { path: '/:pathMatch(.*)*', redirect: '/404' }
    ]
  })
  dynamicRoutesAdded = true
  return true
}

router.beforeEach(async (to, from, next) => {
  const token = getToken()

  // 已登录状态
  if (token) {
    if (to.path === '/login') {
      // 不要直接跳转 /home — 先尝试恢复 session，避免 getUserInfo 失败导致死循环
      if (!store.getters.userId) {
        try {
          await store.dispatch('user/getUserInfo')
          addDynamicRoutes()
          next('/home')
        } catch (e) {
          // token 已失效，清除状态并留在登录页
          store.dispatch('user/clearAll')
          if (router.hasRoute('Layout')) {
            router.removeRoute('Layout')
            router.addRoute({ path: '/', name: 'Root', redirect: '/login' })
          }
          dynamicRoutesAdded = false
          next()
        }
      } else {
        addDynamicRoutes()
        next('/home')
      }
    } else {
      // 检查是否已加载用户信息
      if (!store.getters.userId) {
        try {
          await store.dispatch('user/getUserInfo')
          const added = addDynamicRoutes()
          next(added ? { ...to, replace: true } : undefined)
        } catch (e) {
          store.dispatch('user/clearAll')
          if (router.hasRoute('Layout')) {
            router.removeRoute('Layout')
            router.addRoute({ path: '/', name: 'Root', redirect: '/login' })
          }
          dynamicRoutesAdded = false
          next(`/login?redirect=${to.path}`)
        }
      } else {
        const added = addDynamicRoutes()
        next(added ? { ...to, replace: true } : undefined)
      }
    }
  } else {
    // 未登录
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
