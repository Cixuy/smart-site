<template>
  <div class="page-container space-y-5">
    <!-- 顶部标题栏：返回按钮 + 标题 -->
    <div class="flex items-center gap-3">
      <el-button :icon="ArrowLeft" circle text @click="router.push('/monitor/hoist')" />
      <div>
        <!-- 2026-08-18 修改：标题与副标题随接口切换调整——
             统一告警接口 /api/alarms/record 按级别（预警/警报/控制）划分，不再限定联锁告警类型 -->
        <h2 class="text-xl font-bold text-gray-800">升降机告警列表</h2>
        <p class="text-sm text-gray-400 mt-1">查询升降机设备预警与报警记录</p>
      </div>
    </div>

    <!-- 筛选区：告警级别 + 设备 + 时间范围（接口参数均可选） -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query">
        <el-form-item label="告警级别">
          <!-- 2026-08-18 修改：联锁告警类型（interlockAlarm）筛选改为告警级别（alarmLevel）筛选，
               与统一告警接口 /api/alarms/record 的参数一致：1-预警 2-警报 3-控制；空=全部 -->
          <el-select v-model="query.alarmLevel" placeholder="全部级别" clearable style="width:140px" @change="handleSearch">
            <el-option label="预警" :value="1" />
            <el-option label="警报" :value="2" />
            <el-option label="控制" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <!-- 2026-08-18 修改：下拉框 value 已是台账主键 id（统一告警接口 deviceId 要求台账 id，与塔吊告警页口径一致），无需再改 -->
          <el-select v-model="query.deviceId" placeholder="全部设备" clearable filterable style="width:220px" :loading="deviceLoading" @change="handleSearch">
            <el-option v-for="d in deviceList" :key="d.deviceId" :label="d.deviceName" :value="d.deviceId" />
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
         旧接口的指标列（载重/载重百分比）与联锁告警类型已废弃 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <div class="mb-4"><span class="font-semibold text-gray-700">告警记录</span></div>
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="alarmNo" label="告警编号" width="150" />
        <el-table-column prop="deviceName" label="设备" width="160" />
        <el-table-column prop="alarmContent" label="告警内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="currentValue" label="当前值" width="90" />
        <el-table-column prop="thresholdRange" label="阈值范围" width="130" show-overflow-tooltip />
        <el-table-column prop="alarmTime" label="告警时间" width="175">
          <!-- alarmTime 为 ISO 格式（yyyy-MM-ddTHH:mm:ss.SSS），替换 T 后截取到秒展示 -->
          <template #default="{ row }">{{ row.alarmTime ? String(row.alarmTime).replace('T', ' ').slice(0, 19) : '-' }}</template>
        </el-table-column>
        <!-- alarmLevel 枚举与统一告警接口文档一致：1-预警, 2-警报, 3-控制
             2026-08-18 修复：后端 AlarmListVO 返回的级别文本字段名为 alarmLevelText（非 alarmLevelName），
             字段名不一致会导致级别列只能依赖 fallback 文本，此处按后端实际字段修正 -->
        <el-table-column prop="alarmLevelText" label="级别" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag :type="(row.alarmLevel || 1) >= 2 ? 'danger' : 'warning'" size="small">
              {{ row.alarmLevelText || ((row.alarmLevel || 1) >= 2 ? '警报' : '预警') }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- status 处置状态：0-未处置, 1-处置中, 2-已处置
             2026-08-18 修复：后端字段名为 statusText（非 statusName），不修正会导致处置状态列始终空白 -->
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
// 2026-08-15 修改：设备下拉框数据源由 latest 接口（依赖实时表有数据）改为设备台账接口 assetApi.getDevicePage，
// typeId=8 为升降机设备类型，台账已登记的设备即可出现在下拉框，不再受实时数据影响
// 2026-08-18 修改：告警查询由升降机联锁告警接口 /api/hoist/realtime/interlock-alarms 改为统一告警接口
// /api/alarms/record（alarmApi.getRecordList），故移除 hoistApi；新接口 deviceId 参数为台账主键 id，
// 设备下拉 value 上一版已是台账 id，无需再改
import { alarmApi, assetApi } from '@/api'

const router = useRouter()
let isAlive = true
onBeforeUnmount(() => { isAlive = false })

// ==================== 设备列表加载（与升降机主页面一致） ====================
// 2026-08-15 修改：设备下拉框数据源由 latest 接口改为设备台账接口
// GET /api/asset/device/page?typeId=8，typeId=8 为升降机设备类型；pageSize=200 覆盖台账全部升降机
// 2026-08-15 修改：下拉框 value 直接用台账记录 id（数字），选中后原样传给告警查询接口（与塔吊口径一致）
const deviceList = ref([])
const deviceLoading = ref(false)
async function loadDevices() {
  deviceLoading.value = true
  try {
    const res = await assetApi.getDevicePage({ typeId: 8, pageSize: 200 })
    if (!isAlive) return
    const list = Array.isArray(res.data?.records) ? res.data.records : []
    deviceList.value = list.map(d => ({
      deviceId: d.id,
      deviceName: d.deviceName || d.deviceCode
    }))
  } catch (_) { /* 接口异常时保留空列表 */ }
  finally { if (isAlive) deviceLoading.value = false }
}

// ==================== 筛选条件 ====================
// 2026-08-18 修改：删除联锁告警类型选项（interlockAlarm：1-双门同开 2-超载 3-超速 4-超高），
// 改用统一告警接口的告警级别 alarmLevel（1-预警 2-警报 3-控制），查询参数与接口字段一一对应
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
  alarmLevel: null,
  deviceId: null,
  timeRange: defaultRange()
})

// ==================== 告警列表查询 ====================
const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 2026-08-18 修改：查询统一告警接口 /api/alarms/record（与塔吊告警页一致）
// sourceType=1 限定设备监测类告警（升降机属于设备监测）；isDeleted=0 排除已删除记录；
// deviceId 传台账 id；后端返回 AlarmPageVO：{ total, pageNum, pageSize, pages, list }
async function fetchRecords() {
  if (!isAlive) return
  const [startTime, endTime] = query.timeRange || []
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value, sourceType: 1, isDeleted: 0 }
    if (query.deviceId) params.deviceId = query.deviceId
    if (query.alarmLevel !== null && query.alarmLevel !== undefined && query.alarmLevel !== '') {
      params.alarmLevel = query.alarmLevel
    }
    if (startTime) params.startTime = startTime
    if (endTime) params.endTime = endTime
    const res = await alarmApi.getRecordList(params)
    if (!isAlive) return
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (_) { if (isAlive) { tableData.value = []; total.value = 0 } }
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
  query.deviceId = null
  query.timeRange = defaultRange()
  handleSearch()
}

onMounted(async () => {
  await loadDevices()
  await fetchRecords()
})
</script>

<style scoped>.page-container { padding: 0; }</style>
