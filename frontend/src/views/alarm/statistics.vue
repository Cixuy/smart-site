<template>
  <div class="page-container space-y-4">
    <!-- 页头 -->
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 class="text-xl font-bold text-gray-800">告警统计分析</h2>
        <p class="mt-1 text-sm text-gray-400">告警总览、趋势、分布与处置及时率 · 支持报表导出</p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <el-tag v-if="projectId" effect="plain" type="info">项目 ID：{{ projectId }}</el-tag>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 250px"
          @change="handleDateChange"
        />
        <el-select v-model="granularity" style="width: 100px" @change="loadAll">
          <el-option v-for="(meta, key) in TREND_GRANULARITY" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-select v-model="exportFormat" style="width: 120px">
          <el-option v-for="(meta, key) in EXPORT_FORMAT" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-button :icon="Download" :loading="exporting" @click="handleExport">导出报表</el-button>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 汇总卡片 -->
    <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div v-for="c in cards" :key="c.key" class="rounded-xl border border-gray-100 bg-white p-4 text-center shadow-sm">
        <p class="text-sm text-gray-400">{{ c.label }}</p>
        <p class="mt-1 text-3xl font-bold" :class="c.color">{{ c.value }}</p>
      </div>
    </div>

    <!-- 趋势图（整行） -->
    <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
      <div class="mb-2 flex items-center justify-between">
        <span class="font-semibold text-gray-700">告警趋势（{{ TREND_GRANULARITY[granularity]?.label || granularity }}）</span>
        <div class="flex flex-wrap gap-4 text-xs text-gray-400">
          <span><i class="mr-1 inline-block h-2 w-2 rounded-full" style="background:#409eff"></i>总数</span>
          <span><i class="mr-1 inline-block h-2 w-2 rounded-full" style="background:#faad14"></i>预警</span>
          <span><i class="mr-1 inline-block h-2 w-2 rounded-full" style="background:#fa8c16"></i>警报</span>
          <span><i class="mr-1 inline-block h-2 w-2 rounded-full" style="background:#ff4d4f"></i>控制</span>
          <span><i class="mr-1 inline-block h-2 w-2 rounded-full" style="background:#52c41a"></i>已处置</span>
        </div>
      </div>
      <div ref="trendChart" style="height: 300px"></div>
    </div>

    <!-- 三个分布饼图 -->
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <span class="font-semibold text-gray-700">处置状态分布</span>
        <div ref="statusChart" style="height: 260px"></div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <span class="font-semibold text-gray-700">告警级别分布</span>
        <div ref="levelChart" style="height: 260px"></div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <span class="font-semibold text-gray-700">告警来源分布</span>
        <div ref="sourceChart" style="height: 260px"></div>
      </div>
    </div>

    <!-- 类型分布 + 处置及时率 -->
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <span class="font-semibold text-gray-700">告警类型分布</span>
        <div ref="typeChart" style="height: 300px"></div>
      </div>

      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <div class="flex items-center justify-between">
          <span class="font-semibold text-gray-700">处置及时率（规定 {{ deadlineMinutes }} 分钟）</span>
          <el-input-number v-model="deadlineMinutes" :min="5" :max="240" :step="5" size="small" style="width: 110px" @change="loadTimeliness" />
        </div>
        <div v-if="timeliness" class="mb-2 mt-3 grid grid-cols-4 gap-2 text-center">
          <div class="rounded-lg bg-gray-50 py-2">
            <p class="text-xs text-gray-400">已处置</p>
            <p class="text-lg font-bold text-gray-800">{{ timeliness.disposedCount ?? 0 }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 py-2">
            <p class="text-xs text-gray-400">及时</p>
            <p class="text-lg font-bold text-green-600">{{ timeliness.timelyCount ?? 0 }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 py-2">
            <p class="text-xs text-gray-400">超时</p>
            <p class="text-lg font-bold text-orange-500">{{ timeliness.overdueCount ?? 0 }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 py-2">
            <p class="text-xs text-gray-400">及时率</p>
            <p class="text-lg font-bold text-blue-600">{{ ((timeliness.timelyRate ?? 0) * 100).toFixed(1) }}%</p>
          </div>
        </div>
        <div ref="timelinessChart" style="height: 220px"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useStore } from 'vuex'
import * as echarts from 'echarts'
import { ElMessageBox } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { alarmApi } from '@/api'
import { ALARM_SOURCE, ALARM_LEVEL, ALARM_STATUS, TREND_GRANULARITY, EXPORT_FORMAT } from '@/constants/alarm'

const store = useStore()
const projectId = computed(() => store.getters.projectId)

/* ---------------- 查询条件 ---------------- */
const dateRange = ref(initDateRange(6))
const granularity = ref('DAY')
const exportFormat = ref('XLSX')
const deadlineMinutes = ref(30)
const loading = ref(false)
const exporting = ref(false)

const overview = ref(null)
const trend = ref(null)
const distribution = ref(null)
const timeliness = ref(null)

let resizeHandler = null
// 模板 ref 必须绑定到 <script setup> 顶层同名变量，放在对象里将拿不到 DOM（图表无法渲染）
const trendChart = ref(null)
const statusChart = ref(null)
const levelChart = ref(null)
const sourceChart = ref(null)
const typeChart = ref(null)
const timelinessChart = ref(null)
const chartInstances = {}

/* ---------------- 时间工具 ---------------- */
function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function initDateRange(days) {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - days)
  return [formatDate(start), formatDate(end)]
}

// 后端 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)，需 ISO-8601 格式（T 分隔）
const startTime = computed(() => (dateRange.value?.[0] ? `${dateRange.value[0]}T00:00:00` : ''))
const endTime = computed(() => (dateRange.value?.[1] ? `${dateRange.value[1]}T23:59:59` : ''))

/* ---------------- 汇总卡片 ---------------- */
const cards = computed(() => {
  const dist = (overview.value?.statusDistribution || []).reduce((map, item) => {
    map[item.dimension] = item.count || 0
    return map
  }, {})
  return [
    { key: 'total', label: '总告警数', value: overview.value?.totalCount ?? 0, color: 'text-gray-800' },
    { key: 'pending', label: '未处置', value: dist['0'] ?? 0, color: 'text-red-500' },
    { key: 'processing', label: '处置中', value: dist['1'] ?? 0, color: 'text-orange-500' },
    { key: 'resolved', label: '已处置', value: dist['2'] ?? 0, color: 'text-green-600' }
  ]
})

/* ---------------- 数据加载 ---------------- */
/**
 * 前端没有选择项目的功能（projectId 恒为空）。
 * 后端 trend/distribution/timeliness 的 projectId 为必填（overview 则支持全项目），
 * 因此先从告警列表（GET /api/alarms，projectId 可选，不传按数据权限返回全项目）提取一条
 * 真实项目 ID 写入 store/localStorage，作为统计接口的项目维度。
 * @returns {boolean} true=新设置了 projectId（watch 会触发重新加载）；false=未变化
 */
async function ensureProjectId() {
  if (projectId.value) return false
  try {
    const res = await alarmApi.getPageList({ pageNum: 1, pageSize: 1 })
    const data = res.data || {}
    const list = data.records || data.list || []
    const pid = list[0]?.projectId
    if (pid) {
      store.commit('app/SET_PROJECT_ID', pid)
      return true
    }
    return false
  } catch (_) {
    return false
  }
}

async function loadOverview() {
  const res = await alarmApi.getOverview({
    projectId: projectId.value,
    startTime: startTime.value,
    endTime: endTime.value
  })
  overview.value = res.data || {}
}

async function loadTrend() {
  if (!projectId.value) return
  const res = await alarmApi.getTrend({
    projectId: projectId.value,
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1],
    granularity: granularity.value
  })
  trend.value = res.data || {}
}

async function loadDistribution() {
  if (!projectId.value) return
  const res = await alarmApi.getDistribution({
    projectId: projectId.value,
    startTime: startTime.value,
    endTime: endTime.value,
    dimension: 'ALARM_TYPE'
  })
  distribution.value = res.data || {}
}

async function loadTimeliness() {
  if (!projectId.value) return
  const res = await alarmApi.getTimeliness({
    projectId: projectId.value,
    startTime: startTime.value,
    endTime: endTime.value,
    deadlineMinutes: deadlineMinutes.value
  })
  timeliness.value = res.data || {}
}

async function loadAll() {
  loading.value = true
  try {
    // 任一接口失败不阻塞其余图表渲染；projectId 为空时 trend/distribution/timeliness 不发起请求
    await Promise.allSettled([loadOverview(), loadTrend(), loadDistribution(), loadTimeliness()])
    renderCharts()
    // 等待布局完成后再校正一次尺寸，避免个别图表首次渲染宽度为 0 导致不显示
    nextTick(() => Object.values(chartInstances).forEach(c => c.resize()))
  } finally {
    loading.value = false
  }
}

function handleDateChange() {
  loadAll()
}

/** 项目切换后自动重新加载统计 */
watch(projectId, () => loadAll())

/* ---------------- 图表渲染 ---------------- */
function getChart(name) {
  const chartMap = { trendChart, statusChart, levelChart, sourceChart, typeChart, timelinessChart }
  const el = chartMap[name]?.value
  if (!el) return null
  if (!chartInstances[name]) chartInstances[name] = echarts.init(el)
  return chartInstances[name]
}

const PIE_COLORS = ['#409eff', '#52c41a', '#faad14', '#fa8c16', '#ff4d4f', '#13c2c2', '#722ed1', '#eb2f96']

function renderCharts() {
  renderTrend()
  renderStatus()
  renderLevel()
  renderSource()
  renderType()
  renderTimeliness()
}

function renderTrend() {
  const chart = getChart('trendChart')
  const points = trend.value?.points || []
  if (!chart) return
  const empty = !points.length
  chart.setOption({
    graphic: empty
      ? [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: '#909399', fontSize: 14 } }]
      : [],
    tooltip: { trigger: 'axis' },
    legend: { show: false },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: points.map(p => p.periodStart),
      boundaryGap: false,
      axisLabel: { hideOverlap: true, rotate: points.length > 10 ? 40 : 0, fontSize: 11 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '总数', type: 'line', smooth: true, symbolSize: 6, data: points.map(p => p.totalCount || 0), itemStyle: { color: '#409eff' }, areaStyle: { color: 'rgba(64,158,255,0.10)' } },
      { name: '预警', type: 'line', smooth: true, symbolSize: 5, data: points.map(p => p.warningCount || 0), itemStyle: { color: '#faad14' } },
      { name: '警报', type: 'line', smooth: true, symbolSize: 5, data: points.map(p => p.alarmCount || 0), itemStyle: { color: '#fa8c16' } },
      { name: '控制', type: 'line', smooth: true, symbolSize: 5, data: points.map(p => p.controlCount || 0), itemStyle: { color: '#ff4d4f' } },
      { name: '已处置', type: 'line', smooth: true, symbolSize: 5, data: points.map(p => p.resolvedCount || 0), itemStyle: { color: '#52c41a' } }
    ]
  })
}

function distData(list) {
  return (list || []).filter(d => (d.count || 0) > 0)
}

function renderStatus() {
  const chart = getChart('statusChart')
  if (!chart) return
  const data = distData(overview.value?.statusDistribution).map(d => ({
    name: d.label || dictLabel(ALARM_STATUS, d.dimension),
    value: d.count
  }))
  chart.setOption(pieOption(data))
}

function renderLevel() {
  const chart = getChart('levelChart')
  if (!chart) return
  const data = distData(overview.value?.levelDistribution).map(d => ({
    name: d.label || dictLabel(ALARM_LEVEL, d.dimension),
    value: d.count
  }))
  chart.setOption(pieOption(data))
}

function renderSource() {
  const chart = getChart('sourceChart')
  if (!chart) return
  const data = distData(overview.value?.sourceDistribution).map(d => ({
    name: d.label || dictLabel(ALARM_SOURCE, d.dimension),
    value: d.count
  }))
  chart.setOption(pieOption(data))
}

function renderType() {
  const chart = getChart('typeChart')
  if (!chart) return
  const items = (distribution.value?.items || []).filter(d => (d.count || 0) > 0)
  const data = items.map(d => ({ name: d.name || d.key, value: d.count }))
  chart.setOption(pieOption(data))
}

function renderTimeliness() {
  const chart = getChart('timelinessChart')
  if (!chart) return
  const operators = timeliness.value?.operators || []
  const empty = !operators.length
  chart.setOption({
    graphic: empty
      ? [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: '#909399', fontSize: 14 } }]
      : [],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: p => `${p[0].name}<br/>及时率：${p[0].value}%<br/>已处置：${p[0].data.disposed ?? '-'}`
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: operators.map(o => o.operatorName || `ID:${o.operatorId}`) },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      {
        type: 'bar',
        barWidth: 26,
        data: operators.map(o => ({
          value: Number(((o.timelyRate || 0) * 100).toFixed(1)),
          disposed: o.disposedCount,
          itemStyle: {
            color: (o.timelyRate || 0) >= 0.9 ? '#52c41a' : (o.timelyRate || 0) >= 0.6 ? '#faad14' : '#ff4d4f',
            borderRadius: [4, 4, 0, 0]
          }
        })),
        label: { show: true, position: 'top', formatter: '{c}%' }
      }
    ]
  })
}

function pieOption(data) {
  const empty = !data || !data.length
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: { bottom: 0, icon: 'circle', type: 'scroll', pageIconSize: 10 },
    graphic: empty
      ? [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: '#909399', fontSize: 14 } }]
      : [],
    series: [
      {
        type: 'pie',
        radius: ['40%', '66%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%', fontSize: 12, lineHeight: 16 },
        labelLayout: { hideOverlap: true },
        data: empty ? [] : data,
        color: PIE_COLORS
      }
    ]
  }
}

function dictLabel(dict, dimension) {
  const key = dimension === null || dimension === undefined ? null : Number(dimension)
  return (dict[key] && dict[key].label) || '-'
}

/* ---------------- 导出报表 ---------------- */
async function handleExport() {
  if (!projectId.value) return
  try {
    await ElMessageBox.confirm(
      `导出时间范围 ${dateRange.value[0]} ~ ${dateRange.value[1]} 的告警统计报表（${EXPORT_FORMAT[exportFormat.value]?.label || exportFormat.value}）？`,
      '导出确认',
      { type: 'info', confirmButtonText: '确认导出', cancelButtonText: '取消' }
    )
  } catch (_) {
    return
  }
  exporting.value = true
  try {
    const res = await alarmApi.exportStatistics({
      projectId: projectId.value,
      startTime: startTime.value,
      endTime: endTime.value,
      format: exportFormat.value,
      deadlineMinutes: deadlineMinutes.value
    })
    downloadBlob(res, `告警统计报表_${dateRange.value[0]}_${dateRange.value[1]}.${exportFormat.value.toLowerCase()}`)
  } finally {
    exporting.value = false
  }
}

/** 从 blob 响应触发浏览器下载（解析 Content-Disposition 文件名） */
function downloadBlob(res, defaultName) {
  const disposition = res?.headers?.['content-disposition'] || ''
  let filename = defaultName
  const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  if (match) {
    try {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''))
    } catch (_) {
      filename = match[1].replace(/['"]/g, '')
    }
  }
  const blob = new Blob([res.data], { type: res.data?.type || 'application/octet-stream' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/* ---------------- 生命周期 ---------------- */
onMounted(async () => {
  // 进入页面仅加载一次：先确保拿到统计所需的项目 ID
  // - ensureProjectId 新设置了 projectId → watch(projectId) 会自动触发 loadAll
  // - 已有 projectId 或提取失败 → 手动 loadAll（无项目时至少渲染 overview 全项目数据）
  const changed = await ensureProjectId()
  if (!changed) loadAll()
  resizeHandler = () => Object.values(chartInstances).forEach(c => c.resize())
  window.addEventListener('resize', resizeHandler)
})

onUnmounted(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  Object.values(chartInstances).forEach(c => c.dispose())
  Object.keys(chartInstances).forEach(k => delete chartInstances[k])
})
</script>

<style scoped>
.page-container {
  padding: 0;
}
</style>
