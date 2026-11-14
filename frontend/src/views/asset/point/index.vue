<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">监测点管理</h2>
        <p class="text-sm text-gray-400 mt-1">管理环境监测点，传感器通过 parentId 关联到监测点</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增监测点</el-button>
    </div>

    <!-- 筛选栏：名称 / 安装位置 模糊查询 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100 flex flex-wrap items-center gap-4">
      <el-input v-model="query.name" placeholder="监测点名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
      <el-input v-model="query.location" placeholder="安装位置" clearable style="width: 200px" @keyup.enter="handleSearch" />
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="location" label="安装位置" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sensorCount" label="传感器数量" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sensorCount > 0 ? 'success' : 'info'">{{ row.sensorCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联传感器ID" min-width="220">
          <template #default="{ row }">
            <template v-if="row.sensorIds && row.sensorIds.length">
              <el-tag v-for="sid in row.sensorIds" :key="sid" size="small" class="mr-1">{{ sid }}</el-tag>
            </template>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="删除后不可恢复，确认删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="flex justify-end mt-4">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchData" />
      </div>
    </div>

    <!-- 新增 / 编辑对话框：仅名称（必填）和安装位置 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑监测点' : '新增监测点'" width="520px" :close-on-click-modal="false" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="监测点名称" prop="name">
          <el-input v-model="form.name" placeholder="如：工地东门监测点" />
        </el-form-item>
        <el-form-item label="安装位置" prop="location">
          <el-input v-model="form.location" placeholder="如：东门入口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { pointApi } from '@/api'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 筛选条件（名称、安装位置均为模糊匹配）
const query = reactive({ name: '', location: '' })

// 表单数据：仅名称和安装位置可编辑，指标及阈值通过传感器管理接口配置
const form = reactive({ id: null, name: '', location: '' })

const rules = {
  name: [{ required: true, message: '请输入监测点名称', trigger: 'blur' }]
}

/** 分页查询监测点列表 */
async function fetchData() {
  loading.value = true
  try {
    const res = await pointApi.getPointPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      name: query.name || undefined,
      location: query.location || undefined
    })
    tableData.value = res.data?.records ?? res.data?.list ?? []
    total.value = res.data?.total || 0
  } catch (_) { tableData.value = [] }
  finally { loading.value = false }
}

/** 查询：回到第一页再刷新 */
function handleSearch() { pageNum.value = 1; fetchData() }

/** 重置筛选条件 */
function handleReset() { query.name = ''; query.location = ''; handleSearch() }

/** 打开新增/编辑对话框 */
function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { id: row.id, name: row.name, location: row.location })
  } else {
    Object.assign(form, { id: null, name: '', location: '' })
  }
  dialogVisible.value = true
}

function resetForm() { formRef.value?.resetFields() }

/** 提交新增/修改（名称必填，位置可空） */
async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    // 名称/位置均去除首尾空格；空位置不传，避免覆盖后端已有数据
    const data = { name: form.name.trim() }
    if (form.location.trim()) data.location = form.location.trim()
    if (isEdit.value) await pointApi.updatePoint(form.id, data)
    else await pointApi.createPoint(data)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (_) { /* 错误信息由 request 拦截器统一提示 */ }
  finally { submitLoading.value = false }
}

/** 删除监测点（存在关联运行传感器时后端会拒绝并提示错误） */
async function handleDelete(row) {
  try {
    await pointApi.deletePoint(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (_) { /* 错误信息由 request 拦截器统一提示 */ }
}

onMounted(() => fetchData())
</script>

<style scoped>
.page-container { padding: 0; }
</style>
