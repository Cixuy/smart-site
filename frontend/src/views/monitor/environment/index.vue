<template>
  <div class="page-container space-y-5">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div><h2 class="text-xl font-bold text-gray-800">环境监测</h2><p class="text-sm text-gray-400 mt-1">实时环境数据监测与阈值配置</p></div>
      <div class="flex gap-3">
        <el-select v-model="selectedPoint" placeholder="选择监测点" style="width:200px" @change="onPointChange" :loading="pointsLoading">
          <el-option v-for="p in points" :key="p.id" :label="p.pointName" :value="p.id" />
        </el-select>
        <!-- 2026-08-15 调整：删除自动刷新开关；告警列表/阈值配置入口按钮统一改为白色（默认样式），弱化入口导航的视觉层级 -->
        <el-button :icon="Warning" @click="$router.push('/monitor/environment/alarms')">告警列表</el-button>
        <el-button :icon="Setting" @click="$router.push('/monitor/environment/thresholds')">阈值配置</el-button>
        <!-- 2026-08-15 新增：环境日统计报表入口，跳转到 daily-stat 子页面 -->
        <el-button type="success" plain :icon="DataAnalysis" @click="$router.push('/monitor/environment/daily-stat')">日统计报表</el-button>
      </div>
    </div>

    <!-- 2026-08-15 新增：监测点列表为空时给出提示（由 points/page 接口动态加载） -->
    <el-alert
      v-if="!pointsLoading && !points.length"
      type="warning"
      :closable="false"
      show-icon
      title="暂无环境监测点，请联系管理员配置"
      class="mb-4"
    />

    <!-- 2026-08-17 新增：传感器实时数据模块（按监测类型分组的传感器卡片，WS 实时推流驱动；
         作为独立展示模块保留，不再桥接已删除的环境指标卡） -->
    <SensorRealtime :parent-type="2" :parent-id="selectedPoint" />

    <!-- 2026-08-15 调整：历史数据（表格）置于历史趋势（图表）上方，指标与时间范围控件集中在历史数据区块 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
        <span class="font-semibold text-gray-700">历史数据</span>
        <div class="flex items-center gap-3">
          <!-- 2026-08-15 新增：history 接口 metricType 为必选，这里与历史趋势共用同一指标选择，切换即重新查询 -->
          <el-radio-group v-model="chartMetric" size="small" @change="fetchHistory">
            <el-radio-button value="pm25">PM2.5</el-radio-button>
            <el-radio-button value="pm10">PM10</el-radio-button>
            <el-radio-button value="temperature">温度</el-radio-button>
            <el-radio-button value="humidity">湿度</el-radio-button>
            <el-radio-button value="noise">噪声</el-radio-button>
            <el-radio-button value="wind">风速</el-radio-button>
          </el-radio-group>
          <!-- 2026-08-15 调整：history 接口 startTime/endTime 为必选，提供时间范围选择（默认当天；开始时间不限，结束时间不超过当前时间） -->
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :clearable="false"
            style="width:360px"
            :disabled-date="disabledDate"
            @change="onTimeRangeChange"
          />
        </div>
      </div>
      <!-- 2026-08-15 调整：列由固定 width 改为 min-width。固定宽度时 el-table 不会拉伸列，
           右侧会空出一大截；改用 min-width 后各列按比例分配剩余空间，撑满整行横轴 -->
      <el-table :data="historyData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="collectTime" label="采集时间" min-width="180" />
        <el-table-column prop="metricValue" label="数值" min-width="100" />
        <el-table-column prop="unit" label="单位" min-width="80" />
        <el-table-column prop="statusText" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'danger' : row.status === 1 ? 'warning' : 'success'" size="small">
              {{ row.statusText || '正常' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt-4 justify-end" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchHistory" />
    </div>

    <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4">
        <span class="font-semibold text-gray-700">历史趋势</span>
        <!-- 2026-08-15 调整：指标切换已上移至「历史数据」区块统一控制，此处仅保留图表 -->
      </div>
      <div ref="chartRef" style="height:300px"></div>
      <!-- 2026-08-15 新增：图表下方展示当前查询的日期范围，便于确认趋势图对应的时间段 -->
      <div class="mt-3 text-xs text-gray-400 text-center" v-if="dateRangeText">
        查询日期：{{ dateRangeText }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
// 2026-08-15 修复：补充 ElMessage 导入。此前 onTimeRangeChange 中调用
// ElMessage.warning 但未导入，触发时间范围超限时抛 ReferenceError 导致页面报错。
import { ElMessage } from 'element-plus'
import { Setting, DataAnalysis, Warning } from '@element-plus/icons-vue'
import { environmentApi } from '@/api'
// 2026-08-17 新增：传感器实时推流组件（塔吊/升降机/环境监测通用，通过 parentType/parentId 过滤）
import SensorRealtime from '../components/SensorRealtime.vue'

let isAlive = true
onBeforeUnmount(() => { isAlive = false })

const selectedPoint = ref(null)
const pointsLoading = ref(false)

// 2026-08-15 按新API文档改造：监测点改由 /api/env-monitor/points/page 动态加载，不再写死
const points = ref([])

// 分页查询监测点。
// 2026-08-15 修正：实际接口 /api/env-monitor/points/page 返回 data.list（元素含
// monitorPointId/pointName，并附带最新监测数据），而非文档中假设的 data.records + name 字段，
// 且同一监测点可能因多个指标返回多条记录，故按 monitorPointId 去重后再渲染下拉框。
async function loadPoints() {
  pointsLoading.value = true
  try {
    // 2026-08-15 修改：pageSize 保持 100（后端 points/page 接口限制 pageSize 最大 100，超过报 arg1 校验错误）
    const res = await environmentApi.getMonitorPoints({ pageNum: 1, pageSize: 100 })
    if (!isAlive) return
    // 兼容 list / records 两种容器，优先按实际返回的 list 解析
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
    initDefaultPoint()
  } catch (_) { /* 接口异常时保留空列表，页面显示空提示 */ }
  finally { if (isAlive) pointsLoading.value = false }
}

function initDefaultPoint() {
  if (!selectedPoint.value && points.value.length) {
    selectedPoint.value = points.value[0].id
  }
}

function onPointChange() {
  fetchHistory()
  // 2026-08-17 说明：传感器推流由 SensorRealtime 组件内部 watch parentId 自动刷新，无需在此处理
}
const chartRef = ref(null); let chartInstance = null
const chartMetric = ref('pm25')
const loading = ref(false); const historyData = ref([])
const pageNum = ref(1); const pageSize = ref(10); const total = ref(0)

// 2026-08-15 新增：历史查询时间范围（history 接口 startTime/endTime 必选），默认当天 00:00:00 ~ 当前时间
const timeRange = ref([])
// 2026-08-15 新增：图表下方展示的查询日期范围（取 timeRange 的日期部分，跨天时显示区间）
const dateRangeText = computed(() => {
  const [start, end] = timeRange.value || []
  if (!start || !end) return ''
  const s = String(start).slice(0, 10)
  const e = String(end).slice(0, 10)
  return s === e ? s : `${s} 至 ${e}`
})
// 2026-08-15 调整：按用户需求改为「开始时间不限，结束时间不超过当前时间」。
// 后端 history 接口仅校验 start/end 非空且 end 不早于 start（见 EnvMonitorService.validateTimeRange），
// 并无 7 天窗口限制，故移除前端此前加的最早时间与 7 天上限限制。
// 最晚时间 = 当前时间（历史数据不可能存在于未来）。
function getHistoryMaxTime() {
  return Date.now()
}
// 2026-08-15 调整：日期选择器只禁用未来日期（> 当前时间）。
// 开始时间不再设下限，用户可自由选择任意过去时间；当天内的具体时刻由 onTimeRangeChange 兜底校验。
function disabledDate(date) {
  const maxTime = getHistoryMaxTime()
  return date.getTime() > maxTime
}
// 初始化默认时间范围为当天
function initTimeRange() {
  const { startTime, endTime } = getTodayRange()
  timeRange.value = [startTime, endTime]
}
// 2026-08-15 调整：时间范围变更校验（移除最早时间与 7 天窗口限制）：
// 1. 结束时间不能晚于当前时间（未来无数据）；2. 开始时间不能晚于结束时间。
// 任一不合法则回退当天范围并提示。
function onTimeRangeChange() {
  const [start, end] = timeRange.value || []
  if (!start || !end) { initTimeRange(); return }
  const startTime = new Date(start).getTime()
  const endTime = new Date(end).getTime()
  const now = Date.now()
  // 结束时间不能选未来时刻
  if (endTime > now) {
    ElMessage.warning('结束时间不能晚于当前时间')
    initTimeRange()
    return
  }
  // 开始时间不能晚于结束时间
  if (startTime > endTime) {
    ElMessage.warning('开始时间不能晚于结束时间')
    initTimeRange()
    return
  }
  pageNum.value = 1
  fetchHistory()
}

// chartMetric → API metricType 映射
// 2026-08-15 按新API文档改造：补充 PM10、风速(WIND)
const chartToApiMetric = { pm25: 'PM25', pm10: 'PM10', temperature: 'TEMP', humidity: 'HUMIDITY', noise: 'NOISE', wind: 'WIND' }
function toApiMetric(chartKey) {
  return chartToApiMetric[chartKey] || chartKey.toUpperCase()
}

// 获取当天时间范围
function getTodayRange() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const pad = (n) => String(n).padStart(2, '0')
  const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  return { startTime: fmt(start), endTime: fmt(now) }
}

async function fetchHistory() {
  if (!isAlive || selectedPoint.value == null) return
  loading.value = true
  try {
    // 2026-08-15 修改：不再写死当天范围，改用页面上用户选择的时间范围（history 接口 startTime/endTime 必选）
    const [startTime, endTime] = timeRange.value || []
    if (!startTime || !endTime) { loading.value = false; return }
    // 2026-08-15 修复：monitorPointId 直接传原始值，避免 int64 长 ID 经 Number() 转换丢精度
    const res = await environmentApi.getHistory({
      monitorPointId: selectedPoint.value,
      metricType: toApiMetric(chartMetric.value),
      startTime,
      endTime,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (!isAlive) return
    // 2026-08-15 修正：与 points/page 一致，接口实际返回 data.list（元素含
    // collectTime/metricValue/unit/status 等），此处兼容 records / list / 裸数组三种结构
    const records = Array.isArray(res.data?.records)
      ? res.data.records
      : (Array.isArray(res.data?.list) ? res.data.list : (Array.isArray(res.data) ? res.data : []))
    historyData.value = records
    total.value = res.data?.total || records.length
    updateChart(records)
  } catch (_) { if (isAlive) historyData.value = [] }
  finally { if (isAlive) loading.value = false }
}

function updateChart(records) {
  if (!chartInstance) return
  const list = Array.isArray(records) ? records : []
  // 按时间升序排列
  const sorted = [...list].sort((a, b) => (a.collectTime || '').localeCompare(b.collectTime || ''))
  // 2026-08-15 调整：预计算每个日期在数据中第一次出现的索引，
  // 仅在这些刻度前附加 MM-DD 日期，其余刻度只显示 HH:mm 时间
  const dayFirstIndex = new Map()
  sorted.forEach((r, i) => {
    const t = r.collectTime || ''
    const d = t.slice(0, 10)
    if (d && !dayFirstIndex.has(d)) dayFirstIndex.set(d, i)
  })
  const times = sorted.map(r => {
    const t = r.collectTime || ''
    // 提取 HH:mm 部分
    const m = t.match(/(\d{2}):(\d{2})/)
    return m ? `${m[1]}:${m[2]}` : t.slice(0, 16)
  })
  const data = sorted.map(r => r.metricValue ?? null)
  const unit = sorted[0]?.unit || ''
  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      // 2026-08-15 新增：tooltip 展示完整采集时间（含日期），跨天查询时便于区分
      formatter: params => {
        const p = Array.isArray(params) ? params[0] : null
        if (!p) return ''
        const full = sorted[p.dataIndex]?.collectTime || p.axisValue
        const val = p.data == null ? '-' : p.data
        return `${full}<br/>数值：${val} ${unit}`.trim()
      }
    },
    // 2026-08-15 修正：bottom 由 3% 加大，避免跨天时两行 X 轴标签被底部空间裁剪而不显示
    grid: { left: '3%', right: '4%', bottom: '6%', containLabel: true },
    xAxis: {
      type: 'category',
      data: times.length ? times : ['暂无数据'],
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisTick: { alignWithLabel: true },
      // 2026-08-15 修正：去掉 crossDay 分支，日期显示不依赖是否跨天——
      // 始终在每天的第一个刻度前附加 MM-DD（如 08-12 14:30），当天其余刻度只显示 HH:mm
      axisLabel: {
        formatter: (val, idx) => {
          const t = sorted[idx]?.collectTime || ''
          const d = t.slice(5, 10) // MM-DD
          const h = t.slice(11, 16) // HH:mm
          if (d && h) {
            // 当前刻度是该日期第一次出现的位置时，前面加日期；否则仅显示时间
            return dayFirstIndex.get(t.slice(0, 10)) === idx ? `${d} ${h}` : h
          }
          return val
        },
        lineHeight: 16,
        color: '#909399'
      }
    },
    yAxis: { type: 'value', name: unit },
    series: [{
      data: data.length ? data : [],
      type: 'line', smooth: true,
      areaStyle: { color: 'rgba(24,144,255,0.1)' },
      itemStyle: { color: '#1890ff' }
    }]
  })
}

onMounted(async () => {
  // 2026-08-15 改造：先动态加载监测点，再初始化图表并拉取历史数据
  await loadPoints()
  // 2026-08-15 新增：初始化历史查询时间范围（默认当天），必须在 fetchHistory 前设置
  initTimeRange()
  await nextTick()
  if (chartRef.value) chartInstance = echarts.init(chartRef.value)
  await fetchHistory()
  // 2026-08-17 说明：传感器推流由 SensorRealtime 组件内部 watch selectedPoint 自动初始化，无需在此处理
})
onUnmounted(() => { chartInstance?.dispose() })
</script>

<style scoped>.page-container { padding: 0; }</style>
