<template>
  <!-- 2026-08-15 需求调整：场景详情改为"大屏直出"——去掉卡片网格与"查看"按钮，
       页面主体直接用 Scene3DPreview 铺满整个可视区域（复用 layout main 的负 margin 抵消内边距），
       顶部保留悬浮标题栏与"编辑"入口，编辑仍需进入编辑器 -->
  <div v-loading="loading" class="scene-detail relative overflow-hidden">
    <!-- 顶部悬浮标题栏（大屏风格，半透明深色背景，不遮挡 3D 场景交互） -->
    <div class="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-6 py-3
      bg-gradient-to-b from-black/50 to-transparent">
      <div class="flex items-center gap-3">
        <span class="text-lg font-bold tracking-widest text-slate-100">
          {{ currentScene?.sceneName || '场景详情' }}
        </span>
        <el-tag v-if="demoMode" type="info" size="small" effect="dark">演示数据</el-tag>
      </div>
      <el-button
        v-if="currentScene"
        size="small"
        :icon="Edit"
        @click="$router.push(`/scene/editor?id=${currentScene.id}`)"
      >编辑</el-button>
    </div>

    <!-- 大屏主体：直接铺满可视区域的 3D 场景（固定单场景取 list[0]）
         2026-08-18 新增：active-device-code 控制 3D 选中高亮，device-click 回传点击的设备/空白 -->
    <Scene3DPreview
      v-if="currentScene"
      :model-url="resolveModelUrl(currentScene.modelFileUrl)"
      :devices="sceneDevicesMap[currentScene.id] || []"
      :model-type="1"
      :device-model-url="resolveModelUrl(currentScene.modelFileUrl) || tdcfModel"
      :active-device-code="activeTower"
      :alarm-device-codes="alarmTowers"
      @device-click="handleDeviceClick"
    />

    <!-- 2026-08-18 需求新增：右侧悬浮"塔吊告警"面板（大屏风格，半透明深色，不遮挡 3D 交互）。
         按塔吊设备分组展示各自告警列表：有告警的条目带状态标签，无告警时显示"暂无告警" -->
    <div
      v-if="currentScene && (towerAlarmsMap[currentScene.id] || []).length"
      class="absolute right-4 top-16 bottom-4 z-20 w-80 overflow-hidden rounded-xl border border-slate-700/60
        bg-slate-900/70 backdrop-blur-md flex flex-col"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b border-slate-700/60">
        <span class="text-sm font-bold tracking-widest text-slate-100">塔吊告警</span>
        <span class="text-xs text-slate-400">{{ towerCount }}</span>
      </div>
      <div class="flex-1 overflow-y-auto p-3 space-y-3">
        <div
          v-for="g in towerAlarmsMap[currentScene.id]"
          :key="g.deviceCode"
          class="rounded-lg border p-3 cursor-pointer transition-colors"
          :class="String(g.deviceCode) === activeTower
            ? 'bg-cyan-500/20 border-cyan-400/70 ring-1 ring-cyan-400/50'
            : 'bg-slate-800/60 border-slate-700/50 hover:bg-slate-700/50'"
          @click="activeTower = String(g.deviceCode)"
        >
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm font-medium text-slate-100">{{ g.deviceName || g.deviceCode }}</span>
            <div class="flex items-center gap-2">
              <el-tag
                v-if="g.alarms.length"
                :type="g.alarms.some(a => (a.alarmLevel || 1) >= 2) ? 'danger' : 'warning'"
                size="small"
                effect="dark"
              >{{ g.alarms.length }} 条</el-tag>
              <!-- 2026-08-19 新增：点击"全部告警"跳转到告警管理页，并携带该塔吊 deviceId 按设备筛选；
                   @click.stop 阻止冒泡，避免触发外层分组卡片的 3D 高亮联动 -->
              <span
                class="cursor-pointer text-xs text-cyan-300 hover:text-cyan-200 transition-colors"
                :title="`查看 ${g.deviceName || g.deviceCode} 的全部告警`"
                @click.stop="gotoAlarmManager(g)"
              >全部告警 ›</span>
            </div>
          </div>
          <template v-if="g.alarms.length">
            <div
              v-for="a in g.alarms"
              :key="a.id || a.alarmTime"
              class="flex items-center justify-between gap-2 py-1.5 px-1 rounded border-b border-slate-700/40 last:border-0 cursor-pointer transition-colors hover:bg-slate-700/40"
              :title="`点击查看 ${g.deviceName || g.deviceCode} 的全部告警`"
              @click.stop="gotoAlarmManager(g)"
            >
              <span class="text-xs text-slate-400 shrink-0">{{ formatTime(a.alarmTime) }}</span>
              <!-- 2026-08-18 修改：告警列表改用统一告警接口 /api/alarms/record，
                   后端直接返回告警内容描述（alarmContent，如"塔吊力矩超标"），
                   既满足"显示是哪个指标/事件报警"，也不再显示裸数值（105% 之类） -->
              <span
                class="flex-1 text-xs text-amber-300 text-right truncate"
                :title="a.alarmContent || a.alarmTypeDesc"
              >{{ a.alarmContent || a.alarmTypeDesc || '-' }}</span>
              <el-tag
                :type="(a.alarmLevel || 1) >= 2 ? 'danger' : 'warning'"
                size="small"
                class="shrink-0"
              >{{ a.alarmLevelName || ((a.alarmLevel || 1) >= 2 ? '警报' : '预警') }}</el-tag>
            </div>
          </template>
          <div v-else class="text-xs text-slate-500 py-1">暂无告警</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { Edit } from '@element-plus/icons-vue'
// 2026-08-18 修改：塔吊告警接口由 /api/tower-crane/realtime/alarms 改为统一告警 /api/alarms/record，
// 故移除 towerCraneApi，改用 alarmApi.getRecordList
import { sceneApi, alarmApi } from '@/api'
import Scene3DPreview from './components/Scene3DPreview.vue'
// 本地默认模型：开发演示用（打包后作为静态资源输出，返回可访问 URL）
import tdcfModel from '@/assets/models/tdcf.glb'
// 2026-08-14 调整：兜底设备坐标统一收敛到共享常量 constants.js（供编辑器预览与列表页全屏查看复用）
import { COORD_FALLBACK } from './constants'

const store = useStore()
const router = useRouter()
const projectId = computed(() => store.getters.projectId)

// mock 演示场景的项目ID：真实 projectId 未配置时回退使用，保证场景列表有数据展示
const DEMO_PROJECT_ID = 1001

// mock 兜底场景（后端场景接口未就绪时展示用，与 src/mock/data.js 保持一致）
// 只保留主工地场景，避免回退时出现多余的"基坑监测场景"
const mockScenes = [
  {
    id: 1,
    projectId: 1001,
    sceneName: '主工地3D场景',
    // 2026-08-14 修复：mock 场景的 modelFileUrl 置空（与 editor.vue 的 mockScene 一致），
    // 原值 /files/models/tdcf.glb 是演示假路径、实际无该静态文件，会导致全屏查看加载失败；
    // 底图由 resolveModelUrl 过滤后不显示，塔吊改用本地内置 tdcf.glb 模板渲染
    modelFileUrl: '',
    modelFileMd5: '9e107d9d372bb6826bd81d3542a419d6',
    centerX: 0, centerY: 0, centerZ: 0,
    isSimpleMode: false,
    createdAt: '2026-07-20T10:00:00+08:00',
    updatedAt: '2026-08-10T10:00:00+08:00'
  }
]

/**
 * 2026-08-14 新增：过滤不可访问的模型地址。
 * 说明：mock 数据/接口返回的 /files/models/* 仅为演示假路径（对应资源不存在），
 * 直接传给 Scene3DPreview 会触发"模型加载失败"。返回 '' 表示无底图/无模板，
 * 让组件走"仅设备渲染"分支（塔吊用本地内置 tdcf.glb）。
 * 真实后端接入后可移除此过滤（或改为校验 URL 可达性）。
 */
function resolveModelUrl(url) {
  return url && !url.startsWith('/files/') ? url : ''
}

const list = ref([])
const demoMode = ref(false)
const loading = ref(true)

// ---- 2026-08-15 大屏直出改造：删除全屏查看弹窗相关状态（viewVisible/viewScene/viewDevices/openView），
// ---- 场景直接铺满页面渲染；仅保留各场景设备坐标缓存，供大屏渲染使用 ----
/** 各场景已加载的设备坐标缓存（key=场景id），供大屏渲染复用，避免重复请求 */
const sceneDevicesMap = reactive({})

// ---- 2026-08-18 新增：塔吊告警面板 ----
/** 各场景塔吊告警分组缓存（key=场景id，value=按设备分组的告警列表） */
const towerAlarmsMap = reactive({})

/** 面板标题右侧展示的塔吊数量（如 "3 台"） */
const towerCount = computed(() => {
  const gs = currentScene.value ? (towerAlarmsMap[currentScene.value.id] || []) : []
  return gs.length ? `${gs.length} 台` : ''
})

/**
 * 2026-08-18 新增：当前选中的塔吊 deviceCode（点击 3D 塔吊或右侧告警面板双向联动高亮）。
 * 为空表示未选中任何塔吊。
 */
const activeTower = ref('')

/**
 * 2026-08-18 新增：当前场景处于告警状态的塔吊 deviceCode 集合。
 * 依据：towerAlarmsMap 中该塔吊分组存在告警记录（alarms.length > 0）。
 * 用途：传给 Scene3DPreview 的 alarm-device-codes，让告警塔吊头上的名称标签显示为红色，
 * 大屏上无需点开面板即可一眼识别"哪台塔吊在报警"。
 */
const alarmTowers = computed(() => {
  const gs = currentScene.value ? (towerAlarmsMap[currentScene.value.id] || []) : []
  return gs.filter(g => (g.alarms || []).length).map(g => String(g.deviceCode))
})

/**
 * 2026-08-18 新增：处理 3D 场景设备点击。
 * 仅塔吊（iconType=1）可选中联动右侧告警面板；点击非塔吊设备忽略（保持原选中）；
 * 点击空白（payload 为 null）取消选中。
 */
function handleDeviceClick(payload) {
  if (!payload || !payload.deviceCode) {
    activeTower.value = ''
    return
  }
  const devices = sceneDevicesMap[currentScene.value?.id] || []
  const dev = devices.find(d => String(d.deviceCode) === String(payload.deviceCode))
  if (dev && (dev.iconType || 1) === 1) {
    activeTower.value = String(payload.deviceCode)
  }
}

/**
 * 加载指定场景每个塔吊的告警列表（按设备分组）。
 * 说明：复用 loadSceneDevices 已加载的设备坐标，从中筛选塔吊（iconType=1）且有台账 id（deviceId）的设备，
 * 逐个调用 GET /api/alarms/record?deviceId=<台账id>&sourceType=1 查询该塔吊最近告警；
 * 接口异常/无告警时该设备显示"暂无告警"。
 * 2026-08-18 修改：废弃旧接口 /api/tower-crane/realtime/alarms（deviceId 要设备编码、无告警内容字段，
 * 此前需前端用阈值反推"哪个指标报警"），改用统一告警接口 /api/alarms/record——
 * deviceId 参数为台账主键 id（int64，直接传原始值避免精度丢失），返回 alarmContent（如"塔吊力矩超标"）、
 * alarmLevel（1-预警 2-警报 3-控制）等字段，面板直接展示告警内容，不再需要阈值推导。
 */
async function loadTowerAlarms(scene) {
  const towers = (sceneDevicesMap[scene.id] || []).filter(d => d.iconType === 1 && d.deviceId)
  const groups = await Promise.all(towers.map(async t => {
    let alarms = []
    try {
      // sourceType=1 过滤设备监测类告警；deviceId 为 int64 台账 id，勿 Number() 强转以免丢精度
      const res = await alarmApi.getRecordList({ deviceId: t.deviceId, sourceType: 1, pageNum: 1, pageSize: 20 })
      alarms = (res.data && (res.data.list || res.data.records)) || []
    } catch (_) { /* 接口异常时该塔吊无告警展示 */ }
    return { deviceCode: t.deviceCode, deviceId: t.deviceId, deviceName: t.deviceName, alarms }
  }))
  towerAlarmsMap[scene.id] = groups
}

/**
 * 告警时间统一格式化展示。
 * 2026-08-18 修改：新接口 /api/alarms/record 返回 ISO 格式（yyyy-MM-ddTHH:mm:ss.SSS，含 T 分隔符），
 * 替换 T 后截取到秒；旧接口的 yyyy-MM-dd HH:mm:ss.SSS 同样兼容。
 */
function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

/**
 * 2026-08-19 新增：点击右侧塔吊告警面板跳转到告警管理页，并携带该塔吊 deviceId/deviceName，
 * 使告警管理页按该塔吊筛选展示其全部告警记录。
 * deviceId 为 int64 长整型，统一以字符串传递避免精度丢失。
 */
function gotoAlarmManager(g) {
  router.push({
    path: '/alarm/list',
    query: { deviceId: String(g.deviceId), deviceName: g.deviceName || g.deviceCode || '' }
  })
}

/** 当前大屏展示的场景（固定单场景，取第一个） */
const currentScene = computed(() => list.value[0] || null)

/**
 * 加载指定场景塔吊（iconType=1）设备坐标。
 * 优先取接口实时数据（GET /api/scene/device/list/{sceneId}?iconType=），
 * 接口无数据/失败时回退到 COORD_FALLBACK 兜底，保证 3D 渲染始终有设备展示。
 * 返回统一转换为 Scene3DPreview 所需结构（兼容接口 position/rotation/scale 对象
 * 与兜底数据平铺结构两种形态）。
 * 2026-08-18 修改：① 后端返回为数组结构，修复取值逻辑（原 res.data.records 取不到导致总是回退兜底数据）；
 * ② 保留 deviceId/deviceCode 字段，供塔吊告警面板按设备编码查询告警使用。
 */
async function loadSceneDevices(scene) {
  let coords = []
  try {
    const res = await sceneApi.getDeviceList(scene.id, 1)
    coords = Array.isArray(res.data) ? res.data : ((res.data && res.data.records) || [])
  } catch (_) { coords = [] }
  if (!coords.length) coords = COORD_FALLBACK[1] || []
  return coords.map(d => ({
    deviceId: d.deviceId,
    deviceCode: d.deviceCode,
    deviceName: d.deviceName,
    iconType: d.iconType || d.type || 1,
    position: { x: d.position?.x ?? d.x ?? 0, y: d.position?.y ?? d.y ?? 0, z: d.position?.z ?? d.z ?? 0 },
    rotation: { x: 0, y: d.rotation?.y ?? d.rotation ?? 0, z: 0 },
    scale: { x: d.scale?.x ?? d.scale ?? 1, y: d.scale?.x ?? d.scale ?? 1, z: d.scale?.x ?? d.scale ?? 1 }
  }))
}

async function fetchList() {
  loading.value = true
  const pid = projectId.value || DEMO_PROJECT_ID
  try {
    const res = await sceneApi.getByProject(pid)
    if (res.data && res.data.id) { list.value = [res.data]; demoMode.value = false }
    else { list.value = [...mockScenes]; demoMode.value = true }
  } catch (_) { list.value = [...mockScenes]; demoMode.value = true }
  // 2026-08-14 需求调整：页面进入即预加载各场景设备坐标，供大屏直接渲染 3D 场景
  // 2026-08-18 新增：设备坐标加载完成后同步加载每个塔吊的告警列表
  await Promise.all(list.value.map(async s => {
    sceneDevicesMap[s.id] = await loadSceneDevices(s)
    await loadTowerAlarms(s)
  }))
  loading.value = false
}

onMounted(() => fetchList())
</script>

<style scoped>
/* 2026-08-15 需求调整：页面从"卡片列表+全屏弹窗"改为"大屏直出"。
   用负 margin 抵消 layout main 的 p-4 内边距，高度占满 main 可视区
   （main 高度 = 100vh - navbar 56px），使 3D 场景真正铺满大屏；
   顶部悬浮标题栏与编辑按钮浮在场景之上，不占用场景视口高度 */
.scene-detail {
  margin: -16px;
  height: calc(100vh - 56px);
  background: #0f172a;
}
</style>
