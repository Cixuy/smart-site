<template>
  <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
    <!-- 区块标题 -->
    <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
      <span class="font-semibold text-gray-700">传感器实时数据</span>
      <div class="flex items-center gap-2">
        <el-tag :type="sensorStatusTag.type" size="small" effect="light">
          <span class="inline-block w-2 h-2 rounded-full mr-1" :class="sensorStatusTag.dot"></span>
          {{ sensorStatusTag.text }}
        </el-tag>
        <el-tooltip content="刷新传感器元数据" placement="top">
          <el-button size="small" :icon="Refresh" circle :loading="sensorLoading" @click="refresh" />
        </el-tooltip>
      </div>
    </div>

    <!-- 未选择设备/监测点 -->
    <el-alert
      v-if="parentId == null || parentId === ''"
      type="info"
      :closable="false"
      show-icon
      title="请先选择设备/监测点"
      class="mb-2"
    />
    <!-- 元数据加载中 -->
    <div v-else-if="sensorLoading" class="py-4">
      <el-skeleton :rows="3" animated />
    </div>
    <!-- 无传感器 -->
    <el-alert
      v-else-if="!sensorList.length"
      type="info"
      :closable="false"
      show-icon
      title="该设备/监测点暂无传感器"
      class="mb-2"
    />
    <!-- 按监测类型分组的传感器 -->
    <div v-else class="space-y-4">
      <div
        v-for="group in sensorGroups"
        :key="group.type"
        class="border border-gray-100 rounded-lg overflow-hidden"
      >
        <!-- 类型分区头 -->
        <div class="flex items-center justify-between px-3 py-2 bg-gray-50 border-b border-gray-100">
          <div class="flex items-center gap-2">
            <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="group.meta.badge">
              {{ group.meta.label }}
            </span>
            <span class="text-xs text-gray-400">{{ group.list.length }} 个传感器</span>
          </div>
          <span v-if="groupState(group)" class="text-xs font-medium" :class="groupState(group).cls">
            {{ groupState(group).text }}
          </span>
        </div>

        <!-- 类型分区内的传感器卡片 -->
        <div class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3 p-3">
          <div
            v-for="meta in group.list"
            :key="meta.id"
            class="border border-gray-100 rounded-lg p-3 hover:shadow-md transition-shadow"
          >
            <div class="flex items-center justify-between gap-2">
              <p class="text-sm font-medium text-gray-700 truncate">{{ meta.sensorName || ('传感器#' + meta.id) }}</p>
              <el-tag :type="sensorState(meta).tagType" size="small" effect="light">{{ sensorState(meta).text }}</el-tag>
            </div>
            <div class="mt-2 flex items-end justify-between">
              <p class="text-2xl font-bold leading-none" :style="{ color: sensorState(meta).color }">
                {{ formatValue(sensorData[meta.id] && sensorData[meta.id].value) }}
              </p>
              <span class="text-xs text-gray-400 mb-0.5">
                {{ (sensorData[meta.id] && sensorData[meta.id].unit) || meta.unit || '' }}
              </span>
            </div>
            <div v-if="hasThreshold(meta)" class="mt-2 h-1 bg-gray-100 rounded-full overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-500"
                :style="{ width: sensorPercent(meta) + '%', background: sensorState(meta).color }"
              ></div>
            </div>
            <p class="mt-2 text-xs text-gray-400 flex items-center justify-between">
              <span>{{ typeLabel(meta.monitorType) }}</span>
              <span>{{ sensorData[meta.id] ? ('更新 ' + timeText(sensorData[meta.id].ts)) : '等待数据' }}</span>
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 传感器实时数据组件（塔吊监控 / 升降机监控 / 环境监测 通用）
 *
 * 数据链路（2026-08-17 对照后端实际实现 + 接口文档核对）：
 *   GET /api/sensor/list?parentId&parentType  → 拉取当前设备/监测点下的传感器元数据
 *                                                （后端实际返回驼峰字段 sensorName/monitorType…，
 *                                                 文档示例为下划线 sensor_name/monitor_type…，
 *                                                 组件已做双向兼容：字段归一化 + 参数双传）
 *   WS /api/ws/sensor/subscribe               → 连接后【必须主动发送订阅消息】才会收到推流：
 *                                                {"type":"subscribe","data":{"sensor_ids":[...]}}
 *                                                服务端持续推送 sensor_data（id/monitor_type/value/unit）
 *   通过 id 将 WS 数据与元数据匹配渲染；状态按元数据阈值实时判定（报警/预警/正常/等待数据/离线）。
 *
 * 展示方式：
 *   传感器按 monitor_type 分组（如 风速/起重量/力矩/温度/PM2.5…），
 *   每个类型一个分区，分区内展示该类传感器卡片。
 *
 * 使用方式：
 *   <SensorRealtime :parent-type="1" :parent-id="selectedDevice" />  // 设备下的传感器
 *   <SensorRealtime :parent-type="2" :parent-id="selectedPoint" />   // 监测点下的传感器
 *
 * 生命周期：
 *   - watch parentType/parentId → 退订旧传感器、清空数据、重新拉取元数据、订阅新传感器
 *   - 卸载时清空回调并断开连接（sensorWs 为单例，避免回调残留影响其他页面）
 */
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { sensorApi } from '@/api'
import sensorWs from '@/utils/sensorWs'
import { getToken } from '@/utils/auth'

const props = defineProps({
  // 父节点类型：1-设备（塔吊/升降机），2-监测点（环境监测）
  parentType: { type: Number, default: 1 },
  // 父节点 ID：设备台账 id / 监测点 id
  parentId: { type: [Number, String], default: null }
})
// 2026-08-17 新增：向上抛出传感器实时数据（payload: { id, monitorType, value, unit, ts, state }），
// 供父页面（塔吊/升降机）将传感器值直接填充到现有实时仪表盘，无需新增模块
const emit = defineEmits(['data'])

const sensorList = ref([])       // 传感器元数据列表（后端驼峰字段）
const sensorData = reactive({})  // 传感器 id → { id, monitor_type, value, unit, ts }
const sensorLoading = ref(false)
const sensorWsStatus = ref('idle') // idle/connecting/open/closed/reconnecting
let subscribedIds = []           // 当前已订阅的传感器ID（切换父节点时先退订旧批）

let isAlive = true
onBeforeUnmount(() => {
  isAlive = false
  // 单例连接：卸载时清空回调并断开，避免回调残留覆盖其他页面
  sensorWs.clearCallbacks()
  sensorWs.disconnect()
})

// ---------------- 传感器类型分组元信息（monitor_type 不区分大小写匹配） ----------------
const TYPE_META = {
  // —— 塔吊 ——
  LOAD_WEIGHT:     { label: '起重量',   badge: 'bg-indigo-100 text-indigo-600' },
  AMPLITUDE:       { label: '幅度',     badge: 'bg-sky-100 text-sky-600' },
  MOMENT:          { label: '力矩',     badge: 'bg-violet-100 text-violet-600' },
  MOMENT_PERCENT:  { label: '力矩百分比', badge: 'bg-purple-100 text-purple-600' },
  WIND_SPEED:      { label: '风速',     badge: 'bg-cyan-100 text-cyan-600' },
  HEIGHT:          { label: '高度',     badge: 'bg-teal-100 text-teal-600' },
  SLEW_ANGLE:      { label: '回转角度', badge: 'bg-blue-100 text-blue-600' },
  // —— 升降机 ——
  LOAD_PERCENT:    { label: '载重百分比', badge: 'bg-orange-100 text-orange-600' },
  PASSENGER_COUNT: { label: '乘员数',   badge: 'bg-rose-100 text-rose-600' },
  TILT_ANGLE:      { label: '倾斜角',   badge: 'bg-amber-100 text-amber-600' },
  DOOR_INTERLOCK:  { label: '门联锁',   badge: 'bg-lime-100 text-lime-600' },
  // —— 环境监测 ——
  TEMP:            { label: '温度',     badge: 'bg-red-100 text-red-600' },
  HUMIDITY:        { label: '湿度',     badge: 'bg-blue-100 text-blue-600' },
  PM25:            { label: 'PM2.5',    badge: 'bg-slate-100 text-slate-600' },
  PM10:            { label: 'PM10',     badge: 'bg-gray-100 text-gray-600' },
  NOISE:           { label: '噪声',     badge: 'bg-emerald-100 text-emerald-600' },
  WIND:            { label: '风速',     badge: 'bg-cyan-100 text-cyan-600' }
}

// 分组展示顺序（未知类型排最后）
const TYPE_ORDER = [
  // 塔吊
  'LOAD_WEIGHT', 'MOMENT', 'MOMENT_PERCENT', 'AMPLITUDE',
  'WIND_SPEED', 'HEIGHT', 'SLEW_ANGLE',
  // 升降机
  'LOAD_PERCENT', 'PASSENGER_COUNT', 'TILT_ANGLE', 'DOOR_INTERLOCK',
  // 环境监测
  'TEMP', 'HUMIDITY', 'PM25', 'PM10', 'NOISE', 'WIND'
]

function normalizeType(t) {
  return String(t || '').toUpperCase()
}

function typeLabel(type) {
  const info = TYPE_META[normalizeType(type)]
  return (info && info.label) || '其他'
}

// 按 monitor_type 分组（保持 TYPE_ORDER 顺序）
const sensorGroups = computed(() => {
  const map = new Map()
  sensorList.value.forEach((meta) => {
    const key = normalizeType(meta.monitorType) || 'OTHER'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(meta)
  })
  return [...map.entries()]
    .sort((a, b) => {
      const ia = TYPE_ORDER.indexOf(a[0])
      const ib = TYPE_ORDER.indexOf(b[0])
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
    })
    .map(([type, list]) => ({
      type,
      meta: TYPE_META[type] || { label: '其他', badge: 'bg-gray-100 text-gray-600' },
      list
    }))
})

const sensorStatusMap = {
  idle:         { text: '未连接', type: 'info',    dot: 'bg-gray-300' },
  connecting:   { text: '连接中', type: 'warning', dot: 'bg-amber-400 animate-pulse' },
  open:         { text: '推流中', type: 'success', dot: 'bg-emerald-400' },
  closed:       { text: '已断开', type: 'info',    dot: 'bg-gray-300' },
  reconnecting: { text: '重连中', type: 'warning', dot: 'bg-amber-400 animate-pulse' }
}
const sensorStatusTag = computed(() => sensorStatusMap[sensorWsStatus.value] || sensorStatusMap.idle)

function timeText(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatValue(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100)
}

// 元数据字段命名兼容：后端实际返回驼峰（sensorName/monitorType…），
// 接口文档示例为下划线（sensor_name/monitor_type…），此处统一归一化为驼峰
function normalizeMeta(raw) {
  const pick = (...keys) => {
    for (const k of keys) {
      if (raw[k] != null) return raw[k]
    }
    return undefined
  }
  return {
    id: Number(pick('id')),
    sensorName: pick('sensorName', 'sensor_name'),
    monitorType: pick('monitorType', 'monitor_type'),
    unit: pick('unit'),
    parentId: pick('parentId', 'parent_id'),
    parentType: pick('parentType', 'parent_type'),
    warningUpper: pick('warningUpper', 'warning_upper'),
    warningLower: pick('warningLower', 'warning_lower'),
    alarmUpper: pick('alarmUpper', 'alarm_upper'),
    alarmLower: pick('alarmLower', 'alarm_lower'),
    refreshRate: pick('refreshRate', 'refresh_rate'),
    status: pick('status')
  }
}

// 加载传感器元数据：按父节点类型 + 父节点 ID 过滤
// 参数同时传驼峰与下划线：后端 @RequestParam 绑定驼峰 parentId/parentType（当前实现），
// 文档约定下划线 parent_id/parent_type，双传可兼容两端（Spring 会忽略未绑定参数，不影响）
async function loadSensors() {
  if (props.parentId == null || props.parentId === '') { sensorList.value = []; return }
  sensorLoading.value = true
  try {
    const params = { parentType: props.parentType, parent_type: props.parentType }
    if (props.parentId != null) {
      params.parentId = props.parentId
      params.parent_id = props.parentId
    }
    const res = await sensorApi.getSensorList(params)
    if (!isAlive) return
    sensorList.value = Array.isArray(res.data) ? res.data.map(normalizeMeta) : []
  } catch (_) {
    if (isAlive) { sensorList.value = []; ElMessage.error('加载传感器元数据失败') }
  } finally {
    if (isAlive) sensorLoading.value = false
  }
}

// 建立/复用 WS 连接并绑定回调（订阅动作由 refresh 在元数据加载后触发）
function initSensorWs() {
  const token = getToken()
  if (!token) return
  sensorWs.onStatus = (s) => { if (isAlive) sensorWsStatus.value = s }
  sensorWs.onData = (data) => {
    if (!isAlive || data.id == null) return
    // data: { id: 1, monitor_type: "wind_speed", value: 12.5, unit: "m/s" }
    sensorData[data.id] = { ...data, ts: Date.now() }
    // 2026-08-17 新增：向上抛出传感器实时数据（monitorType 优先取元数据已归一化的驼峰值，
    // 兜底读 WS 推送的 monitor_type/monitorType；state 为阈值判定结果，供父页面联动告警）
    const meta = sensorList.value.find((m) => m.id == data.id)
    emit('data', {
      id: data.id,
      monitorType: (meta && meta.monitorType) || data.monitor_type || data.monitorType,
      value: data.value,
      unit: data.unit || (meta && meta.unit),
      ts: Date.now(),
      state: meta ? sensorState(meta) : null
    })
  }
  sensorWs.onError = (info) => {
    if (!isAlive) return
    if (info?.type === 'server_error' && info?.message) ElMessage.warning(info.message)
  }
  sensorWs.connect(token)
}

// 刷新：退订旧传感器 → 清空数据 → 重新拉取元数据 → 订阅新传感器
async function refresh() {
  if (!isAlive) return
  // 同一连接上先退订上一批传感器，避免累积订阅推送
  if (subscribedIds.length) sensorWs.unsubscribe(subscribedIds)
  subscribedIds = []
  Object.keys(sensorData).forEach((k) => { delete sensorData[k] })
  await loadSensors()
  if (!isAlive) return
  const ids = sensorList.value.map((m) => Number(m.id)).filter((n) => Number.isFinite(n))
  subscribedIds = ids
  if (ids.length) sensorWs.subscribe(ids)
}

// 父节点/切换：退订旧传感器 → 重新加载元数据 → 订阅新传感器
watch(
  () => [props.parentType, props.parentId],
  refresh,
  { immediate: true }
)

initSensorWs()

// 按元数据阈值计算传感器状态：故障/停止 > 报警 > 预警 > 正常 > 等待数据 > 离线
function sensorState(meta) {
  const d = sensorData[meta.id]
  // 传感器自身状态（文档约定 status：1-运行，0-停止，2-故障）
  if (meta.status === 2) return { text: '故障', tagType: 'danger', color: '#ef4444' }
  if (meta.status === 0) return { text: '已停止', tagType: 'info', color: '#9ca3af' }
  // 连接异常/未连接 → 离线
  if (sensorWsStatus.value !== 'open') return { text: '离线', tagType: 'info', color: '#9ca3af' }
  // 已订阅但服务端尚未推送该传感器（或 TCP 尚未上报 → value 为 null）→ 等待数据
  if (!d || d.value == null || d.value === '') return { text: '等待数据', tagType: 'warning', color: '#f59e0b' }
  const v = Number(d.value)
  if (Number.isNaN(v)) return { text: '异常', tagType: 'warning', color: '#f59e0b' }
  const au = toNumber(meta.alarmUpper)
  const al = toNumber(meta.alarmLower)
  const wu = toNumber(meta.warningUpper)
  const wl = toNumber(meta.warningLower)
  if ((au != null && v >= au) || (al != null && v <= al)) {
    return { text: '报警', tagType: 'danger', color: '#ef4444' }
  }
  if ((wu != null && v >= wu) || (wl != null && v <= wl)) {
    return { text: '预警', tagType: 'warning', color: '#f59e0b' }
  }
  return { text: '正常', tagType: 'success', color: '#10b981' }
}

function toNumber(v) {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}

function hasThreshold(meta) {
  return (
    meta.alarmUpper != null || meta.alarmLower != null ||
    meta.warningUpper != null || meta.warningLower != null
  )
}

// 阈值进度条：有上下限按区间比例，只有上限按上限比例
function sensorPercent(meta) {
  const d = sensorData[meta.id]
  if (!d || d.value == null || d.value === '') return 0
  const v = Number(d.value)
  if (Number.isNaN(v)) return 0
  const upper = toNumber(meta.alarmUpper != null ? meta.alarmUpper : meta.warningUpper)
  const lower = toNumber(meta.alarmLower != null ? meta.alarmLower : meta.warningLower)
  const clamp = (n) => Math.max(0, Math.min(100, n))
  if (upper != null && lower != null) {
    if (upper === lower) return 0
    return clamp(((v - lower) / (upper - lower)) * 100)
  }
  if (upper != null) return clamp((v / upper) * 100)
  return 0
}

// 类型分区内的汇总状态（有报警显示报警数，否则有预警显示预警数）
function groupState(group) {
  let alarm = 0
  let warning = 0
  group.list.forEach((meta) => {
    const s = sensorState(meta)
    if (s.text === '报警') alarm++
    else if (s.text === '预警') warning++
  })
  if (alarm > 0) return { text: `${alarm} 个报警`, cls: 'text-red-500' }
  if (warning > 0) return { text: `${warning} 个预警`, cls: 'text-amber-500' }
  return null
}
</script>
