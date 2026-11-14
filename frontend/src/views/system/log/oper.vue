<template>
  <div class="page-container space-y-4">
    <div><h2 class="text-xl font-bold text-gray-800">操作日志</h2><p class="text-sm text-gray-400 mt-1">查看用户操作记录</p></div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="操作模块">
          <el-input v-model="query.title" clearable placeholder="如: 设备台账" style="width:160px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model="query.operType" clearable placeholder="新增/修改/删除" style="width:150px" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operByName" clearable placeholder="请输入姓名" style="width:150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:110px">
            <el-option label="成功" :value="0" />
            <el-option label="失败" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="title" label="操作模块" width="130" show-overflow-tooltip />
        <el-table-column label="操作类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.operType)" size="small">{{ row.operType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operByName" label="操作人" width="100" />
        <el-table-column prop="operParam" label="请求参数" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-xs text-gray-500 font-mono">{{ row.operParam }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
              {{ row.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operTime" label="操作时间" width="170" />
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="View" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-4 justify-end"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="fetchData"
      />
    </div>

    <!-- 操作日志详情对话框 -->
    <el-dialog v-model="detailVisible" title="操作详情" width="600px">
      <div v-if="detailData" class="space-y-3">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="日志ID">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item label="操作模块">{{ detailData.title }}</el-descriptions-item>
          <el-descriptions-item label="操作类型">
            <el-tag :type="typeTag(detailData.operType)" size="small">{{ detailData.operType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailData.operByName }}</el-descriptions-item>
          <el-descriptions-item label="操作人ID">{{ detailData.operById }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ detailData.operTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailData.status === 0 ? 'success' : 'danger'" size="small">
              {{ detailData.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="detailData.errorMsg ? 1 : 2">
            <span class="text-red-500">{{ detailData.errorMsg || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="detailData.operParam">
          <div class="text-sm font-medium text-gray-700 mb-1">请求参数 (JSON)</div>
          <div class="bg-gray-50 rounded-lg p-3 border border-gray-200">
            <pre class="text-xs text-gray-700 whitespace-pre-wrap break-all font-mono">{{ formatJson(detailData.operParam) }}</pre>
          </div>
        </div>
      </div>
      <div v-else v-loading="detailLoading" style="min-height:120px" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { logApi } from '@/api'

const loading = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const query = reactive({
  title: '',
  operType: '',
  operByName: '',
  status: null,
  timeRange: null
})

const detailVisible = ref(false)
const detailData = ref(null)
const detailLoading = ref(false)

/** 操作类型标签颜色 */
function typeTag(t) {
  const map = {}
  if (!t) return 'info'
  if (t.includes('新增') || t.includes('添加') || t.includes('创建')) return 'success'
  if (t.includes('修改') || t.includes('编辑') || t.includes('更新')) return 'warning'
  if (t.includes('删除') || t.includes('移除')) return 'danger'
  if (t.includes('查询') || t.includes('查看') || t.includes('导出')) return 'info'
  return ''
}

/** 格式化 JSON 字符串，格式化失败则原样返回 */
function formatJson(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

/** 构建请求参数 */
function buildParams() {
  const params = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    title: query.title || undefined,
    operType: query.operType || undefined,
    operByName: query.operByName || undefined,
    status: query.status !== null && query.status !== '' ? query.status : undefined
  }
  if (query.timeRange && query.timeRange.length === 2) {
    params.beginTime = query.timeRange[0]
    params.endTime = query.timeRange[1]
  }
  return params
}

async function fetchData() {
  loading.value = true
  try {
    const res = await logApi.getOperLogPage(buildParams())
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (_) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  detailVisible.value = true
  detailData.value = null
  detailLoading.value = true
  try {
    const res = await logApi.getOperLogById(row.id)
    detailData.value = res.data || null
  } catch (_) {
    detailData.value = null
  } finally {
    detailLoading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { title: '', operType: '', operByName: '', status: null, timeRange: null })
  pageNum.value = 1
  fetchData()
}

onMounted(() => fetchData())
</script>

<style scoped>.page-container { padding: 0; }</style>
