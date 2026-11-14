<template>
  <div class="home-dashboard space-y-5">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">工作台首页</h2>
        <p class="text-sm text-gray-400 mt-1">建筑安全智能工地平台</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-400">{{ currentDate }}</span>
      </div>
    </div>

    <!-- 全局搜索结果（来自导航栏搜索跳转 /home?search=xxx） -->
    <div v-if="searchKeyword" class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-3">
        <span class="font-semibold text-gray-700">
          搜索「{{ searchKeyword }}」结果
          <span class="text-sm text-gray-400 ml-1">共 {{ searchResults.length }} 条</span>
        </span>
        <el-button text type="primary" size="small" @click="clearSearch">清除搜索</el-button>
      </div>
      <div v-loading="searchLoading" class="space-y-2">
        <div
          v-for="item in searchResults"
          :key="item.id"
          class="flex items-center gap-3 p-3 rounded-lg border border-gray-100 hover:bg-gray-50 cursor-pointer transition-all"
          @click="gotoSearchResult(item)"
        >
          <el-tag :type="searchTypeTag(item.searchType)" size="small" class="w-14 justify-center">
            {{ searchTypeLabel(item.searchType) }}
          </el-tag>
          <span class="flex-1 text-sm font-medium text-gray-700 truncate">{{ item.title }}</span>
          <span class="text-xs text-gray-400 truncate max-w-[40%]">{{ item.summary }}</span>
        </div>
        <el-empty v-if="!searchLoading && searchResults.length === 0" description="未找到相关内容" :image-size="60" />
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div
        class="stat-card cursor-pointer hover:shadow-md transition-shadow"
        v-for="card in statCards"
        :key="card.key"
        @click="card.url && router.push(card.url)"
      >
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500">{{ card.label }}</p>
            <p class="text-3xl font-bold mt-1" :class="card.color">{{ stats[card.key] ?? '--' }}</p>
          </div>
          <div class="w-12 h-12 rounded-xl flex items-center justify-center" :class="card.bg">
            <el-icon :size="24" :color="card.iconColor"><component :is="card.icon" /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div v-if="shortcuts.length" class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4">
        <span class="font-semibold text-gray-700">快捷入口</span>
        <el-tag size="small" type="info" effect="plain">关键指标</el-tag>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-4 xl:grid-cols-8 gap-3">
        <div
          v-for="s in shortcuts"
          :key="s.key"
          class="shortcut-item group flex flex-col items-center gap-2 rounded-xl border border-gray-100 bg-white p-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-blue-100 hover:shadow-md cursor-pointer"
          @click="handleShortcutClick(s)"
        >
          <span
            class="flex h-11 w-11 items-center justify-center rounded-xl transition-transform duration-200 group-hover:scale-110"
            :style="{ background: shortcutStyle(s.key).iconBg, color: shortcutStyle(s.key).iconColor }"
          >
            <el-icon :size="22"><component :is="shortcutStyle(s.key).icon" /></el-icon>
          </span>
          <span class="text-sm font-medium text-gray-700 text-center leading-tight">{{ s.title }}</span>
          <span class="text-xs text-gray-400 text-center truncate w-full">{{ s.value }}</span>
        </div>
      </div>
    </div>

    <!-- 图表 + 最新告警 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <!-- 告警趋势图（经理面板） -->
      <div v-if="managerPanel" class="lg:col-span-2 bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <div class="flex items-center justify-between mb-4">
          <span class="font-semibold text-gray-700">近7天告警趋势</span>
          <el-tag size="small" type="warning" effect="plain">实时更新</el-tag>
        </div>
        <div ref="chartRef" style="height:280px"></div>
      </div>

      <!-- 最新告警 -->
      <div :class="managerPanel ? '' : 'lg:col-span-2'" class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <div class="flex items-center justify-between mb-4">
          <span class="font-semibold text-gray-700">最新告警</span>
          <el-button text type="primary" size="small" @click="$router.push('/alarm/list')">更多 →</el-button>
        </div>
        <div v-loading="alarmLoading" class="space-y-2 max-h-[300px] overflow-auto">
          <div
            v-for="alarm in latestAlarms"
            :key="alarm.alarmId"
            class="flex items-center gap-3 p-2.5 rounded-lg hover:bg-gray-50 cursor-pointer border border-transparent hover:border-gray-100 transition-all"
            @click="gotoAlarm(alarm)"
          >
            <span class="w-2 h-2 rounded-full flex-shrink-0" :style="{ background: levelColor(alarm.alarmLevel) }"></span>
            <span class="flex-1 text-sm truncate">{{ alarm.alarmType }} · {{ alarm.deviceName }}</span>
            <el-tag :type="levelTag(alarm.alarmLevel)" size="small">{{ levelLabel(alarm.alarmLevel) }}</el-tag>
          </div>
          <el-empty v-if="!alarmLoading && latestAlarms.length === 0" description="暂无告警" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 经理面板：在线率 / 告警处置进度 -->
    <div v-if="managerPanel" class="grid grid-cols-1 md:grid-cols-2 gap-5">
      <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <div class="flex items-center justify-between mb-2">
          <span class="font-semibold text-gray-700">设备在线率</span>
          <span class="text-2xl font-bold text-blue-600">{{ fmtRate(managerPanel.onlineRate) }}%</span>
        </div>
        <el-progress :percentage="fmtRate(managerPanel.onlineRate)" :stroke-width="10" :color="progressColor" />
      </div>
      <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <div class="flex items-center justify-between mb-2">
          <span class="font-semibold text-gray-700">告警处置进度</span>
          <span class="text-2xl font-bold text-green-600">{{ fmtRate(managerPanel.disposalProgress) }}%</span>
        </div>
        <el-progress :percentage="fmtRate(managerPanel.disposalProgress)" :stroke-width="10" :color="progressColor" />
      </div>
    </div>

    <!-- 安全管理员面板 -->
    <div v-if="safetyAdminPanel" class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <!-- 待办告警 -->
      <div class="lg:col-span-2 bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <div class="flex items-center justify-between mb-4">
          <span class="font-semibold text-gray-700">待办告警</span>
          <el-tag size="small" type="danger" effect="plain">待处置</el-tag>
        </div>
        <div class="space-y-2 max-h-[300px] overflow-auto">
          <div
            v-for="alarm in todoAlarms"
            :key="alarm.alarmId"
            class="flex items-center gap-3 p-2.5 rounded-lg hover:bg-gray-50 cursor-pointer border border-transparent hover:border-gray-100 transition-all"
            @click="alarm.disposalUrl && router.push(alarm.disposalUrl)"
          >
            <span class="w-2 h-2 rounded-full flex-shrink-0" :style="{ background: levelColor(alarm.alarmLevel) }"></span>
            <div class="flex-1 min-w-0">
              <p class="text-sm truncate">{{ alarm.alarmType }} · {{ alarm.deviceName }}</p>
              <p class="text-xs text-gray-400 mt-0.5">{{ formatTime(alarm.alarmTime) }}</p>
            </div>
            <el-tag :type="alarm.status === 'processing' ? 'warning' : 'danger'" size="small">
              {{ alarm.status === 'processing' ? '处理中' : '待处理' }}
            </el-tag>
          </div>
          <el-empty v-if="todoAlarms.length === 0" description="暂无待办告警" :image-size="60" />
        </div>
      </div>

      <!-- 任务统计 -->
      <div class="space-y-5">
        <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
          <div class="flex items-center justify-between">
            <span class="font-semibold text-gray-700">巡检任务</span>
            <span class="text-3xl font-bold text-orange-500">{{ safetyAdminPanel.patrolTaskCount ?? 0 }}</span>
          </div>
          <p class="text-xs text-gray-400 mt-1">巡检任务数</p>
        </div>
        <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
          <div class="flex items-center justify-between">
            <span class="font-semibold text-gray-700">喷淋记录</span>
            <span class="text-3xl font-bold text-blue-500">{{ safetyAdminPanel.sprayRecordCount ?? 0 }}</span>
          </div>
          <p class="text-xs text-gray-400 mt-1">降尘喷淋启动记录数</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { workbenchApi } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useStore()

const chartRef = ref(null)
let chartInstance = null
const alarmLoading = ref(false)
const overviewLoading = ref(false)

const currentDate = computed(() => new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }))
const projectId = computed(() => store.state.app.projectId)

// 概览数据
const stats = reactive({ onlineDevices: 0, offlineDevices: 0, dailyAlarms: 0, pendingAlarms: 0 })
const shortcuts = ref([])
const latestAlarms = ref([])
const managerPanel = ref(null)
const safetyAdminPanel = ref(null)
const todoAlarms = computed(() => safetyAdminPanel.value?.todoAlarms || [])

// 统计卡片配置（数据来自 overview.stats）
const statCards = [
  { key: 'onlineDevices', label: '在线设备', color: 'text-green-600', bg: 'bg-green-50', icon: 'CircleCheckFilled', iconColor: '#52c41a', url: '/asset/device' },
  { key: 'offlineDevices', label: '离线设备', color: 'text-gray-500', bg: 'bg-gray-50', icon: 'CircleCloseFilled', iconColor: '#909399', url: '/asset/device' },
  { key: 'dailyAlarms', label: '当日告警', color: 'text-red-600', bg: 'bg-red-50', icon: 'BellFilled', iconColor: '#ff4d4f', url: '/alarm/list' },
  { key: 'pendingAlarms', label: '未处理告警', color: 'text-orange-500', bg: 'bg-orange-50', icon: 'WarningFilled', iconColor: '#fa8c16', url: '/alarm/list' }
]

// ---- 快捷入口：后端业务路径 → 前端真实路由映射 ----
// 后端返回的 targetUrl 是业务路径（如 /devices?status=online），
// 前端实际路由由动态菜单生成（如 /asset/device），这里统一映射并保留 query。
const SHORTCUT_ROUTE_MAP = {
  '/devices': '/asset/device',            // 设备列表（在线/离线）
  '/alarms': '/alarm/list',               // 告警列表
  '/tower-cranes': '/monitor/towerCrane', // 塔吊监控
  '/hoists': '/monitor/hoist',            // 升降机监控
  '/environment': '/monitor/environment'  // 环境监测
}

// 快捷入口展示样式（按后端返回的 key 匹配图标与配色）
const SHORTCUT_STYLE_MAP = {
  online_device:  { icon: 'SuccessFilled',     iconColor: '#52c41a', iconBg: 'linear-gradient(135deg,#f6ffed,#d9f7be)' },
  offline_device: { icon: 'CircleCloseFilled', iconColor: '#909399', iconBg: 'linear-gradient(135deg,#fafafa,#e8e8e8)' },
  daily_alarm:    { icon: 'BellFilled',        iconColor: '#1890ff', iconBg: 'linear-gradient(135deg,#e6f4ff,#bae0ff)' },
  pending_alarm:  { icon: 'WarningFilled',     iconColor: '#fa8c16', iconBg: 'linear-gradient(135deg,#fff7e6,#ffd591)' },
  tower_crane:    { icon: 'Operation',         iconColor: '#722ed1', iconBg: 'linear-gradient(135deg,#f9f0ff,#d3adf7)' },
  hoist:          { icon: 'Upload',            iconColor: '#4f46e5', iconBg: 'linear-gradient(135deg,#eef2ff,#c7d2fe)' },
  environment:    { icon: 'Sunny',             iconColor: '#13c2c2', iconBg: 'linear-gradient(135deg,#e6fffb,#87e8de)' },
  alarm_scroll:   { icon: 'Bell',              iconColor: '#ff4d4f', iconBg: 'linear-gradient(135deg,#fff1f0,#ffa39e)' }
}
const DEFAULT_SHORTCUT_STYLE = { icon: 'Link', iconColor: '#1890ff', iconBg: 'linear-gradient(135deg,#e6f4ff,#bae0ff)' }

function shortcutStyle(key) {
  return SHORTCUT_STYLE_MAP[key] || DEFAULT_SHORTCUT_STYLE
}

// 快捷入口点击：解析 targetUrl（path + query）→ 映射真实路由 → 校验 → 跳转
function handleShortcutClick(s) {
  if (!s?.targetUrl) return
  const [path, queryStr = ''] = s.targetUrl.split('?')
  const realPath = SHORTCUT_ROUTE_MAP[path] || path
  // 命中 catch-all 兜底路由说明目标页面不存在，给出提示而非跳 404
  if (router.resolve(realPath).matched.some(r => r.path === '/:pathMatch(.*)*')) {
    ElMessage.warning('目标页面暂未开通')
    return
  }
  const query = {}
  queryStr.split('&').forEach(kv => {
    const [k, v] = kv.split('=')
    if (k) query[k] = decodeURIComponent(v || '')
  })
  router.push({ path: realPath, query })
}

// ---- 告警级别（warning预警 / alarm报警 / control控制）----
function levelColor(level) {
  const map = { warning: '#faad14', alarm: '#fa8c16', control: '#ff4d4f' }
  return map[level] || '#999'
}
function levelTag(level) {
  const map = { warning: 'warning', alarm: 'warning', control: 'danger' }
  return map[level] || 'info'
}
function levelLabel(level) {
  const map = { warning: '预警', alarm: '报警', control: '控制' }
  return map[level] || '未知'
}

// ---- 数值格式化 ----
function fmtRate(v) {
  return Math.round((Number(v) || 0) * 100) / 100
}
function progressColor(percentage) {
  if (percentage >= 90) return '#52c41a'
  if (percentage >= 60) return '#1890ff'
  return '#fa8c16'
}
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// ---- 数据加载 ----
async function fetchOverview() {
  overviewLoading.value = true
  try {
    const res = await workbenchApi.getOverview({ projectId: projectId.value })
    const d = res.data || {}
    stats.onlineDevices = d.stats?.onlineDevices ?? 0
    stats.offlineDevices = d.stats?.offlineDevices ?? 0
    stats.dailyAlarms = d.stats?.dailyAlarms ?? 0
    stats.pendingAlarms = d.stats?.pendingAlarms ?? 0
    shortcuts.value = d.shortcuts || []
    latestAlarms.value = d.latestAlarms || []
    managerPanel.value = d.managerPanel || null
    safetyAdminPanel.value = d.safetyAdminPanel || null
  } catch (_) { /* ignore */ }
  finally { overviewLoading.value = false }
}

async function fetchLatestAlarms() {
  alarmLoading.value = true
  try {
    const res = await workbenchApi.listLatestAlarms({ projectId: projectId.value, limit: 5 })
    latestAlarms.value = res.data?.records || []
  } catch (_) { latestAlarms.value = [] }
  finally { alarmLoading.value = false }
}

// ---- 告警趋势图（使用 managerPanel.alarmTrend 真实数据）----
function initChart() {
  if (!chartRef.value || !managerPanel.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)

  const trend = managerPanel.value.alarmTrend || []
  // 补齐最近7天日期（无数据的天数补 0）
  const days = []
  const values = []
  const trendMap = {}
  trend.forEach(p => { if (p && p.date) trendMap[p.date] = p.value ?? 0 })
  for (let i = 6; i >= 0; i--) {
    const d = new Date(); d.setDate(d.getDate() - i)
    const key = d.toISOString().slice(0, 10)
    days.push(d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }))
    values.push(trendMap[key] ?? 0)
  }

  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 10, containLabel: true },
    xAxis: { type: 'category', data: days, axisLine: { lineStyle: { color: '#e0e0e0' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f0f0f0' } } },
    series: [{
      data: values, type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: '#1890ff', width: 2 },
      itemStyle: { color: '#1890ff' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(24,144,255,0.25)' }, { offset: 1, color: 'rgba(24,144,255,0.02)' }
      ]) }
    }]
  })
}

function handleResize() { chartInstance?.resize() }

// ---- 全局搜索（导航栏搜索跳转 /home?search=xxx 后展示）----
const searchKeyword = ref('')
const searchResults = ref([])
const searchLoading = ref(false)

const SEARCH_TYPE = {
  1: { label: '设备', tag: 'primary' },
  2: { label: '告警', tag: 'danger' },
  3: { label: '用户', tag: 'success' }
}
function searchTypeLabel(type) { return SEARCH_TYPE[type]?.label || '其他' }
function searchTypeTag(type) { return SEARCH_TYPE[type]?.tag || 'info' }

async function doGlobalSearch(keyword) {
  if (!keyword) {
    searchResults.value = []
    return
  }
  searchLoading.value = true
  try {
    const res = await workbenchApi.globalSearch({ keyword })
    searchResults.value = res.data?.records || []
  } catch (_) { searchResults.value = [] }
  finally { searchLoading.value = false }
}

function clearSearch() {
  router.replace({ path: '/home' })
  searchKeyword.value = ''
  searchResults.value = []
}

function gotoSearchResult(item) {
  if (item.targetUrl) router.push(item.targetUrl)
}

watch(
  () => route.query.search,
  (val) => {
    searchKeyword.value = val || ''
    doGlobalSearch(searchKeyword.value)
  },
  { immediate: true }
)

// ---- 跳转 ----
function gotoAlarm(alarm) {
  // 优先跳转处置页，否则进入告警列表
  router.push({ path: '/alarm/list', query: { id: alarm.alarmId } })
}

// ---- 生命周期 ----
onMounted(async () => {
  await Promise.all([fetchOverview(), fetchLatestAlarms()])
  await nextTick()
  initChart()
  window.addEventListener('resize', handleResize)
})

watch(managerPanel, async () => {
  await nextTick()
  initChart()
})

onUnmounted(() => {
  chartInstance?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #f0f0f0;
}
</style>
