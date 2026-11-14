<template>
  <div class="page-container space-y-4">
    <!-- 页头 -->
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 class="text-xl font-bold text-gray-800">AI智能识别</h2>
        <p class="text-sm text-gray-400 mt-1">安全帽 / 反光衣 / 吸烟 / 明火 识别告警</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button :icon="Refresh" @click="refreshCurrent">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <!-- ==================== 识别告警 ==================== -->
      <el-tab-pane label="识别告警" name="alarms">
        <el-form :inline="true" :model="alarmQuery" size="default" class="mb-4" @submit.prevent>
          <el-form-item label="识别类型">
            <el-select v-model="alarmQuery.alarmTypeCode" clearable filterable placeholder="全部" style="width:180px" @change="handleAlarmSearch">
              <el-option v-for="t in alarmTypeOptions" :key="t.code" :label="`${t.code} ${t.desc}`" :value="t.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="处置状态">
            <el-select v-model="alarmQuery.status" clearable placeholder="全部" style="width:120px" @change="handleAlarmSearch">
              <el-option v-for="(meta, key) in ALARM_STATUS" :key="key" :label="meta.label" :value="Number(key)" />
            </el-select>
          </el-form-item>
          <el-form-item label="告警时间">
            <el-date-picker v-model="alarmQuery.timeRange" type="datetimerange" range-separator="至"
              start-placeholder="开始时间" end-placeholder="结束时间" format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DDTHH:mm:ss" style="width:360px" @change="handleAlarmSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleAlarmSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetAlarmQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 告警卡片：GET /api/ai/alarms（records/total） -->
        <div v-loading="alarmLoading" class="min-h-40">
          <el-empty v-if="!alarmLoading && alarmList.length === 0" description="暂无 AI 识别告警" />
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div v-for="item in alarmList" :key="item.id"
              class="bg-white border border-gray-100 rounded-xl overflow-hidden cursor-pointer hover:shadow-md transition-shadow" @click="showDetail(item)">
              <div class="flex items-center justify-between px-3 py-2.5 border-b border-gray-50">
                <el-tag :type="alarmTypeTag(item.alarmTypeCode)" size="small" effect="dark">{{ getAlarmTypeDesc(item.alarmTypeCode) }}</el-tag>
                <el-tag :type="dictMeta(ALARM_LEVEL, item.alarmLevel).tag" effect="light" size="small">{{ dictMeta(ALARM_LEVEL, item.alarmLevel).label }}</el-tag>
              </div>
              <div class="p-3">
                <div class="flex items-center gap-2">
                  <span class="text-xs text-gray-400">{{ item.alarmNo }}</span>
                  <el-tag :type="dictMeta(ALARM_STATUS, item.status).tag" effect="plain" size="small" class="ml-auto">{{ dictMeta(ALARM_STATUS, item.status).label }}</el-tag>
                </div>
                <p class="mt-2 text-sm text-gray-700 line-clamp-2" :title="item.alarmContent">{{ item.alarmContent || '-' }}</p>
                <p class="mt-2 text-xs text-gray-400">{{ formatTime(item.alarmTime) }}</p>
              </div>
            </div>
          </div>
        </div>
        <el-pagination class="mt-4 justify-end" v-model:current-page="alarmPage" v-model:page-size="alarmSize"
          :total="alarmTotal" :page-sizes="[9,18,30]" layout="total,sizes,prev,pager,next" @change="fetchAlarms" />
      </el-tab-pane>

      <!-- ==================== 推理日志（暂未实现，前端隐藏；保留实现代码） ==================== -->
      <el-tab-pane label="推理日志" name="logs" v-if="false">
        <el-alert v-if="logsUnavailable" type="info" :closable="false" show-icon class="mb-3"
          title="推理日志接口暂未开通（/api/ai/inference-logs 当前不可用），日志数据暂时无法加载" />
        <el-form :inline="true" :model="logQuery" size="default" class="mb-4" @submit.prevent>
          <el-form-item label="摄像头">
            <el-select v-model="logQuery.cameraId" clearable filterable placeholder="全部摄像头" style="width:220px"
              :loading="cameraLoading" @change="handleLogSearch">
              <el-option v-for="c in cameraOptions" :key="c.id" :label="c.cameraName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleLogSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetLogQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 推理日志表：GET /api/ai/inference-logs -->
        <el-table :data="logList" v-loading="logLoading" stripe border style="width:100%">
          <el-table-column prop="inferenceTime" label="推理时间" width="180"><template #default="{ row }">{{ formatTime(row.inferenceTime) }}</template></el-table-column>
          <el-table-column label="摄像头" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ cameraName(row.cameraId) }}</template>
          </el-table-column>
          <el-table-column label="识别类型" width="110" align="center">
            <template #default="{ row }"><el-tag :type="detectTypeTag(row.detectionType)" size="small">{{ detectTypeLabel(row.detectionType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="置信度" width="110" align="right">
            <template #default="{ row }">
              <span class="font-medium" :class="Number(row.confidence) >= 0.8 ? 'text-red-500' : 'text-gray-700'">{{ (Number(row.confidence) * 100).toFixed(1) }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.hasAlarm === 1 ? 'danger' : 'success'" size="small">{{ row.hasAlarm === 1 ? '已告警' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="目标坐标 bbox" min-width="180" show-overflow-tooltip>
            <template #default="{ row }"><span class="font-mono text-xs">{{ fmtBbox(row.bboxCoords) }}</span></template>
          </el-table-column>
          <el-table-column label="耗时(ms)" width="100" align="right"><template #default="{ row }">{{ row.inferenceDurationMs ?? '-' }}</template></el-table-column>
          <el-table-column prop="id" label="日志ID" width="90" align="right" />
        </el-table>
        <el-pagination class="mt-4 justify-end" v-model:current-page="logPage" v-model:page-size="logSize"
          :total="logTotal" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchLogs" />
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 告警详情弹窗：GET /api/ai/alarms/{id} ==================== -->
    <el-dialog v-model="detailVisible" title="AI 告警详情" width="720px" :close-on-click-modal="false" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="告警编号"><span class="font-medium text-gray-800">{{ detail.alarmNo }}</span></el-descriptions-item>
            <el-descriptions-item label="识别类型"><el-tag :type="alarmTypeTag(detail.alarmTypeCode)" size="small">{{ getAlarmTypeDesc(detail.alarmTypeCode) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="告警级别"><el-tag :type="dictMeta(ALARM_LEVEL, detail.alarmLevel).tag" effect="dark" size="small">{{ dictMeta(ALARM_LEVEL, detail.alarmLevel).label }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="处置状态"><el-tag :type="dictMeta(ALARM_STATUS, detail.status).tag" effect="light" size="small">{{ dictMeta(ALARM_STATUS, detail.status).label }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="告警时间">{{ formatTime(detail.alarmTime) }}</el-descriptions-item>
            <el-descriptions-item label="摄像头">{{ cameraName(detail.cameraId) }}</el-descriptions-item>
            <el-descriptions-item label="告警内容" :span="2"><span class="text-gray-700">{{ detail.alarmContent }}</span></el-descriptions-item>
          </el-descriptions>

          <div class="mt-4">
            <div class="mb-2 text-sm font-semibold text-gray-700">AI 截图（{{ detailShots.length }}）</div>
            <el-empty v-if="detailShots.length === 0" description="暂无截图" :image-size="60" />
            <div v-else class="grid grid-cols-2 gap-3">
              <div v-for="shot in detailShots" :key="shot.url"
                class="rounded-lg border border-gray-100 overflow-hidden group">
                <div class="relative bg-gray-100 aspect-video flex items-center justify-center overflow-hidden">
                  <img v-if="shot.url" :src="shot.url" :alt="shot.type"
                    class="w-full h-full object-contain cursor-zoom-in transition group-hover:scale-105" @click="openPreview(shot.url)" />
                  <el-icon v-else :size="36" class="text-gray-300"><Picture /></el-icon>
                </div>
                <div class="px-2 py-1.5 flex items-center justify-between border-t border-gray-50">
                  <el-tag size="small" effect="plain">{{ shot.type }}</el-tag>
                  <el-button size="small" text type="primary" :disabled="!shot.url" @click="openPreview(shot.url)">查看大图</el-button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 截图大图预览 -->
    <el-dialog v-model="previewVisible" title="截图预览" width="880px" destroy-on-close>
      <div v-if="previewUrl" class="flex justify-center bg-gray-50 rounded-lg overflow-hidden">
        <img :src="previewUrl" class="max-w-full max-h-[70vh] object-contain" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { Search, Refresh, RefreshLeft, Picture } from '@element-plus/icons-vue'
import { aiApi, videoApi } from '@/api'
import { ALARM_LEVEL, ALARM_STATUS, dictMeta, getAlarmTypeDesc } from '@/constants/alarm'

let isAlive = true
onBeforeUnmount(() => { isAlive = false })

const activeTab = ref('alarms')

/* ==================== 常量映射 ==================== */
// 告警类型编码（与 constants/alarm.js ALARM_TYPE_GROUPS source=3 一致）
const alarmTypeOptions = [
  { code: 'NO_HELMET', desc: '安全帽未佩戴' },
  { code: 'NO_VEST', desc: '安全服未穿' },
  { code: 'SMOKING', desc: '现场吸烟' },
  { code: 'FIRE', desc: '现场明火' }
]
const alarmTypeTagMap = { NO_HELMET: 'warning', NO_VEST: 'primary', SMOKING: 'danger', FIRE: 'danger' }
function alarmTypeTag(code) { return alarmTypeTagMap[code] || 'info' }

// 推理日志 detection_type（与 biz_ai_inference_log 表一致）
const detectTypeOption = [
  { code: 'helmet', label: '安全帽', tag: 'warning' },
  { code: 'vest', label: '反光衣', tag: 'primary' },
  { code: 'smoking', label: '吸烟', tag: 'danger' },
  { code: 'fire', label: '明火', tag: 'danger' }
]
function detectTypeLabel(t) { return detectTypeOption.find(o => o.code === t)?.label || t || '-' }
function detectTypeTag(t) { return detectTypeOption.find(o => o.code === t)?.tag || 'info' }

// 截图类型 1-原始截图 2-标注截图
function screenshotTypeLabel(t) { return { 1: '原始截图', 2: '标注截图' }[Number(t)] || `类型${t}` }

/* ==================== 摄像头（用于日志筛选 + 名称映射） ==================== */
const cameraOptions = ref([])
const cameraLoading = ref(false)
const cameraMap = {}
function cameraName(id) {
  if (id == null) return '-'
  return cameraMap[id] ? cameraMap[id] : `摄像头${id}`
}
async function loadCameras() {
  cameraLoading.value = true
  try {
    let pageNum = 1
    const pageSize = 100
    while (true) {
      const res = await videoApi.getCameras({ pageNum, pageSize })
      if (!isAlive) return
      const list = res?.data?.records ?? res?.data?.list ?? (Array.isArray(res?.data) ? res.data : [])
      list.forEach(c => { if (c.id != null) cameraMap[c.id] = c.cameraName || `摄像头${c.id}` })
      cameraOptions.value = Object.keys(cameraMap).map(id => ({ id: Number(id), cameraName: cameraMap[id] }))
      if (res?.data?.total != null || list.length < pageSize) break
      pageNum++
    }
  } catch (_) { /* 接口不可用时保留空列表，不阻塞页面 */ }
  finally { if (isAlive) cameraLoading.value = false }
}

/* ==================== 识别告警 ==================== */
const alarmQuery = reactive({ alarmTypeCode: '', status: null, timeRange: null })
const alarmList = ref([])
const alarmLoading = ref(false)
const alarmPage = ref(1)
const alarmSize = ref(9)
const alarmTotal = ref(0)

function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  start.setHours(0, 0, 0, 0)
  const pad = n => String(n).padStart(2, '0')
  const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  return [fmt(start), fmt(end)]
}
alarmQuery.timeRange = defaultRange()

async function fetchAlarms() {
  if (!isAlive) return
  const [startTime, endTime] = alarmQuery.timeRange || []
  alarmLoading.value = true
  try {
    const res = await aiApi.getAlarms({
      alarmTypeCode: alarmQuery.alarmTypeCode || undefined,
      status: alarmQuery.status ?? undefined,
      startTime: startTime || undefined,
      endTime: endTime || undefined,
      pageNum: alarmPage.value,
      pageSize: alarmSize.value
    })
    if (!isAlive) return
    const list = Array.isArray(res.data?.records) ? res.data.records : (Array.isArray(res.data?.list) ? res.data.list : [])
    alarmList.value = list
    alarmTotal.value = res.data?.total ?? list.length
  } catch (_) {
    if (isAlive) { alarmList.value = []; alarmTotal.value = 0 }
  } finally {
    if (isAlive) alarmLoading.value = false
  }
}

function handleAlarmSearch() {
  alarmPage.value = 1
  fetchAlarms()
}

function resetAlarmQuery() {
  alarmQuery.alarmTypeCode = ''
  alarmQuery.status = null
  alarmQuery.timeRange = defaultRange()
  handleAlarmSearch()
}

/* ==================== 告警详情 ==================== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailShots = ref([])
const previewVisible = ref(false)
const previewUrl = ref('')

function openPreview(url) {
  previewUrl.value = url
  previewVisible.value = true
}

// 兼容两种截图返回结构：接口文档的 screenshots[].screenshotUrl，以及实际后端返回的 screenshotUrls[]
function screenshotsOf(d) {
  const list = []
  ;(d?.screenshots || []).forEach(s => {
    if (s?.screenshotUrl) list.push({ url: s.screenshotUrl, type: screenshotTypeLabel(s.screenshotType) })
  })
  ;(d?.screenshotUrls || []).forEach(u => {
    if (u) list.push({ url: u, type: screenshotTypeLabel(1) })
  })
  return list
}

async function showDetail(item) {
  detail.value = null
  detailShots.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await aiApi.getAlarmById(item.id)
    detail.value = res.data || null
    detailShots.value = screenshotsOf(res.data)
  } catch (_) {
    detail.value = null
    detailShots.value = []
  } finally {
    if (isAlive) detailLoading.value = false
  }
}

/* ==================== 推理日志 ==================== */
const logQuery = reactive({ cameraId: null })
const logList = ref([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)
// 后端暂未实现 /api/ai/inference-logs（404），静默失败并提示，避免每次进入页面弹"网络错误"
const logsUnavailable = ref(false)

function fmtBbox(v) {
  if (!v) return '-'
  try { return Array.isArray(v) ? JSON.stringify(v) : (typeof v === 'object' ? JSON.stringify(v) : v) }
  catch (_) { return String(v) }
}

async function fetchLogs() {
  if (!isAlive) return
  logLoading.value = true
  try {
    const res = await aiApi.getInferenceLogs({
      cameraId: logQuery.cameraId ?? undefined,
      pageNum: logPage.value,
      pageSize: logSize.value
    }, { silent: true })
    if (!isAlive) return
    logsUnavailable.value = false
    const list = Array.isArray(res.data?.records) ? res.data.records : (Array.isArray(res.data?.list) ? res.data.list : [])
    logList.value = list
    logTotal.value = res.data?.total ?? list.length
  } catch (_) {
    if (isAlive) { logsUnavailable.value = true; logList.value = []; logTotal.value = 0 }
  } finally {
    if (isAlive) logLoading.value = false
  }
}

function handleLogSearch() {
  logPage.value = 1
  fetchLogs()
}

function resetLogQuery() {
  logQuery.cameraId = null
  handleLogSearch()
}

/* ==================== 工具 ==================== */
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/* ==================== 初始化 ==================== */
function refreshCurrent() {
  if (activeTab.value === 'alarms') fetchAlarms()
  else fetchLogs()
}

onMounted(async () => {
  await loadCameras()
  await fetchAlarms()
  await fetchLogs()
})
</script>

<style scoped>.page-container { padding: 0; }</style>