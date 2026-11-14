<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div class="flex items-center gap-3">
        <el-button :icon="ArrowLeft" circle plain @click="goBack" />
        <div><h2 class="text-xl font-bold text-gray-800">喷淋定时任务</h2><p class="text-sm text-gray-400 mt-1">管理喷淋系统的定时任务</p></div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增任务</el-button>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column prop="locationName" label="位置" width="140" />
        <el-table-column label="开始时间" width="110">
          <template #default="{ row }">{{ row.startTime }}</template>
        </el-table-column>
        <el-table-column label="时长" width="90" align="center">
          <template #default="{ row }">{{ row.durationMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="周期" width="90" align="center">
          <template #default="{ row }"><el-tag size="small">{{ cycleLabel(row.cycleType) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="周期配置" min-width="130">
          <template #default="{ row }">{{ row.cycleConfigDesc || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-switch :model-value="row.status===1" @change="val => toggleSchedule(row, val)" /></template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="delSchedule(row)">
              <template #reference><el-button text type="danger" size="small" :icon="Delete">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt-4 justify-end" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchData" />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑定时任务' : '新增定时任务'" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="任务名称" prop="taskName"><el-input v-model="form.taskName" placeholder="请输入" /></el-form-item>
        <el-form-item label="执行位置" prop="locationId"><el-select v-model="form.locationId" placeholder="请选择" style="width:100%"><el-option v-for="loc in locations" :key="loc.id" :label="loc.locationName" :value="loc.id" /></el-select></el-form-item>
        <el-form-item label="开始时间" prop="startTime"><el-time-select v-model="form.startTime" start="00:00" step="00:05" end="23:55" placeholder="选择开始时间" style="width:100%" /></el-form-item>
        <el-form-item label="持续时长" prop="durationMinutes"><el-input-number v-model="form.durationMinutes" :min="1" :max="1440" style="width:100%" /><div class="text-xs text-gray-400 mt-1">单位：分钟</div></el-form-item>
        <el-form-item label="执行周期" prop="cycleType">
          <el-select v-model="form.cycleType" style="width:100%">
            <el-option label="每天" value="daily" />
            <el-option label="每周" value="weekly" />
            <el-option label="每月" value="monthly" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.cycleType === 'weekly'" label="周几">
          <el-select v-model="form.cycleConfig" multiple placeholder="选择日期" style="width:100%">
            <el-option v-for="d in weekDays" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="form.cycleType === 'monthly'" label="每月几号">
          <el-select v-model="form.cycleConfig" multiple placeholder="选择日期" style="width:100%">
            <el-option v-for="d in 31" :key="d" :label="`${d}号`" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { sprayApi, assetApi } from '@/api'

const router = useRouter()

/** 返回上级页面（喷淋控制/运行状态页，两模块已合并为 spray/status） */
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/spray/status')
}

const weekDays = [
  { label: '周一', value: 'MON' },
  { label: '周二', value: 'TUE' },
  { label: '周三', value: 'WED' },
  { label: '周四', value: 'THU' },
  { label: '周五', value: 'FRI' },
  { label: '周六', value: 'SAT' },
  { label: '周日', value: 'SUN' }
]

const locations = ref([])
const loading = ref(false); const dialogVisible = ref(false); const isEdit = ref(false); const submitLoading = ref(false)
const formRef = ref(null); const tableData = ref([])
const pageNum = ref(1); const pageSize = ref(10); const total = ref(0)
const form = reactive({ id: null, taskName: '', locationId: null, startTime: '', durationMinutes: 5, cycleType: 'daily', cycleConfig: [], remark: '' })
const rules = {
  taskName: [{ required: true, message: '请输入任务名称' }],
  locationId: [{ required: true, message: '请选择执行位置' }],
  startTime: [{ required: true, message: '请选择开始时间' }],
  durationMinutes: [{ required: true, message: '请输入持续时长' }],
  cycleType: [{ required: true, message: '请选择执行周期' }]
}

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
    const res = await assetApi.getLocationTree()
    locations.value = flattenTree(res.data?.tree || [])
  } catch (_) { locations.value = [] }
}

function cycleLabel(type) {
  return ({ daily: '每天', weekly: '每周', monthly: '每月' })[type] || type
}

/** 后端 cycleConfig 为 JSON 字符串（weekly 如 ["MON","WED"]，monthly 如 [1,15]） */
function parseCycleConfig(row) {
  if (!row.cycleConfig) return []
  try { return JSON.parse(row.cycleConfig) } catch (_) { return [] }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await sprayApi.getSchedulePage({ pageNum: pageNum.value, pageSize: pageSize.value })
    // 分页接口返回 PageResult：{ total, pages, pageNum, pageSize, records }
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (_) {
    tableData.value = []
    ElMessage.warning('定时任务加载失败，请检查接口或稍后重试')
  } finally { loading.value = false }
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { ...row, cycleConfig: parseCycleConfig(row) })
  } else {
    Object.assign(form, { id: null, taskName: '', locationId: null, startTime: '', durationMinutes: 5, cycleType: 'daily', cycleConfig: [], remark: '' })
  }
  dialogVisible.value = true
}

function resetForm() { formRef.value?.resetFields() }

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  const payload = {
    ...form,
    startTime: form.startTime.length === 5 ? `${form.startTime}:00` : form.startTime,
    cycleConfig: form.cycleConfig.length ? JSON.stringify(form.cycleConfig) : undefined
  }
  try {
    // 保存前校验同一位置的时间冲突（新增时 excludeId=0）
    const conflictRes = await sprayApi.checkConflict({
      excludeId: isEdit.value ? form.id : 0,
      locationId: payload.locationId,
      startTime: payload.startTime,
      durationMinutes: payload.durationMinutes,
      cycleType: payload.cycleType,
      cycleConfig: payload.cycleConfig
    })
    const conflictData = conflictRes.data
    const hasConflict = typeof conflictData === 'boolean' ? conflictData : conflictData?.hasConflict
    if (hasConflict) {
      ElMessage.warning('与同一位置的其他启用任务存在时间冲突，请调整开始时间或时长')
      return
    }
    if (isEdit.value) await sprayApi.updateSchedule(payload)
    else await sprayApi.createSchedule(payload)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false; fetchData()
  } catch (_) { /* ignore */ }
  finally { submitLoading.value = false }
}

async function toggleSchedule(row, val) {
  try { await sprayApi.toggleSchedule(row.id, val ? 1 : 0); ElMessage.success('操作成功'); fetchData() } catch (_) { /* ignore */ }
}

async function delSchedule(row) {
  try { await sprayApi.deleteSchedule(row.id); ElMessage.success('删除成功'); fetchData() } catch (_) { /* ignore */ }
}

onMounted(() => { loadLocations(); fetchData() })
</script>

<style scoped>.page-container { padding: 0; }</style>
