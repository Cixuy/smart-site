<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">区域喷淋运行状态</h2>
        <p class="text-sm text-gray-400 mt-1">实时展示各区域喷淋运行情况，支持手动启停与自动联动控制</p>
      </div>
      <div class="flex gap-2">
        <el-button :icon="Plus" @click="openAddDialog">添加区域</el-button>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="fetchData">刷新</el-button>
      </div>
    </div>

    <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
      <div class="bg-white rounded-lg p-3 text-center border border-gray-100">
        <p class="text-xs text-gray-400">区域总数</p><p class="text-xl font-bold mt-1">{{ stats.total }}</p>
      </div>
      <div class="bg-white rounded-lg p-3 text-center border border-gray-100">
        <p class="text-xs text-gray-400">运行中</p><p class="text-xl font-bold mt-1 text-green-600">{{ stats.running }}</p>
      </div>
      <div class="bg-white rounded-lg p-3 text-center border border-gray-100">
        <p class="text-xs text-gray-400">已停止</p><p class="text-xl font-bold mt-1 text-gray-500">{{ stats.stopped }}</p>
      </div>
      <div class="bg-white rounded-lg p-3 text-center border border-gray-100">
        <p class="text-xs text-gray-400">自动联动</p><p class="text-xl font-bold mt-1 text-blue-600">{{ stats.auto }}</p>
      </div>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="locationName" label="区域名称" min-width="180" />
        <el-table-column label="运行状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRunning ? 'success' : 'info'" size="small">
              <span class="inline-block w-2 h-2 rounded-full mr-1" :class="row.isRunning ? 'bg-green-500 animate-pulse' : 'bg-gray-300'" />
              {{ row.isRunning ? '运行中' : '已停止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近启动时间" width="180">
          <template #default="{ row }">{{ row.lastStartTime ? formatTime(row.lastStartTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="触发方式" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="triggerTagType(row.triggerMode)" size="small">{{ triggerLabel(row.triggerMode) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 2026-08-18 修改：移除"联动"按钮。自动联动由环境监测服务在 PM2.5/PM10 超标时
             自动调用触发接口完成，属系统自动行为，不应提供人工模拟触发的按钮入口，故删除。
             操作列仅保留 启动/停止 两个手动控制按钮 -->
        <el-table-column label="操作" width="190" align="center">
          <template #default="{ row }">
            <div class="flex justify-center gap-1">
              <el-button type="success" size="small" :icon="VideoPlay"
                :disabled="row.isRunning || opLoading.id === row.locationId"
                :loading="opLoading.id === row.locationId && opLoading.type === 'start'"
                @click="startSpray(row)">启动</el-button>
              <el-button type="danger" size="small" :icon="VideoPause"
                :disabled="!row.isRunning || opLoading.id === row.locationId"
                :loading="opLoading.id === row.locationId && opLoading.type === 'stop'"
                @click="stopSpray(row)">停止</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !tableData.length" description="暂无喷淋运行状态数据" />
    </div>

    <!-- 合并喷淋控制模块：启用的定时任务摘要卡片 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-3">
        <span class="font-semibold text-gray-700">启用的定时任务</span>
        <div class="flex items-center gap-1">
          <el-button text type="primary" size="small" :icon="Refresh" :loading="scheduleLoading" @click="refreshSchedules">刷新</el-button>
          <el-button text type="primary" size="small" @click="$router.push('/spray/schedule')">管理 →</el-button>
        </div>
      </div>
      <div v-if="scheduleLoadFailed" class="text-sm text-gray-500 text-center py-6">
        定时任务加载失败（接口请求异常）
        <el-button text type="danger" size="small" @click="refreshSchedules">点击重试</el-button>
      </div>
      <div v-else-if="activeSchedules.length" class="space-y-2">
        <div v-for="s in activeSchedules" :key="s.id" class="flex items-center justify-between p-2 rounded bg-gray-50">
          <span class="text-sm">{{ s.taskName }}</span>
          <el-tag size="small">{{ s.startTime }} · {{ s.durationMinutes }}分钟</el-tag>
        </div>
      </div>
      <el-empty v-else description="暂无启用中的定时任务" :image-size="40" />
    </div>

    <!-- 添加区域弹窗：从位置树勾选区域，确定后为每个新区域触发一次手动喷淋、写入操作日志，
         区域列表即批量状态接口返回的启用区域（区域列表不再从操作日志反查） -->
    <el-dialog v-model="addDialogVisible" title="添加区域" width="420px" append-to-body>
      <p class="text-xs text-gray-400 mb-2">勾选要添加的区域，确定后该区域将纳入喷淋管理并展示运行状态。</p>
      <el-tree
        ref="treeRef"
        :data="locationTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'locationName', children: 'children' }"
        empty-text="暂无可用区域"
      />
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="confirmAddRegions">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 2026-08-18 修改：移除 Connection 图标（原"联动"按钮使用），联动按钮已删除
import { Refresh, Plus, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { sprayApi, assetApi } from '@/api'

const loading = ref(false)
const tableData = ref([])

/** 区域列表（直接来自批量状态接口 /api/spray/operate/status/batch）；添加区域弹窗相关状态 */
const topLocations = ref([])
const addDialogVisible = ref(false)
const locationTree = ref([])
const treeRef = ref()
const adding = ref(false)

/** 行内操作加载态：opLoading.id 为当前操作的区域，opLoading.type 区分 start/stop（联动已移除） */
const opLoading = ref({ id: null, type: '' })
/** 启用的定时任务（合并自原喷淋控制页） */
const activeSchedules = ref([])
const scheduleLoading = ref(false)
const scheduleLoadFailed = ref(false)

const stats = computed(() => {
  const running = tableData.value.filter(t => t.isRunning).length
  const stopped = tableData.value.length - running
  const auto = tableData.value.filter(t => t.triggerMode === 'auto').length
  return { total: tableData.value.length, running, stopped, auto }
})

function triggerLabel(mode) {
  return ({ auto: '自动', manual: '手动', schedule: '定时' })[mode] || mode || '-'
}

function triggerTagType(mode) {
  return ({ auto: 'primary', manual: 'warning', schedule: 'success' })[mode] || 'info'
}

function formatTime(t) {
  if (!t) return '-'
  // 后端已返回本地时间格式 "yyyy-MM-dd HH:mm:ss"，直接展示
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}(:\d{2})?$/.test(t)) return t
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 批量查询喷淋运行状态：
 * 区域列表直接以 /api/spray/operate/status/batch 返回的数据为准。
 * 该接口返回所有启用区域（status=1、未删除）的喷淋状态列表，字段含
 * locationId/locationName/isRunning/lastStartTime/triggerMode，本身即区域列表，
 * 因此不再从操作日志分页反查"有哪些区域"，接口调用与数据组装更简洁。
 * @param {boolean} silent 静默模式（轮询用）：不触发 loading、失败不清空表格，避免周期性刷新闪烁。
 */
async function fetchData(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await sprayApi.getBatchStatus()
    const list = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    tableData.value = list
    // 同步维护"区域列表"（供添加区域弹窗回显勾选），与表格数据同源：批量状态接口返回的启用区域
    topLocations.value = list.map(n => ({ id: n.locationId, locationName: n.locationName }))
  } catch (_) {
    // 轮询失败静默保留旧数据，仅首次/手动刷新失败时清空
    if (!silent) tableData.value = []
  } finally {
    if (!silent) loading.value = false
  }
}

/** 位置树平铺为下拉/勾选辅助数组（含子区域） */
function flattenTree(nodes, list = []) {
  nodes.forEach(n => {
    list.push({ id: n.id, locationName: n.locationName })
    if (n.children && n.children.length) flattenTree(n.children, list)
  })
  return list
}

/** 打开"添加区域"弹窗：加载位置树，并把已添加区域（有日志记录的）回显为勾选状态 */
async function openAddDialog() {
  addDialogVisible.value = true
  try {
    const res = await assetApi.getLocationTree({ status: 1 })
    locationTree.value = res.data?.tree || []
    await nextTick()
    treeRef.value?.setCheckedKeys(topLocations.value.map(l => l.id))
  } catch (_) {
    locationTree.value = []
  }
}

/**
 * 确定添加：为勾选区域中"尚未有日志记录"的区域逐个触发手动喷淋（写入操作日志）。
 * 说明：添加区域 = 在该区域产生一条手动喷淋日志；已有日志的区域跳过，避免重复启动报错。
 */
async function confirmAddRegions() {
  const checked = treeRef.value?.getCheckedNodes(false, false) || []
  const checkedIds = flattenTree(checked).map(l => l.id)
  if (!checkedIds.length) {
    ElMessage.warning('请先勾选要添加的区域')
    return
  }
  const existIds = new Set(topLocations.value.map(l => l.id))
  const newIds = checkedIds.filter(id => !existIds.has(id))
  if (!newIds.length) {
    // 文案面向使用者：不暴露"以操作日志为准/已有记录"等内部实现
    ElMessage.info('所选区域均已纳入喷淋管理，无需重复添加')
    addDialogVisible.value = false
    return
  }
  adding.value = true
  try {
    for (const id of newIds) {
      await sprayApi.start({ locationId: id, reason: '运行状态页-添加区域' })
    }
    addDialogVisible.value = false
    ElMessage.success(`已添加 ${newIds.length} 个区域`)
    fetchData()
  } catch (e) {
    ElMessage.error(`添加失败：${e?.message || '接口异常'}`)
  } finally {
    adding.value = false
  }
}

/**
 * 查询启用的定时任务（合并自原喷淋控制页）。
 * @param {boolean} silent 静默模式（轮询用）：不触发按钮 loading、失败不置失败标记。
 */
async function refreshSchedules(silent = false) {
  if (!silent) scheduleLoading.value = true
  try {
    const res = await sprayApi.getActiveSchedules()
    // 后端 /schedule/active 返回 Result.ok(List)，data 直接是数组（非分页对象），
    // 需做 Array.isArray 兼容，否则 res.data.list 恒为 undefined 导致卡片一直显示"暂无"。
    activeSchedules.value = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    scheduleLoadFailed.value = false
  } catch (_) {
    activeSchedules.value = []
    // 轮询失败静默，仅首次/手动刷新失败时显示失败提示
    if (!silent) scheduleLoadFailed.value = true
  } finally {
    if (!silent) scheduleLoading.value = false
  }
}

/**
 * 操作成功后更新单行状态：
 * 1) 先本地乐观更新：立即把该行 isRunning 置为期望值，界面即时反馈，无需等接口；
 * 2) 再静默重新拉取批量状态 /api/spray/operate/status/batch 与后端对齐。
 * 说明：原先依赖单行接口 /api/spray/operate/status 刷新，失败时被静默吞掉导致
 * 停止后界面仍显示运行中、需手动刷新；现改为直接复用 fetchData（batch 接口），
 * 该接口与"区域列表"同源且已实测可靠，保证操作后状态实时一致。
 * @param {number} locationId 区域ID
 * @param {boolean} running 操作后的期望运行状态（乐观更新用，start= true，stop= false）
 */
async function refreshRowStatus(locationId, running) {
  // 乐观更新：操作成功即先更新界面，不等待接口返回
  const row = tableData.value.find(r => r.locationId === locationId)
  if (row) row.isRunning = running
  // 静默重拉批量状态，确保与后端一致（lastStartTime/triggerMode 等字段一并刷新）
  await fetchData(true)
}

/** 启动喷淋（合并自原喷淋控制页） */
async function startSpray(row) {
  try {
    await ElMessageBox.confirm(`确认启动【${row.locationName}】喷淋？`, '操作确认', { type: 'info' })
  } catch { return }
  opLoading.value = { id: row.locationId, type: 'start' }
  try {
    await sprayApi.start({ locationId: row.locationId, reason: '运行状态页-手动启动' })
    ElMessage.success('喷淋已启动')
    await refreshRowStatus(row.locationId, true)
  } catch (_) { /* ignore */ }
  finally { opLoading.value = { id: null, type: '' } }
}

/** 停止喷淋（合并自原喷淋控制页） */
async function stopSpray(row) {
  try {
    await ElMessageBox.confirm(`确认停止【${row.locationName}】喷淋？`, '操作确认', { type: 'warning' })
  } catch { return }
  opLoading.value = { id: row.locationId, type: 'stop' }
  try {
    await sprayApi.stop({ locationId: row.locationId, reason: '运行状态页-手动停止' })
    ElMessage.success('喷淋已停止')
    await refreshRowStatus(row.locationId, false)
  } catch (_) { /* ignore */ }
  finally { opLoading.value = { id: null, type: '' } }
}

// 2026-08-18 移除 autoTrigger（联动触发）函数：自动联动由环境监测服务在指标超标时
// 自动调用 /api/spray/operate/trigger 完成，属系统自动行为，无需前端人工模拟入口，
// 故连同"联动"按钮一并删除（sprayApi.autoTrigger 接口保留，供后端/环境监测服务调用）。

onMounted(() => {
  fetchData()
  refreshSchedules()
  startPolling()
})

/** 轮询定时器：每 30s 静默刷新一次状态与定时任务。
 * 目的：定时计划/自动联动等"由后端自动触发"的启停（页面停留期间不产生前端操作）也能在页面上自动更新，
 * 无需手动点刷新；静默刷新避免周期性闪烁。
 */
let pollTimer = null
let polling = false

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (polling) return // 上一次请求未完成则跳过本次，避免请求叠加
    polling = true
    try {
      await fetchData(true)
      await refreshSchedules(true)
    } finally {
      polling = false
    }
  }, 30 * 1000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onUnmounted(stopPolling)
</script>

<style scoped>.page-container { padding: 0; }</style>
