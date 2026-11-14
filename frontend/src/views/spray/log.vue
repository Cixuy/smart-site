<template>
  <div class="page-container space-y-4">
    <div>
      <h2 class="text-xl font-bold text-gray-800">喷淋操作日志</h2>
      <p class="text-sm text-gray-400 mt-1">完整记录所有喷淋动作（自动联动/手动/定时），满足环保监管审计要求</p>
    </div>

    <!-- 筛选区 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query" size="default" @submit.prevent>
        <el-form-item label="位置">
          <el-select v-model="query.locationId" clearable placeholder="全部位置" style="width:180px">
            <el-option v-for="loc in locations" :key="loc.id" :label="loc.locationName" :value="loc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="query.triggerMode" clearable placeholder="全部" style="width:120px">
            <el-option label="自动联动" value="auto" />
            <el-option label="手动" value="manual" />
            <el-option label="定时" value="schedule" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="query.operationType" clearable placeholder="全部" style="width:110px">
            <el-option label="启动" value="start" />
            <el-option label="停止" value="stop" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operatorName" clearable placeholder="姓名模糊搜索" style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Download" plain :loading="exporting" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 运行统计卡片（2026-08-14 方案D：固定口径 + 明确标注）：
         口径说明：统计直接来自真实后端 GET /api/spray/statistics，只受"位置 + 时间范围"影响，
         "触发方式/操作类型/操作人"筛选仅作用于下方日志列表，不影响统计卡片（后端统计接口不支持这些维度）。
         字段说明：totalStartCount 总启动次数 / totalDurationMinutes 总运行时长(分钟) /
         avgDurationMinutes 平均运行时长(分钟) / autoTriggerRate 自动联动占比(%) /
         manualTriggerRate 手动操作占比(%) / scheduleTriggerRate 定时任务占比(%) -->
    <div class="bg-white rounded-lg p-3 border border-gray-100">
      <!-- 口径标注：明确告知用户统计与列表筛选维度的差异，避免"查询后统计不变"的误解 -->
      <p class="text-xs text-gray-400 mb-2">
        <el-icon class="align-middle mr-1"><InfoFilled /></el-icon>
        统计口径：仅按「位置 + 时间范围」统计（接口限制），不受上方「触发方式/操作类型/操作人」筛选影响，明细请查看下方列表
      </p>
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <div v-for="s in statCards" :key="s.key" v-loading="statLoading" class="bg-white rounded-lg p-3 text-center border border-gray-100">
          <p class="text-xs text-gray-400">{{ s.label }}</p>
          <p class="text-xl font-bold mt-1 text-gray-800">{{ s.value }}</p>
        </div>
      </div>
    </div>

    <!-- 日志表格 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="createTime" label="操作时间" width="170" />
        <el-table-column prop="locationName" label="位置" min-width="120">
          <template #default="{ row }">{{ row.locationName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="triggerMode" label="触发方式" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="modeTagType[row.triggerMode]" size="small">{{ triggerModeMap[row.triggerMode] || row.triggerMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationType" label="操作类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.operationType === 'start' ? 'success' : 'danger'" size="small">{{ row.operationType === 'start' ? '启动' : '停止' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sensorName" label="监测点" min-width="110">
          <template #default="{ row }">{{ row.sensorName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-4 justify-end"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total,sizes,prev,pager,next"
        @change="fetchData"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="喷淋操作详情" width="540px">
      <el-descriptions :column="2" border v-loading="detailLoading" v-if="detail">
        <el-descriptions-item label="操作ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ detail.locationName }}</el-descriptions-item>
        <el-descriptions-item label="位置ID">{{ detail.locationId }}</el-descriptions-item>
        <el-descriptions-item label="监测点">{{ detail.sensorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="监测点ID">{{ detail.sensorId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ triggerModeMap[detail.triggerMode] || detail.triggerMode }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ detail.operationType === 'start' ? '启动' : '停止' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作人ID">{{ detail.operatorId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="原因" :span="2">{{ detail.reason || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
// 统计卡片需要 computed 计算展示卡片数据（统计接口后端已就绪，恢复使用）
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Download, InfoFilled } from '@element-plus/icons-vue'
import { sprayApi, assetApi } from '@/api'

const triggerModeMap = { auto: '自动联动', manual: '手动', schedule: '定时' }
const modeTagType = { auto: 'primary', manual: 'warning', schedule: 'success' }

const locations = ref([])
const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
// 位置名兜底缓存（按 locationId）：后端日志列表 JOIN 只关联"未删除"位置，
// 位置被删除/停用时列表返回空名称；用日志 id 调详情接口 getLogDetail 反查位置名称展示，
// 按 locationId 缓存避免同一位置反复请求（详情接口不过滤删除标记，删除后仍可查到）。
const locationNameCache = new Map()

const timeRange = ref(null)
const query = reactive({ locationId: null, triggerMode: '', operationType: '', operatorName: '' })

// ==================== 运行统计 ====================
// 2026-08-14 需求调整：统计卡片固定口径（方案D），直接对接真实后端 GET /api/spray/statistics（后端接口已就绪，不依赖 mock）。
// 口径说明：仅受"位置 + 时间范围"影响；触发方式/操作类型/操作人筛选不影响统计卡片，模板中已加提示标注。
// 接口字段：totalStartCount 总启动次数 / totalDurationMinutes 总运行时长(分钟) /
// avgDurationMinutes 平均运行时长(分钟) / autoTriggerRate 自动联动占比(%) /
// manualTriggerRate 手动操作占比(%) / scheduleTriggerRate 定时任务占比(%) / details 分组明细
const statLoading = ref(false)
const statData = ref(null)
const statCards = computed(() => {
  const d = statData.value
  if (!d) return []
  // 占比字段后端返回百分比数值（如 65 表示 65%），统一保留 1 位小数展示；
  // 接口未返回的字段显示 '—'，明确"暂无该指标"
  const fmtRate = v => (v == null ? '—' : `${Number(v).toFixed(1)}%`)
  return [
    { key: 'totalStartCount', label: '总启动次数', value: d.totalStartCount ?? '—' },
    { key: 'totalDuration', label: '总运行时长(分)', value: d.totalDurationMinutes ?? '—' },
    { key: 'avgDuration', label: '平均运行时长(分)', value: d.avgDurationMinutes ?? '—' },
    { key: 'auto', label: '自动联动占比', value: fmtRate(d.autoTriggerRate) },
    { key: 'manual', label: '手动操作占比', value: fmtRate(d.manualTriggerRate) },
    { key: 'schedule', label: '定时任务占比', value: fmtRate(d.scheduleTriggerRate) }
  ]
})

// 拉取运行统计（2026-08-14 方案D：固定口径，直连真实后端 GET /api/spray/statistics）。
// 说明：接口只支持 locationId + beginTime/endTime 过滤（beginTime/endTime 必填，默认取当天），
// 位置未选则全局统计；groupBy 可不传，按需再扩展。
async function fetchStats() {
  statLoading.value = true
  try {
    const [b, e] = timeRange.value || defaultRange()
    const res = await sprayApi.getStatistics({
      locationId: query.locationId ?? undefined,
      beginTime: b,
      endTime: e
    })
    statData.value = res.data
  } catch (_) {
    // 统计接口失败时不阻塞日志列表展示，卡片显示空态
    statData.value = null
  } finally {
    statLoading.value = false
  }
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

/** 位置树平铺为下拉选项 */
function flattenTree(nodes, list = []) {
  nodes.forEach(n => {
    list.push({ id: n.id, locationName: n.locationName })
    if (n.children && n.children.length) flattenTree(n.children, list)
  })
  return list
}

async function loadLocations() {
  try {
    // 拉取完整位置树（不传 status，含停用/父级节点），用于筛选下拉
    const res = await assetApi.getLocationTree()
    locations.value = flattenTree(res.data?.tree || [])
  } catch (_) {
    locations.value = []
  }
}

/**
 * 列表"位置"列兜底：日志列表接口对已删除/停用位置返回空名称，
 * 直接用日志 id 调详情接口 getLogDetail 取位置名称展示（详情接口不过滤删除标记，删除后仍可查到）。
 */
async function fillLocationName(row) {
  if (row.locationName) return row
  if (row.locationId == null || row.id == null) return row
  if (locationNameCache.has(row.locationId)) {
    row.locationName = locationNameCache.get(row.locationId)
    return row
  }
  try {
    const res = await sprayApi.getLogDetail(row.id)
    const name = res.data?.locationName || null
    if (name) {
      locationNameCache.set(row.locationId, name)
      row.locationName = name
    }
  } catch (_) { /* 详情查不到则保持空，由模板显示 '—' */ }
  return row
}

/** 组装查询参数（undefined 不会传给后端） */
function buildParams(extra = {}) {
  const [beginTime, endTime] = timeRange.value || []
  return {
    locationId: query.locationId ?? undefined,
    triggerMode: query.triggerMode || undefined,
    operationType: query.operationType || undefined,
    operatorName: query.operatorName || undefined,
    beginTime: beginTime || undefined,
    endTime: endTime || undefined,
    ...extra
  }
}

/** 兜底默认时间范围：今天 00:00:00 至当前（统计/导出接口 beginTime/endTime 必填，仅在获取时间边界失败时使用） */
function defaultRange() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const pad = n => String(n).padStart(2, '0')
  const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  return [fmt(start), fmt(now)]
}

/**
 * 2026-08-14 新增：加载日志时间边界并填入默认时间范围。
 * 说明：统计/导出接口的 beginTime/endTime 为必填，进入页面时不应只查"当天"，而应覆盖全量日志时间。
 * 实现：依赖日志分页接口默认按操作时间倒序——
 *   1) pageNum=1&pageSize=1 取第一条 = 最新操作时间（endTime）
 *   2) 由 total 算出最后一页，pageSize=1 时最后一页那条 = 最早操作时间（beginTime）
 * 仅当两次均成功且最早时间不晚于最新时间时，才覆盖 timeRange；否则保留兜底 defaultRange()
 */
async function loadTimeBounds() {
  try {
    // 最新一条（第一页首条，后端默认倒序）
    const latestRes = await sprayApi.getOperationLogPage({ pageNum: 1, pageSize: 1 })
    const total = latestRes.data?.total || 0
    const latest = latestRes.data?.records?.[0]?.createTime
    if (!total || !latest) return
    // 最早一条（pageSize=1 时总页数 = total，取最后一页首条）
    const oldestRes = await sprayApi.getOperationLogPage({ pageNum: total, pageSize: 1 })
    const oldest = oldestRes.data?.records?.[0]?.createTime
    if (!oldest) return
    // 防御：若后端排序非倒序导致时间颠倒，则交换，保证时间范围合法
    timeRange.value = oldest <= latest ? [oldest, latest] : [latest, oldest]
  } catch (_) {
    // 获取时间边界失败时保持 timeRange 为 null，后续由 defaultRange() 兜底，不阻塞页面
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await sprayApi.getOperationLogPage(buildParams({ pageNum: pageNum.value, pageSize: pageSize.value }))
    // 位置名称兜底：后端 JOIN 未删除位置，删除/停用后名称为空；逐个用 locationId 反查补齐
    const rows = res.data?.records || []
    const filled = []
    for (const r of rows) filled.push(await fillLocationName(r))
    tableData.value = filled
    total.value = res.data?.total || 0
  } catch (_) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 2026-08-14 需求调整：查询/重置时同步刷新统计卡片（统计接口后端已就绪）
function handleSearch() {
  pageNum.value = 1
  fetchData()
  fetchStats()
}

async function handleReset() {
  query.locationId = null
  query.triggerMode = ''
  query.operationType = ''
  query.operatorName = ''
  // 重置后恢复"全量时间范围"（最早到最晚），与进入页面时的默认行为一致
  timeRange.value = null
  await loadTimeBounds()
  handleSearch()
}

/** 导出喷淋操作日志（CSV） */
async function handleExport() {
  const [b, e] = timeRange.value || defaultRange()
  exporting.value = true
  try {
    const res = await sprayApi.exportLog(buildParams({ beginTime: b, endTime: e }))
    const disposition = res.headers?.['content-disposition'] || ''
    let filename = 'spray_operation_log.csv'
    const m = disposition.match(/filename=([^;]+)/i)
    if (m) filename = m[1].trim().replace(/^"|"$/g, '')
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (_) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

/** 查看详情 */
async function openDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await sprayApi.getLogDetail(id)
    // 详情弹窗本身就是 getLogDetail 接口，位置名称已直接返回，无需再兜底
    detail.value = res.data
  } catch (_) {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  // 先加载完整位置树（构建名称兜底映射），再加载列表/统计，保证位置列能补齐
  await loadLocations()
  // 2026-08-14 新增：进入页面先获取日志最早/最晚时间填入默认时间范围（统计/导出接口 beginTime/endTime 必填），
  // 再加载列表与统计，保证查询覆盖全量日志
  await loadTimeBounds()
  fetchData()
  fetchStats()
})
</script>

<style scoped>
.page-container {
  padding: 0;
}
</style>
