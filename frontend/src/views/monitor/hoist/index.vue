<template>
  <div class="page-container space-y-5">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">升降机实时监控</h2>
        <p class="text-sm text-gray-400 mt-1">实时监测施工升降机运行状态</p>
      </div>
      <div class="flex items-center gap-3">
        <el-select v-model="selectedDevice" placeholder="选择升降机" style="width:220px" @change="onDeviceChange" :loading="deviceLoading">
          <el-option v-for="d in deviceList" :key="d.deviceId" :label="d.deviceName" :value="d.deviceId" />
        </el-select>
        <!-- 2026-08-15 新增：升降机联锁告警列表入口（白色按钮，与阈值配置保持一致） -->
        <el-button :icon="Warning" @click="router.push('/monitor/hoist/alarms')">告警列表</el-button>
        <el-button :icon="Setting" @click="router.push('/monitor/hoist/thresholds')">阈值配置</el-button>
        <!-- 2026-08-15 调整：与塔吊同步——后端 WS 推送接口暂未就绪（已删除、新接口开发中），
             实时数据暂时回退为 latest 接口定时轮询驱动，此处不再展示 WS 连接状态；
             新接口就绪后恢复脚本中的 connectWs -->
      </div>
    </div>

    <!-- 联锁告警状态条 -->
    <div v-if="deviceAlarm" class="rounded-xl px-4 py-3 bg-red-50 border border-red-200 text-red-500 text-sm">
      ⚠ 设备当前处于联锁告警状态：{{ alarmText }}
    </div>

    <!-- 2026-08-17 新增：传感器实时数据模块（按监测类型分组的传感器卡片，WS 实时推流驱动；
         作为独立展示模块保留，不再桥接已删除的实时状态面板） -->
    <SensorRealtime :parent-type="1" :parent-id="selectedDevice" />

    <!-- 2026-08-15 拆分：将原"运行曲线（历史数据）"拆为两个独立模块——
         1) 历史数据：表格分页展示所选时间范围内的采集记录，时间范围控件集中在此区块；
         2) 运行曲线：纯图表，与历史数据共用同一时间范围，互不耦合 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
        <span class="font-semibold text-gray-700">历史数据</span>
        <div class="flex items-center gap-3">
          <!-- 2026-08-15 调整：时间范围由"快捷档位"改为起止时间选择（history 接口 startTime/endTime 必选，
               默认当天；开始时间不限，结束时间不超过当前时间） -->
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
      <!-- 2026-08-15 新增：历史数据表格，列与 history 接口返回字段一一对应，min-width 撑满整行 -->
      <el-table :data="historyData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="collectTime" label="采集时间" min-width="170" />
        <el-table-column prop="loadWeight" label="载重(T)" min-width="90" />
        <el-table-column prop="loadPercent" label="载重百分比(%)" min-width="110" />
        <el-table-column prop="height" label="高度(m)" min-width="90" />
        <el-table-column prop="runDirectionText" label="运行方向" min-width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.runDirection) === 1 ? 'success' : Number(row.runDirection) === -1 ? 'warning' : 'info'" size="small">
              {{ row.runDirectionText || '停止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="前门" min-width="80">
          <template #default="{ row }">
            <el-tag :type="Number(row.frontDoorStatus) === 0 ? 'success' : 'danger'" size="small">
              {{ Number(row.frontDoorStatus) === 0 ? '关闭' : '打开' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="后门" min-width="80">
          <template #default="{ row }">
            <el-tag :type="Number(row.rearDoorStatus) === 0 ? 'success' : 'danger'" size="small">
              {{ Number(row.rearDoorStatus) === 0 ? '关闭' : '打开' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="passengerCount" label="载人数" min-width="90" />
        <el-table-column prop="windSpeed" label="风速(m/s)" min-width="90" />
        <el-table-column label="联锁告警" min-width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.interlockAlarm) === 0 ? 'success' : 'danger'" size="small">
              {{ Number(row.interlockAlarm) === 0 ? '正常' : '告警' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt-4 justify-end" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10,20,50,100]" layout="total,sizes,prev,pager,next" @change="fetchHistory" />
    </div>

    <!-- 运行曲线：纯历史数据图表，与上方历史数据共用同一时间范围 -->
    <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
        <span class="font-semibold text-gray-700">运行曲线</span>
        <!-- 2026-08-15 调整：支持选择单个指标参数展示曲线（原固定载重+高度两条线） -->
        <el-radio-group v-model="chartMetric" size="small" @change="onMetricChange">
          <el-radio-button v-for="m in metricOptions" :key="m.key" :value="m.key">{{ m.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="chartRef" style="height:280px"></div>
      <!-- 2026-08-15 新增：图表下方展示当前查询的日期范围，便于确认曲线对应的时间段 -->
      <div class="mt-3 text-xs text-gray-400 text-center" v-if="dateRangeText">
        查询日期：{{ dateRangeText }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Setting, Warning } from '@element-plus/icons-vue'
// 2026-08-15 修改：设备下拉框数据源由 latest 接口（依赖实时表有数据）改为设备台账接口 assetApi.getDevicePage，
// typeId=8 为升降机设备类型，台账已登记的设备即可出现在下拉框，不再受实时数据影响
import { hoistApi, assetApi } from '@/api'
// 2026-08-17 新增：传感器实时推流组件（塔吊/升降机/环境监测通用，通过 parentType/parentId 过滤）
import SensorRealtime from '../components/SensorRealtime.vue'
// 2026-08-15 调整：与塔吊同步——升降机 WS 推送接口暂未就绪（后端已删除、新接口开发中），
// 实时数据暂时回退为 latest 接口定时轮询驱动仪表盘（utils/hoistWs.js 已保留）。
// 新 WS 接口就绪后，恢复以下引入与下方 connectWs 接入代码即可：
//   import hoistWs from '@/utils/hoistWs'
//   import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()

let isAlive = true
onBeforeUnmount(() => { isAlive = false })

const selectedDevice = ref('')
const deviceList = ref([])
const deviceLoading = ref(false)
const chartRef = ref(null)
let chartInstance = null

// 联锁告警状态（interlockAlarm > 0 表示已触发，1-双门同开 2-超载 3-超速 4-超高）
const deviceAlarm = ref(false)
const alarmText = ref('正常')

// 2026-08-15 调整：实时刷新暂由 latest 接口定时轮询驱动（30s），顶部 WS 连接状态标签已随
// WS 接入一并移除。新 WS 接口就绪后恢复以下状态逻辑（并配合 connectWs 中 onStatus 更新）：
//   const wsStatus = ref('idle')
//   const wsStatusTag = computed(() => { ... 状态文案映射 ... })

// 2026-08-15 修改：设备下拉框数据源由 latest 接口改为设备台账接口
// GET /api/asset/device/page?typeId=8，typeId=8 为升降机设备类型；pageSize=200 覆盖台账全部升降机
// 2026-08-18 修改：下拉框 value 仍为台账主键 id（数字，供 SensorRealtime 按台账 id 过滤传感器），
// 同时保留 deviceCode（设备编码）——latest/history 接口的 deviceId 参数要求设备编码而非台账 id，
// 查询时需经 selectedDeviceCode 换算（见下方 computed），与塔吊页修复口径一致
async function loadDevices() {
  deviceLoading.value = true
  try {
    const res = await assetApi.getDevicePage({ typeId: 8, pageSize: 200 })
    const list = Array.isArray(res.data?.records) ? res.data.records : []
    deviceList.value = list.map(d => ({
      deviceId: d.id,
      deviceCode: d.deviceCode,
      deviceName: d.deviceName || d.deviceCode
    }))
    if (!list.length) ElMessage.warning('暂无升降机设备数据')
  } catch (_) {
    ElMessage.error('设备列表加载失败，请检查后端服务')
  } finally {
    deviceLoading.value = false
    if (!selectedDevice.value && deviceList.value.length) {
      selectedDevice.value = deviceList.value[0].deviceId
    }
  }
}

// 2026-08-18 新增：台账 id → 设备编码（deviceCode）映射与当前选中编码。
// 原因：latest/history 接口的 deviceId 参数需传设备编码，而 selectedDevice 是台账主键 id，
// 二者不可混用（SensorRealtime 的 parent-id 仍需台账 id）；此处换算后再传给升降机接口。
const deviceCodeMap = computed(() => {
  const map = {}
  deviceList.value.forEach(d => { if (d.deviceCode) map[d.deviceId] = d.deviceCode })
  return map
})
const selectedDeviceCode = computed(() => deviceCodeMap.value[selectedDevice.value] || '')

// 用一条实时数据（REST latest 或 WS REALTIME_DATA）刷新联锁告警状态
function applyRealtime(d) {
  if (!d || !Object.keys(d).length) return
  deviceAlarm.value = Number(d.interlockAlarm) > 0
  alarmText.value = d.interlockAlarmText || (deviceAlarm.value ? '已触发' : '正常')
}

// 用 latest 接口拉取最新实时数据刷新联锁告警状态
// 2026-08-15 调整：WS 推送接口未就绪期间，由 startRealtimePolling 定时（30s）轮询调用；
// 新 WS 接口就绪后改回"仅首次初始化 + WS REALTIME_DATA 驱动"，并停用轮询
async function fetchData() {
  if (!isAlive || !selectedDevice.value) return
  try {
    // 2026-08-18 修改：latest 接口 deviceId 参数需传设备编码（deviceCode），
    // 原传台账主键 id 导致查不到实时数据（与塔吊页同款 bug）
    const res = await hoistApi.getLatest({ deviceId: selectedDeviceCode.value })
    if (!isAlive) return
    const row = res.data?.list?.[0]
    applyRealtime(row)
  } catch (_) { /* 忽略：轮询失败时等待下一轮重试 */ }
}

// 切换设备：重新拉取实时数据、历史表格与曲线（2026-08-17 调整：startRealtimePolling 内部含 fetchData 并重置轮询计时）
// 2026-08-18 修改：设备切换同时刷新曲线（曲线与表格分页解耦后需单独拉取）
function onDeviceChange(deviceId) {
  startRealtimePolling()
  fetchHistory()
  fetchCurve()
}

// ==================== 实时刷新：latest 定时轮询（过渡方案） ====================
// 2026-08-15 调整：与塔吊同步——后端 WS 推送接口暂未就绪（已删除、新接口开发中），
// 告警状态暂时回退为 GET /api/hoist/realtime/latest 定时轮询驱动，曲线保持纯历史数据不变。

// 轮询间隔（毫秒）：latest 接口为低频查询接口，30s 轮询较为合理，可按需调整
// 2026-08-17 调整：轮询间隔由 30s 缩短至 5s，与环境监测页实时看板口径统一
//（后端 latest 接口为低频查询，5s 轮询可满足实时看板展示）
const POLL_INTERVAL = 5000
let realtimePollingTimer = null

// 启动实时刷新：立即拉取一次，然后按 POLL_INTERVAL 定时轮询
function startRealtimePolling() {
  stopRealtimePolling()
  if (!selectedDevice.value) return
  fetchData()
  realtimePollingTimer = setInterval(() => { fetchData() }, POLL_INTERVAL)
}

// 停止实时刷新（页面销毁时调用，防止定时器泄漏）
function stopRealtimePolling() {
  if (realtimePollingTimer) {
    clearInterval(realtimePollingTimer)
    realtimePollingTimer = null
  }
}

// ==================== WebSocket 接入点（待后端新接口就绪后启用） ====================
// 连接升降机实时 WebSocket 并订阅指定设备；REALTIME_DATA 仅驱动联锁告警状态，
// 不再追加到运行曲线（曲线保持纯历史数据）。与全局告警 wsManager 分开维护，互不影响。
// 启用前需恢复：import hoistWs from '@/utils/hoistWs' 与 import { getToken } from '@/utils/auth'，
// 并把 onMounted 中的 startRealtimePolling() 改回 connectWs(selectedDevice.value)。
// function connectWs(deviceId) {
//   const token = getToken()
//   if (!token || !deviceId) return
//   hoistWs.onStatus = (s) => { if (isAlive) wsStatus.value = s }
//   hoistWs.onData = (d) => {
//     if (!isAlive) return
//     applyRealtime(d)
//   }
//   hoistWs.onError = (e) => {
//     if (e?.type === 'auth_failed') console.error('[升降机WS] 鉴权失败:', e.message)
//   }
//   hoistWs.connect(token)
//   hoistWs.subscribe(deviceId)
// }

// 格式化时间（历史查询区间用）
function fmt(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ==================== 历史数据 与 运行曲线 ====================
// 2026-08-15 拆分：原"运行曲线（历史数据）"拆为两个独立模块——历史数据（表格）与运行曲线（图表），
// 共用同一时间范围（默认当天；开始时间不限，结束时间不超过当前时间）。
// 数据全部来自 history 接口，WS 推送只负责仪表盘实时刷新，不再写入曲线。

// 历史数据表格分页状态（同一时间范围内分页展示）
const timeRange = ref([])
const loading = ref(false)
const historyData = ref([])
const pageNum = ref(1)
const pageSize = ref(100)
const total = ref(0)

// 查询日期范围文本（图表下方展示，便于确认对应时间段）
const dateRangeText = computed(() => {
  const [start, end] = timeRange.value || []
  if (!start || !end) return ''
  const s = String(start).slice(0, 10)
  const e = String(end).slice(0, 10)
  return s === e ? s : `${s} 至 ${e}`
})

// 最晚时间 = 当前时间（历史数据不可能存在于未来）
function getHistoryMaxTime() {
  return Date.now()
}

// 日期选择器只禁用未来日期；开始时间不设下限，可自由选择任意过去时间
function disabledDate(date) {
  return date.getTime() > getHistoryMaxTime()
}

// 获取当天时间范围（当天 00:00:00 ~ 当前时刻）
function getTodayRange() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return { startTime: fmt(start), endTime: fmt(now) }
}

// 初始化默认时间范围为当天
function initTimeRange() {
  const { startTime, endTime } = getTodayRange()
  timeRange.value = [startTime, endTime]
}

// 时间范围变更校验：结束时间不晚于当前时间、开始时间不晚于结束时间；合法则回到第一页重新查询
function onTimeRangeChange() {
  const [start, end] = timeRange.value || []
  if (!start || !end) { initTimeRange(); return }
  const startTime = new Date(start).getTime()
  const endTime = new Date(end).getTime()
  if (endTime > Date.now()) {
    ElMessage.warning('结束时间不能晚于当前时间')
    initTimeRange()
    return
  }
  if (startTime > endTime) {
    ElMessage.warning('开始时间不能晚于结束时间')
    initTimeRange()
    return
  }
  pageNum.value = 1
  fetchHistory()
  // 2026-08-18 修改：时间范围变化时同步刷新曲线（曲线独立覆盖整个时间范围）
  fetchCurve()
}

// 2026-08-15 调整：曲线支持按指标切换展示。缓冲中直接保存完整原始记录 {time, raw}，
// 切换指标时无需重新请求接口，仅重绘即可。
const curveBuffer = ref([])
// 可选指标定义（与 history 接口字段一一对应，颜色用于曲线区分）
const metricOptions = [
  { key: 'loadWeight', label: '载重(T)', color: '#1890ff' },
  { key: 'loadPercent', label: '载重百分比(%)', color: '#722ed1' },
  { key: 'height', label: '高度(m)', color: '#52c41a' },
  { key: 'passengerCount', label: '载人数(人)', color: '#fa8c16' },
  { key: 'windSpeed', label: '风速(m/s)', color: '#13c2c2' }
]
// 当前曲线展示的指标（默认载重）
const chartMetric = ref('loadWeight')

// 从 "2026-08-05 14:30:25.123" 提取 "14:30:25"
function fmtShort(t) {
  const m = String(t).match(/(\d{2}):(\d{2}):(\d{2})/)
  return m ? `${m[1]}:${m[2]}:${m[3]}` : String(t).slice(0, 19)
}

// 拉取所选时间范围的历史数据表格（分页展示）
// 2026-08-15 拆分：时间范围由 onTimeRangeChange 统一维护，页码/每页条数变化也在此重查
// 2026-08-18 修改：曲线不再由本函数驱动——原来曲线直接取表格当前页数据（pageSize=100），
// 时间范围内数据超过一页时曲线不完整，且切换分页不会改变曲线，属设计缺陷。
// 现拆出 fetchCurve() 独立拉取整个时间范围的数据绘制曲线，本函数只管表格分页。
async function fetchHistory() {
  if (!isAlive || !selectedDevice.value) return
  const [startTime, endTime] = timeRange.value || []
  if (!startTime || !endTime) return
  loading.value = true
  try {
    const res = await hoistApi.getHistory({
      // 2026-08-18 修改：history 接口 deviceId 参数需传设备编码（deviceCode），与 latest 口径一致
      deviceId: selectedDeviceCode.value,
      startTime,
      endTime,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (!isAlive) return
    const list = Array.isArray(res.data?.list) ? res.data.list : []
    historyData.value = list
    total.value = res.data?.total ?? list.length
  } catch (_) {
    // 接口异常：清空表格并显示"暂无数据"，绝不展示 mock 假数据
    if (isAlive) historyData.value = []
  } finally {
    if (isAlive) loading.value = false
  }
}

// 2026-08-18 新增：曲线数据独立拉取所选时间范围内的全部历史数据（循环分页直到取完），
// 与表格分页解耦。这样曲线完整覆盖用户选择的时间范围，不受 pageSize 限制。
async function fetchCurve() {
  if (!isAlive || !selectedDevice.value) return
  const [startTime, endTime] = timeRange.value || []
  if (!startTime || !endTime) return
  try {
    const list = []
    let page = 1
    let total = Infinity
    while (list.length < total) {
      const res = await hoistApi.getHistory({
        deviceId: selectedDeviceCode.value,
        startTime,
        endTime,
        pageNum: page,
        pageSize: 100
      })
      if (!isAlive) return
      const records = Array.isArray(res.data?.list) ? res.data.list : []
      total = res.data?.total ?? records.length
      list.push(...records)
      if (records.length === 0) break // 防止 total 返回不准确时死循环
      page++
    }
    // 历史数据按采集时间升序填充曲线缓冲
    const sorted = [...list].sort((a, b) => (a.collectTime || '').localeCompare(b.collectTime || ''))
    curveBuffer.value = sorted.map(r => ({ time: fmtShort(r.collectTime), raw: r }))
    updateChart()
  } catch (_) {
    // 曲线接口异常：清空曲线并显示"暂无数据"
    if (isAlive) { curveBuffer.value = []; updateChart() }
  }
}

// 渲染曲线：按当前选中的指标展示单条曲线（数据全部来自历史数据接口，无数据时显示"暂无数据"）
// 2026-08-18 修改：只绘制当前指标"有数据"的点——某条采集记录里该指标字段为空
// （null/undefined/空串）则跳过，x 轴与 y 轴都不生成该时间点，避免曲线出现无意义的空点。
function updateChart() {
  if (!chartInstance) return
  const points = curveBuffer.value
  const metric = metricOptions.find(m => m.key === chartMetric.value) || metricOptions[0]
  // 过滤出当前指标有数据的记录（该字段非 null/undefined/空串）
  const valid = points.filter(p => p.raw && p.raw[chartMetric.value] != null && p.raw[chartMetric.value] !== '')
  // 2026-08-18 修改：setOption 传 { notMerge: true }，每次全新替换图表配置，
  // 彻底避免与上一次指标曲线的合并残留（配合 onMetricChange 中的 clear 使用）
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: [metric.label] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: valid.length ? valid.map(p => p.time) : ['暂无数据'] },
    yAxis: [{ type: 'value', name: metric.label, splitLine: { lineStyle: { color: '#f0f0f0' } } }],
    series: [{
      name: metric.label,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: valid.length ? valid.map(p => p.raw[chartMetric.value]) : [],
      lineStyle: { color: metric.color },
      itemStyle: { color: metric.color },
      areaStyle: { color: hexToRgba(metric.color, 0.12) }
    }]
  }, { notMerge: true })
}

// 切换曲线指标：数据已缓存，仅重绘即可
// 2026-08-18 修改：切换前先 clear() 清空图表。原直接 updateChart() 时 setOption 为 merge 模式，
// ECharts 会对新旧曲线做过渡动画，出现"先画出其他指标曲线再显示正确曲线"的闪烁；
// clear 后重新 setOption 是全新渲染，切换瞬间不显示任何曲线，只呈现正确的新曲线。
function onMetricChange() {
  chartInstance?.clear()
  updateChart()
}

// 十六进制颜色转 rgba，用于曲线面积填充
function hexToRgba(hex, alpha) {
  const n = parseInt(hex.slice(1), 16)
  const r = (n >> 16) & 255
  const g = (n >> 8) & 255
  const b = n & 255
  return `rgba(${r},${g},${b},${alpha})`
}

onMounted(async () => {
  await loadDevices()
  await nextTick()
  // 修复 "Initialize failed: invalid dom"：
  // mounted 中 await 多个异步接口期间，若用户切换路由，layout 会按 route.path key 同步卸载本组件，
  // 此时 chartRef 已被 Vue 置为 null，直接 echarts.init(null) 会抛 invalid dom。
  // 因此 init 前必须校验组件仍存活（isAlive）且 DOM 存在。
  if (!isAlive || !chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  initTimeRange()      // 2026-08-15 拆分：初始化默认时间范围（当天）
  await fetchData()    // latest 接口初始化联锁告警状态
  await fetchHistory() // history 接口初始化历史数据表格（分页）
  await fetchCurve()   // 2026-08-18 修改：曲线独立拉取整个时间范围数据
  // 2026-08-15 调整：WS 接口未就绪，改由 latest 定时轮询驱动告警状态（曲线保持纯历史数据）
  startRealtimePolling()
  // 新 WS 接口就绪后改回：if (selectedDevice.value) connectWs(selectedDevice.value)
})

onUnmounted(() => {
  chartInstance?.dispose()
  // 2026-08-15 调整：停用 latest 定时轮询，避免页面销毁后定时器泄漏
  stopRealtimePolling()
  // 新 WS 接口就绪后恢复：hoistWs.disconnect()
})
</script>

<style scoped>
.page-container { padding: 0; }
</style>
