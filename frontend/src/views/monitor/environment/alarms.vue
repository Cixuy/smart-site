<template>
  <div class="page-container space-y-5">
    <!-- 顶部标题栏：返回按钮 + 标题 -->
    <div class="flex items-center gap-3">
      <el-button :icon="ArrowLeft" circle text @click="router.push('/monitor/environment')" />
      <div>
        <!-- 2026-08-18 修改：副标题随接口切换调整——统一告警接口按级别（预警/警报/控制）划分 -->
        <h2 class="text-xl font-bold text-gray-800">环境告警列表</h2>
        <p class="text-sm text-gray-400 mt-1">查询环境监测预警与报警记录</p>
      </div>
    </div>

    <!-- 筛选区：告警级别 + 监测点 + 指标类型 + 时间范围（接口参数均可选） -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query">
        <el-form-item label="告警级别">
          <!-- 2026-08-18 修改：旧接口的"状态"筛选（status 1-预警 2-报警）改为统一告警接口的
               "告警级别"筛选（alarmLevel 1-预警 2-警报 3-控制），与塔吊/升降机告警页口径一致 -->
          <el-select v-model="query.alarmLevel" placeholder="全部级别" clearable style="width:140px" @change="handleSearch">
            <el-option label="预警" :value="1" />
            <el-option label="警报" :value="2" />
            <el-option label="控制" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="监测点">
          <!-- 2026-08-18 修改：value 仍是环境监测点 id（biz_env_monitor.id）。
               统一告警接口 monitorPointId 参数按 a.monitor_point_id 精确过滤，
               环境告警写入时 monitor_point_id 存的就是监测点 id（传感器 parent_id），两者同体系，可直接复用 -->
          <el-select v-model="query.monitorPointId" placeholder="全部监测点" clearable filterable style="width:200px" :loading="pointsLoading" @change="handleSearch">
            <el-option v-for="p in points" :key="p.id" :label="p.pointName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标类型">
          <!-- 2026-08-18 修改：保留指标类型筛选，请求时由 metricType 拼出统一告警接口的
               alarmTypeCode（如 PM25 → PM25_EXCEED，后端创建环境告警时 alarm_type_code = metricType + "_EXCEED"） -->
          <el-select v-model="query.metricType" placeholder="全部指标" clearable style="width:120px" @change="handleSearch">
            <el-option v-for="m in metricOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="query.timeRange" type="datetimerange" range-separator="至"
            start-placeholder="开始时间" end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss" style="width:380px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 告警记录表格：GET /api/alarms/record -->
    <!-- 2026-08-18 修改：表格列适配统一告警接口 /api/alarms/record 的返回字段
         （alarmNo/alarmContent/alarmLevel/alarmTime/status 等），
         旧接口的采集时间/指标类型等列随接口废弃；监测点列展示后端返回的 monitorPointName -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <div class="mb-4"><span class="font-semibold text-gray-700">告警记录</span></div>
      <el-table :data="records" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="alarmNo" label="告警编号" width="150" />
        <!-- 环境告警（sourceType=2）展示监测点名称：后端 JOIN biz_env_monitor 取 name，空时降级显示 -- -->
        <el-table-column label="监测点" min-width="160">
          <template #default="{ row }">{{ row.monitorPointName || row.deviceName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="alarmContent" label="告警内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="currentValue" label="当前值" width="90" />
        <el-table-column prop="thresholdRange" label="阈值范围" width="140" show-overflow-tooltip />
        <el-table-column prop="alarmTime" label="告警时间" width="175">
          <!-- alarmTime 为 ISO 格式（yyyy-MM-ddTHH:mm:ss.SSS），替换 T 后截取到秒展示 -->
          <template #default="{ row }">{{ row.alarmTime ? String(row.alarmTime).replace('T', ' ').slice(0, 19) : '--' }}</template>
        </el-table-column>
        <!-- alarmLevel 枚举与统一告警接口文档一致：1-预警, 2-警报, 3-控制 -->
        <el-table-column prop="alarmLevelText" label="级别" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag :type="(row.alarmLevel || 1) >= 2 ? 'danger' : 'warning'" size="small">
              {{ row.alarmLevelText || ((row.alarmLevel || 1) >= 2 ? '警报' : '预警') }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- status 处置状态：0-未处置, 1-处置中, 2-已处置 -->
        <el-table-column prop="statusText" label="处置状态" width="90" align="center" fixed="right" />
      </el-table>
      <el-pagination class="mt-4 justify-end" v-model:current-page="pageNum" v-model:page-size="pageSize"
        :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchRecords" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Search, Refresh } from '@element-plus/icons-vue'
// 2026-08-18 修改：告警查询由环境历史告警接口 /api/env-monitor/alarms（environmentApi.getAlarms）
// 改为统一告警接口 /api/alarms/record（alarmApi.getRecordList）；environmentApi 仅保留用于加载监测点
import { alarmApi, environmentApi } from '@/api'

const router = useRouter()
let isAlive = true
onBeforeUnmount(() => { isAlive = false })

// ==================== 监测点加载（与主页面保持一致） ====================
// 2026-08-15 新增：从 /api/env-monitor/points/page 动态加载，实际返回 data.list
// 2026-08-18 修改：接口返回元素为 { id, name }（EnvMonitorPointVO），
// 其中 id 就是环境监测点 id（biz_env_monitor.id），下拉框 value 直接用该 id，
// 查询时原样传给统一告警接口 /api/alarms/record 的 monitorPointId 参数即可
const points = ref([])
const pointsLoading = ref(false)
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
      // 2026-08-18 修改：直接用接口返回的 id（监测点 id）作为 value，不再兼容 monitorPointId 字段
      const id = p.id
      if (id == null || map.has(id)) return
      map.set(id, { id, pointName: p.name || `监测点${id}` })
    })
    points.value = [...map.values()]
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

// 2026-08-15 新增：默认时间范围为近 7 天（datetime 格式，接口参数可选）
function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  start.setHours(0, 0, 0, 0)
  const pad = n => String(n).padStart(2, '0')
  const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  return [fmt(start), fmt(end)]
}

const query = reactive({
  // 2026-08-18 修改：status（1-预警 2-报警）改为 alarmLevel（1-预警 2-警报 3-控制）
  alarmLevel: null,
  monitorPointId: null,
  metricType: null,
  timeRange: defaultRange()
})

// ==================== 告警列表查询 ====================
const records = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 2026-08-18 修改：查询统一告警接口 /api/alarms/record（与塔吊/升降机告警页一致）
// sourceType=2 限定环境监测类告警；isDeleted=0 排除已删除记录；
// monitorPointId 传环境监测点 id（biz_env_monitor.id）；指标类型由 metricType 拼成 alarmTypeCode；
// 后端返回 AlarmPageVO：{ total, pageNum, pageSize, pages, list }
async function fetchRecords() {
  if (!isAlive) return
  const [startTime, endTime] = query.timeRange || []
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value, sourceType: 2, isDeleted: 0 }
    if (query.alarmLevel !== null && query.alarmLevel !== undefined && query.alarmLevel !== '') {
      params.alarmLevel = query.alarmLevel
    }
    // 2026-08-18 修改：monitorPointId 为 int64，直接传原始值避免 Number() 强转丢精度
    if (query.monitorPointId != null) params.monitorPointId = query.monitorPointId
    // 2026-08-18 新增：指标类型映射为统一告警接口的 alarmTypeCode（PM25 → PM25_EXCEED 等）
    if (query.metricType) params.alarmTypeCode = `${query.metricType}_EXCEED`
    if (startTime) params.startTime = startTime
    if (endTime) params.endTime = endTime
    const res = await alarmApi.getRecordList(params)
    if (!isAlive) return
    records.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (_) { if (isAlive) { records.value = []; total.value = 0 } }
  finally { if (isAlive) loading.value = false }
}

// 查询（切换任意筛选条件均触发）：回到第一页重新加载
function handleSearch() {
  pageNum.value = 1
  fetchRecords()
}

// 重置：清空全部筛选条件并恢复默认时间范围
function handleReset() {
  query.alarmLevel = null
  query.monitorPointId = null
  query.metricType = null
  query.timeRange = defaultRange()
  handleSearch()
}

onMounted(async () => {
  await loadPoints()
  await fetchRecords()
})
</script>

<style scoped>.page-container { padding: 0; }</style>
