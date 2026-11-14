<template>
  <div class="page-container space-y-5">
    <!-- 顶部标题栏：返回按钮 + 标题 + 监测点选择 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div class="flex items-center gap-3">
        <el-button :icon="ArrowLeft" circle text @click="router.push('/monitor/environment')" />
        <div>
          <h2 class="text-xl font-bold text-gray-800">环境日统计报表</h2>
          <p class="text-sm text-gray-400 mt-1">按监测点与指标查看每日最大、最小、平均及告警次数</p>
        </div>
      </div>
      <el-select v-model="selectedPoint" placeholder="选择监测点" style="width:220px" :loading="pointsLoading" @change="handleSearch">
        <el-option v-for="p in points" :key="p.id" :label="p.pointName" :value="p.id" />
      </el-select>
    </div>

    <!-- 筛选区：指标 + 日期范围 + 查询/重置 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query">
        <el-form-item label="指标类型">
          <el-radio-group v-model="query.metricType" @change="handleSearch">
            <el-radio-button v-for="m in metricOptions" :key="m.value" :value="m.value">{{ m.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="query.dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ==================== 日统计趋势区块（2026-08-15 临时注释禁用） ====================
         禁用原因：daily-stat/trend 接口对部分监测点校验失败，返回"环境监测点或设备传感器不存在"。
         如需恢复：取消本注释，并同步放开 script 中趋势相关代码（echarts import、chartRef、
         trendLoading/trendInfo、fetchTrend/updateChart、handleSearch 内 fetchTrend 调用、
         onMounted 中图表初始化与 onUnmounted 中 dispose）。
    <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4">
        <span class="font-semibold text-gray-700">日统计趋势</span>
        <span class="text-xs text-gray-400">{{ trendInfo }}</span>
      </div>
      <div ref="chartRef" style="height:320px" v-loading="trendLoading"></div>
    </div>
    ==================== 日统计趋势区块结束 ==================== -->

    <!-- 统计表格：GET /api/env-monitor/daily-stat（每日最大/最小/平均/告警次数） -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <div class="mb-4"><span class="font-semibold text-gray-700">日统计报表</span></div>
      <el-table :data="statRecords" v-loading="statLoading" stripe border style="width:100%">
        <el-table-column prop="statDate" label="统计日期" width="140" />
        <el-table-column prop="pointName" label="监测点" minWidth="160" />
        <el-table-column prop="metricTypeText" label="指标" width="100" />
        <el-table-column prop="maxValue" label="最大值" width="110" align="right">
          <template #default="{ row }"><span>{{ fmt(row.maxValue) }}</span></template>
        </el-table-column>
        <el-table-column prop="minValue" label="最小值" width="110" align="right">
          <template #default="{ row }"><span>{{ fmt(row.minValue) }}</span></template>
        </el-table-column>
        <el-table-column prop="avgValue" label="平均值" width="110" align="right">
          <template #default="{ row }"><span>{{ fmt(row.avgValue) }}</span></template>
        </el-table-column>
        <el-table-column prop="alarmCount" label="告警次数" width="100" align="center">
          <template #default="{ row }"><el-tag v-if="row.alarmCount > 0" type="danger" size="small">{{ row.alarmCount }}</el-tag><span v-else>0</span></template>
        </el-table-column>
        <el-table-column prop="warnCount" label="预警次数" width="100" align="center">
          <template #default="{ row }"><el-tag v-if="row.warnCount > 0" type="warning" size="small">{{ row.warnCount }}</el-tag><span v-else>0</span></template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt-4 justify-end" v-model:current-page="pageNum" v-model:page-size="pageSize"
        :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchStats" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
// 2026-08-15 注释：echarts 仅供趋势图使用，趋势图暂禁用；恢复趋势图时取消注释
// import * as echarts from 'echarts'
import { ArrowLeft, Search, Refresh } from '@element-plus/icons-vue'
import { environmentApi } from '@/api'

const router = useRouter()
let isAlive = true
onBeforeUnmount(() => { isAlive = false })

// ==================== 监测点加载（复用主页面逻辑） ====================
// 2026-08-15 新增：日统计子页面需要选择监测点，从 /api/env-monitor/points/page 动态加载，
// 实际返回 data.list（元素含 monitorPointId/pointName），按 monitorPointId 去重
const points = ref([])
const pointsLoading = ref(false)
const selectedPoint = ref(null)
async function loadPoints() {
  pointsLoading.value = true
  try {
    // 2026-08-15 修改：pageSize 保持 100（后端 points/page 接口限制 pageSize 最大 100，超过报 arg1 校验错误）
    const res = await environmentApi.getMonitorPoints({ pageNum: 1, pageSize: 100 })
    if (!isAlive) return
    const list = Array.isArray(res.data?.list)
      ? res.data.list
      : (Array.isArray(res.data?.records) ? res.data.records : [])
    const map = new Map()
    list.forEach(p => {
      const id = p.monitorPointId ?? p.id
      if (id == null || map.has(id)) return
      map.set(id, { id, pointName: p.pointName || p.name || `监测点${id}` })
    })
    points.value = [...map.values()]
    if (!selectedPoint.value && points.value.length) selectedPoint.value = points.value[0].id
  } catch (_) { /* 接口异常时保留空列表 */ }
  finally { if (isAlive) pointsLoading.value = false }
}

// ==================== 筛选条件 ====================
// 2026-08-15 新增：metricType 枚举与接口文档一致（PM25/PM10/NOISE/TEMP/HUMIDITY/WIND）
const metricOptions = [
  { value: 'PM25', label: 'PM2.5' },
  { value: 'PM10', label: 'PM10' },
  { value: 'NOISE', label: '噪声' },
  { value: 'TEMP', label: '温度' },
  { value: 'HUMIDITY', label: '湿度' },
  { value: 'WIND', label: '风速' }
]
const METRIC_TEXT = Object.fromEntries(metricOptions.map(m => [m.value, m.label]))

// 默认日期范围：近 7 天（今天往前推 6 天）
function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  const pad = n => String(n).padStart(2, '0')
  const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return [fmt(start), fmt(end)]
}

const query = reactive({
  metricType: 'PM25',
  dateRange: defaultRange()
})

// ==================== 趋势图（2026-08-15 临时注释禁用，恢复时取消整段注释） ====================
// 禁用原因：daily-stat/trend 接口对部分监测点返回"环境监测点或设备传感器不存在"，
// 与模板中趋势区块同步禁用，避免请求失败弹错。
// const chartRef = ref(null)
// let chartInstance = null
// const trendLoading = ref(false)
// const trendInfo = ref('')

// // GET /api/env-monitor/daily-stat/trend：monitorPointId/startDate/endDate/metricType 均为必填
// async function fetchTrend() {
//   if (!isAlive || selectedPoint.value == null) return
//   const [startDate, endDate] = query.dateRange || []
//   if (!startDate || !endDate) { trendInfo.value = '请选择日期范围'; return }
//   trendLoading.value = true
//   try {
//     // 2026-08-15 修复：monitorPointId 为 int64，若为雪花长 ID 则 Number() 强转会丢精度，
//     // 导致后端查不到该监测点而报"环境监测点或设备传感器不存在"，改为直接传原始值
//     const res = await environmentApi.getDailyTrend({
//       monitorPointId: selectedPoint.value,
//       startDate,
//       endDate,
//       metricType: query.metricType
//     })
//     if (!isAlive) return
//     const data = res.data || {}
//     const trend = Array.isArray(data.trend) ? data.trend : []
//     // 标题栏展示当前查询的监测点 + 指标（宽松字符串比较，兼容 number/string 类型 ID）
//     const pt = points.value.find(p => String(p.id) === String(selectedPoint.value))
//     trendInfo.value = `${pt?.pointName || data.pointName || ''} · ${METRIC_TEXT[data.metricType] || query.metricType}（${data.unit || ''}）`
//     updateChart(trend, data.unit || '')
//   } catch (_) { if (isAlive) updateChart([], '') }
//   finally { if (isAlive) trendLoading.value = false }
// }

// function updateChart(trend, unit) {
//   if (!chartInstance) return
//   const list = Array.isArray(trend) ? trend : []
//   const dates = list.map(t => t.date || '')
//   chartInstance.setOption({
//     tooltip: {
//       trigger: 'axis',
//       formatter: params => {
//         const p = Array.isArray(params) ? params[0] : null
//         if (!p) return ''
//         const row = list[p.dataIndex] || {}
//         return `${row.date || ''}<br/>最大：${fmt(row.maxValue)} ${unit}<br/>平均：${fmt(row.avgValue)} ${unit}<br/>最小：${fmt(row.minValue)} ${unit}`
//       }
//     },
//     legend: { data: ['最大值', '平均值', '最小值'], bottom: 0 },
//     grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
//     xAxis: {
//       type: 'category',
//       data: dates.length ? dates : ['暂无数据'],
//       axisLine: { lineStyle: { color: '#dcdfe6' } },
//       axisTick: { alignWithLabel: true },
//       axisLabel: { color: '#909399' }
//     },
//     yAxis: { type: 'value', name: unit },
//     series: [
//       { name: '最大值', data: list.map(t => t.maxValue ?? null), type: 'line', smooth: true, itemStyle: { color: '#f5222d' } },
//       { name: '平均值', data: list.map(t => t.avgValue ?? null), type: 'line', smooth: true, itemStyle: { color: '#1890ff' } },
//       { name: '最小值', data: list.map(t => t.minValue ?? null), type: 'line', smooth: true, itemStyle: { color: '#52c41a' } }
//     ]
//   })
// }

// ==================== 统计表格 ====================
const statRecords = ref([])
const statLoading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// GET /api/env-monitor/daily-stat：monitorPointId/startDate/endDate/metricType 可选，分页返回
async function fetchStats() {
  if (!isAlive || selectedPoint.value == null) return
  const [startDate, endDate] = query.dateRange || []
  if (!startDate || !endDate) { statRecords.value = []; total.value = 0; return }
  statLoading.value = true
  try {
    // 2026-08-15 修复：同 fetchTrend，monitorPointId 直接传原始值避免 int64 精度丢失
    const res = await environmentApi.getDailyStat({
      monitorPointId: selectedPoint.value,
      startDate,
      endDate,
      metricType: query.metricType,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (!isAlive) return
    // 兼容 records / list 两种容器，元素补充指标中文名
    const records = Array.isArray(res.data?.records)
      ? res.data.records
      : (Array.isArray(res.data?.list) ? res.data.list : [])
    statRecords.value = records.map(r => ({ ...r, metricTypeText: METRIC_TEXT[r.metricType] || r.metricType || '--' }))
    total.value = res.data?.total || records.length
  } catch (_) { if (isAlive) { statRecords.value = []; total.value = 0 } }
  finally { if (isAlive) statLoading.value = false }
}

// 数值格式化：null/undefined 显示 --
function fmt(v) {
  return v == null ? '--' : v
}

// 查询（切换监测点/指标/日期均触发）：回到第一页并同时刷新趋势图和表格
function handleSearch() {
  pageNum.value = 1
  // 2026-08-15 注释：趋势图暂禁用，不再请求 trend 接口
  // fetchTrend()
  fetchStats()
}

function handleReset() {
  query.metricType = 'PM25'
  query.dateRange = defaultRange()
  handleSearch()
}

onMounted(async () => {
  await loadPoints()
  // 2026-08-15 注释：趋势图暂禁用，不再初始化 echarts 实例与请求 trend 接口
  // await nextTick()
  // if (chartRef.value) chartInstance = echarts.init(chartRef.value)
  // await fetchTrend()
  await fetchStats()
})

// 2026-08-15 注释：趋势图暂禁用，无需销毁图表实例
// onUnmounted(() => { chartInstance?.dispose() })
</script>

<style scoped>.page-container { padding: 0; }</style>
