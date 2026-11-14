/**
 * 前端默认菜单树 —— 当后端未返回 menuTree 时的兜底方案
 * 
 * 菜单结构与 componentMap（router/index.js）一一对应：
 *   - path:   路由路径（相对路径会自动拼接父路径）
 *   - name:   菜单显示名称
 *   - component: 对应 componentMap 的 key
 *   - icon:   图标名（Element Plus Icons 的 kebab-case 名）
 *   - children: 子菜单
 */
const defaultMenuTree = [
  {
    path: '/home',
    name: '首页',
    component: 'home',
    meta: { title: '首页', icon: 'HomeFilled' },
    children: []
  },
  {
    path: '/asset',
    name: '设备资产',
    meta: { title: '设备资产', icon: 'Monitor' },
    children: [
      { path: 'device', name: '设备管理', component: 'asset/device', meta: { title: '设备管理', icon: 'Cpu' } },
      { path: 'type', name: '设备类型', component: 'asset/type', meta: { title: '设备类型', icon: 'SetUp' } },
      { path: 'sensor', name: '传感器管理', component: 'asset/sensor', meta: { title: '传感器管理', icon: 'Connection' } },
      { path: 'point', name: '环境监测点', component: 'asset/point', meta: { title: '环境监测点', icon: 'Aim' } },
      { path: 'location', name: '位置管理', component: 'asset/location', meta: { title: '位置管理', icon: 'Location' } }
    ]
  },
  {
    path: '/monitor',
    name: '实时监控',
    meta: { title: '实时监控', icon: 'DataAnalysis' },
    children: [
      { path: 'towerCrane', name: '塔吊监控', component: 'monitor/towerCrane', meta: { title: '塔吊监控', icon: 'Operation' } },
      { path: 'hoist', name: '升降机监控', component: 'monitor/hoist', meta: { title: '升降机监控', icon: 'Upload' } },
      { path: 'video', name: '视频监控', component: 'monitor/video', meta: { title: '视频监控', icon: 'VideoCamera' } },
      { path: 'environment', name: '环境监测', component: 'monitor/environment', meta: { title: '环境监测', icon: 'Sunny' } }
    ]
  },
  {
    path: '/ai',
    name: 'AI智能识别',
    component: 'ai/alarms',
    meta: { title: 'AI智能识别', icon: 'Cpu' }
  },
  {
    path: '/alarm',
    name: '告警管理',
    meta: { title: '告警管理', icon: 'Bell' },
    children: [
      { path: 'list', name: '告警列表', component: 'alarm/list', meta: { title: '告警列表', icon: 'List' } },
      { path: 'statistics', name: '告警统计', component: 'alarm/statistics', meta: { title: '告警统计', icon: 'PieChart' } }
    ]
  },
  {
    path: '/spray',
    name: '喷淋降尘',
    meta: { title: '喷淋降尘', icon: 'Umbrella' },
    children: [
      { path: 'status', name: '喷淋控制', component: 'spray/status', meta: { title: '喷淋控制', icon: 'Switch' } },
      { path: 'log', name: '操作日志', component: 'spray/log', meta: { title: '操作日志', icon: 'Document' } },
      { path: 'schedule', name: '定时计划', component: 'spray/schedule', meta: { title: '定时计划', icon: 'Clock' } }
    ]
  },
  {
    path: '/screen',
    name: '数据大屏',
    meta: { title: '数据大屏', icon: 'Monitor' },
    children: [
      { path: 'list', name: '大屏列表', component: 'screen/list', meta: { title: '大屏列表', icon: 'List' } },
      { path: 'editor', name: '大屏编辑', component: 'screen/editor', meta: { title: '大屏编辑', icon: 'Edit' } }
    ]
  },
  {
    path: '/scene',
    name: '可视化场景',
    meta: { title: '可视化场景', icon: 'Picture' },
    children: [
      // 2026-08-14 需求调整：固定只展示一个场景，菜单名由"场景列表"改为"场景详情"
      { path: 'list', name: '场景详情', component: 'scene/list', meta: { title: '场景详情', icon: 'List' } },
      { path: 'editor', name: '场景编辑', component: 'scene/editor', meta: { title: '场景编辑', icon: 'Edit' } }
    ]
  },
  {
    path: '/agent',
    name: '智能体',
    component: 'agent/chat',
    // 2026-08-17 需求调整：侧边栏菜单名称由"Coze智能体"改为"对话智能体"，与后端服务命名保持一致
    meta: { title: '对话智能体', icon: 'ChatDotRound' }
  },
  {
    path: '/system',
    name: '系统管理',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      { path: 'user', name: '用户管理', component: 'system/user', meta: { title: '用户管理', icon: 'User' } },
      { path: 'role', name: '角色管理', component: 'system/role', meta: { title: '角色管理', icon: 'Avatar' } },
      { path: 'log/login', name: '登录日志', component: 'system/log/login', meta: { title: '登录日志', icon: 'Document' } },
      { path: 'log/oper', name: '操作日志', component: 'system/log/oper', meta: { title: '操作日志', icon: 'Operation' } }
    ]
  }
]

export default defaultMenuTree
