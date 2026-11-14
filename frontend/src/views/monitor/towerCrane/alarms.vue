<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <!-- 2026-08-14 新增：返回上一级（塔吊实时监控主页面） -->
      <div class="flex items-center gap-3">
        <el-button :icon="ArrowLeft" circle text @click="router.push('/monitor/towerCrane')" />
        <div>
          <h2 class="text-xl font-bold text-gray-800">塔吊告警列表</h2>
          <p class="text-sm text-gray-400 mt-1">查询塔吊设备预警与报警记录</p>
        </div>
      </div>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="设备">
          <!-- 2026-08-18 修改：value 改为台账主键 id（新接口 /api/alarms/record 的 deviceId 参数为台账 id），
               旧接口 /api/tower-crane/realtime/alarms 要求设备编码，接口切换后一并调整 -->
          <el-select v-model="query.deviceId" placeholder="全部设备" clearable style="width:200px" @change="handleSearch">
            <el-option v-for="d in devices" :key="d.deviceId" :label="d.deviceName" :value="d.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别">
          <!-- alarmLevel 枚举与统一告警接口文档一致：1-预警, 2-警报, 3-控制；空=全部 -->
          <el-select v-model="query.alarmLevel" placeholder="全部级别" clearable style="width:140px" @change="handleSearch">
            <el-option label="预警" :value="1" />
            <el-option label="警报" :value="2" />
            <el-option label="控制" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="query.timeRange" type="datetimerange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <!-- 2026-08-18 修改：表格列适配统一告警接口 /api/alarms/record 的返回字段
           （alarmNo/alarmContent/alarmLevel/alarmTime/status 等），
           旧接口的指标列（力矩/风速等）与 alarmStatus 已废弃 -->
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="alarmNo" label="告警编号" width="150" />
        <el-table-column prop="deviceName" label="设备" width="130" />
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
      <el-pagination class="mt-4 justify-end" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchData" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, ArrowLeft } from '@element-plus/icons-vue'
// 2026-08-15 修改：设备下拉框数据源由 latest 接口（依赖实时表有数据）改为设备台账接口 assetApi.getDevicePage，
// typeId=2 为塔吊设备类型，台账已登记的设备即可出现在下拉框，不再受实时数据影响
// 2026-08-18 修改：告警查询改用统一告警接口 /api/alarms/record（alarmApi.getRecordList），
// 故移除 towerCraneApi；新接口 deviceId 参数为台账主键 id，因此设备下拉 value 同步改为台账 id
import { alarmApi, assetApi } from '@/api'

const router = useRouter()

const devices = ref([])
// 2026-08-18 修改：alarmStatus（0-正常/1-预警/2-报警）改为 alarmLevel（1-预警/2-警报/3-控制）
const query = reactive({ deviceId: '', alarmLevel: null, timeRange: null })
const loading = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 2026-08-15 修改：设备下拉框数据源由 latest 接口改为设备台账接口
// GET /api/asset/device/page?typeId=2，typeId=2 为塔吊设备类型
// 2026-08-18 修改：value 由设备编码（deviceCode）改为台账主键 id——统一告警接口 /api/alarms/record
// 的 deviceId 参数要求台账 id（int64）；保留 deviceCode 备用
async function loadDevices() {
  try {
    const res = await assetApi.getDevicePage({ typeId: 2, pageSize: 200 })
    const list = Array.isArray(res.data?.records) ? res.data.records : []
    devices.value = list.map(d => ({ deviceId: d.id, deviceCode: d.deviceCode, deviceName: d.deviceName || d.deviceCode }))
  } catch (_) { /* 接口异常时保持空列表 */ }
}

function handleSearch() {
  pageNum.value = 1
  fetchData()
}

function handleReset() {
  query.deviceId = ''
  // 2026-08-18 修复：原代码重置的是已废弃的 alarmStatus（旧接口字段），
  // 接口切换后筛选条件为 alarmLevel（1-预警 2-警报 3-控制），此处一并修正，避免重置后级别筛选残留
  query.alarmLevel = null
  query.timeRange = null
  pageNum.value = 1
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    // 2026-08-18 修改：查询统一告警接口 /api/alarms/record
    // sourceType=1 限定设备监测类告警（塔吊）；isDeleted=0 排除已删除记录
    const params = { pageNum: pageNum.value, pageSize: pageSize.value, sourceType: 1, isDeleted: 0 }
    if (query.deviceId) params.deviceId = query.deviceId
    if (query.alarmLevel !== null && query.alarmLevel !== undefined && query.alarmLevel !== '') {
      params.alarmLevel = query.alarmLevel
    }
    if (query.timeRange && query.timeRange.length === 2) {
      params.startTime = query.timeRange[0]
      params.endTime = query.timeRange[1]
    }
    // 后端返回 AlarmPageVO：{ total, pageNum, pageSize, pages, list }
    const res = await alarmApi.getRecordList(params)
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (_) { tableData.value = [] }
  finally { loading.value = false }
}

onMounted(async () => {
  await loadDevices()
  fetchData()
})
</script>

<style scoped>.page-container { padding: 0; }</style>
