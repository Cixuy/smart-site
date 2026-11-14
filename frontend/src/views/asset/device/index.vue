<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">设备台账管理</h2>
        <p class="text-sm text-gray-400 mt-1">管理所有设备的全生命周期信息</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增设备</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :model="query" :inline="true" size="default">
        <el-form-item label="设备名称">
          <el-input v-model="query.deviceName" placeholder="请输入" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="设备编码">
          <el-input v-model="query.deviceCode" placeholder="精确匹配" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-tree-select v-model="query.typeId" :data="typeTree" :props="{ label: 'typeName', value: 'id', children: 'children' }"
            check-strictly clearable placeholder="全部" style="width:180px" />
        </el-form-item>
        <el-form-item label="安装位置">
          <!-- 修复：位置树节点字段实际为 locationName，此前 label 配成 name 会选中后显示 locationId，改为 locationName 显示名称 -->
          <el-tree-select v-model="query.locationId" :data="locationTree" :props="{ label: 'locationName', value: 'id', children: 'children' }"
            check-strictly clearable placeholder="全部" style="width:180px" />
        </el-form-item>
        <el-form-item label="在线状态">
          <el-select v-model="query.onlineStatus" clearable placeholder="全部" style="width:120px">
            <el-option label="在线" :value="1" /><el-option label="离线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="query.enableStatus" clearable placeholder="全部" style="width:120px">
            <el-option label="启用" :value="1" /><el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="query.brand" placeholder="请输入" clearable style="width:140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="deviceCode" label="设备编号" width="130" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="typeName" label="设备类型" width="120" />
        <!-- 新增安装位置列：显示位置名称（locationName），优先取后端返回字段，缺失时用缓存兜底，避免显示 locationId -->
        <el-table-column label="安装位置" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row._locationName || '—' }}</template>
        </el-table-column>
        <el-table-column label="经度" width="110">
          <template #default="{ row }">{{ row._gislon }}</template>
        </el-table-column>
        <el-table-column label="纬度" width="110">
          <template #default="{ row }">{{ row._gislat }}</template>
        </el-table-column>
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column prop="model" label="型号" width="100" />
        <el-table-column label="在线状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.onlineStatus === 1 ? 'success' : 'info'" size="small">
              {{ row.onlineStatus === 1 ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enableStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.enableStatus === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button text type="warning" size="small" @click="toggleEnable(row)">
              {{ row.enableStatus === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm title="确认删除该设备？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="flex justify-between items-center mt-4">
        <el-button text type="danger" :disabled="!selectedRows.length" @click="handleBatchDelete">批量删除</el-button>
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchData" />
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="680px" :close-on-click-modal="false" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input v-model="form.deviceName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型" prop="typeId">
              <el-tree-select v-model="form.typeId" :data="typeTree"
                :props="{ label: 'typeName', value: 'id', children: 'children' }" check-strictly placeholder="请选择" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装位置" prop="locationId">
              <!-- 修复：同搜索栏，label 改为 locationName，避免选中后显示 locationId -->
              <el-tree-select v-model="form.locationId" :data="locationTree"
                :props="{ label: 'locationName', value: 'id', children: 'children' }" check-strictly placeholder="请选择" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌" prop="brand">
              <el-input v-model="form.brand" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号" prop="model">
              <el-input v-model="form.model" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产日期" prop="productionDate">
              <el-date-picker v-model="form.productionDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应日期">
              <el-date-picker v-model="form.supplyDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="验收日期">
              <el-date-picker v-model="form.acceptanceDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装日期">
              <el-date-picker v-model="form.installDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用日期">
              <el-date-picker v-model="form.enableDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计报废日期">
              <el-date-picker v-model="form.expectedScrapDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item label="实际报废日期">
              <el-date-picker v-model="form.actualScrapDate" type="date" placeholder="请选择" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="合格证附件">
              <el-upload
                :action="''"
                :auto-upload="false"
                :limit="1"
                :file-list="certificateFileList"
                :on-remove="handleCertRemove"
                :on-change="handleCertChange"
                :before-upload="() => false"
                accept=".jpg,.jpeg,.png,.pdf"
              >
                <el-button size="small">选择文件</el-button>
                <template #tip>
                  <span class="text-xs text-gray-400 ml-2">支持 jpg/png/pdf</span>
                </template>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>
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
import { Search, Refresh, Edit, Delete, Plus } from '@element-plus/icons-vue'
import { assetApi } from '@/api'

const loading = ref(false); const submitLoading = ref(false)
const dialogVisible = ref(false); const isEdit = ref(false)
const formRef = ref(null)
const tableData = ref([]); const typeTree = ref([]); const locationTree = ref([])
const selectedRows = ref([])
const pageNum = ref(1); const pageSize = ref(10); const total = ref(0)
const locationCache = ref(new Map()) // locationId → { locationName, gisLon, gisLat }

// 查询参数
const query = reactive({
  deviceName: '', deviceCode: '', typeId: null, locationId: null,
  onlineStatus: null, enableStatus: null, brand: ''
})

// 表单
const form = reactive({
  id: null, deviceName: '', typeId: null, locationId: null,
  brand: '', model: '',
  productionDate: '', supplyDate: '', acceptanceDate: '', installDate: '',
  enableDate: '', expectedScrapDate: '', actualScrapDate: '', certificateUrl: ''
})

// 合格证附件
const certificateFileList = ref([])
const certificateFile = ref(null)

const rules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  typeId: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择安装位置', trigger: 'change' }],
  brand: [{ required: true, message: '请输入品牌', trigger: 'blur' }],
  model: [{ required: true, message: '请输入型号', trigger: 'blur' }],
  productionDate: [{ required: true, message: '请选择生产日期', trigger: 'change' }]
}

function handleCertChange(file) {
  certificateFile.value = file.raw
}

function handleCertRemove() {
  certificateFile.value = null
}

async function fetchData() {
  loading.value = true
  try {
    const params = {}
    Object.entries(query).forEach(([k, v]) => {
      if (v !== null && v !== '') params[k] = v
    })
    const res = await assetApi.getDevicePage({ ...params, pageNum: pageNum.value, pageSize: pageSize.value })
    const records = res.data?.records ?? res.data?.list ?? []
    total.value = res.data?.total ?? 0

    // 从缓存中取位置经纬度；安装位置名称优先取后端返回的 locationName，缺失时用缓存兜底，避免显示 locationId
    tableData.value = records.map(row => {
      const loc = locationCache.value.get(row.locationId)
      return {
        ...row,
        _gislon: loc?.gisLon ?? '-',
        _gislat: loc?.gisLat ?? '-',
        _locationName: row.locationName || loc?.locationName || ''
      }
    })
  } catch (_) { tableData.value = [] }
  finally { loading.value = false }
}

async function loadTrees() {
  try {
    const [tRes, lRes] = await Promise.all([assetApi.getTypeTree(), assetApi.getLocationTree()])
    typeTree.value = tRes.data?.tree || []
    locationTree.value = lRes.data?.tree || []

    // 预加载所有位置详情（获取经纬度），只做一次
    const allIds = []
    function collectIds(tree) {
      tree?.forEach(n => { if (n.id) allIds.push(n.id); if (n.children) collectIds(n.children) })
    }
    collectIds(locationTree.value)
    if (allIds.length) {
      const results = await Promise.allSettled(allIds.map(id => assetApi.getLocationById(id, { silent: true })))
      results.forEach((r, i) => {
        if (r.status === 'fulfilled' && r.value?.code === 0 && r.value?.data) {
          const d = r.value.data
          locationCache.value.set(allIds[i], { locationName: d.locationName, gisLon: d.gisLon, gisLat: d.gisLat })
        }
      })
    }
  } catch (_) { /* ignore */ }
}

function buildFormFromRow(row) {
  return {
    id: row.id,
    deviceName: row.deviceName || '',
    typeId: row.typeId ?? null,
    locationId: row.locationId ?? null,
    brand: row.brand || '',
    model: row.model || '',
    productionDate: row.productionDate || '',
    supplyDate: row.supplyDate || '',
    acceptanceDate: row.acceptanceDate || '',
    installDate: row.installDate || '',
    enableDate: row.enableDate || '',
    expectedScrapDate: row.expectedScrapDate || '',
    actualScrapDate: row.actualScrapDate || '',
    certificateUrl: row.certificateUrl || ''
  }
}

const defaultForm = () => ({
  id: null, deviceName: '', typeId: null, locationId: null,
  brand: '', model: '',
  productionDate: '', supplyDate: '', acceptanceDate: '', installDate: '',
  enableDate: '', expectedScrapDate: '', actualScrapDate: '', certificateUrl: ''
})

function openDialog(row) {
  isEdit.value = !!row
  certificateFile.value = null
  certificateFileList.value = []
  if (row) {
    Object.assign(form, buildFormFromRow(row))
  } else {
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  certificateFile.value = null
  certificateFileList.value = []
}

function buildSubmitData() {
  // 仅提交表单字段，过滤空字符串
  const fields = ['deviceName', 'typeId', 'locationId', 'brand', 'model',
    'productionDate', 'supplyDate', 'acceptanceDate', 'installDate',
    'enableDate', 'expectedScrapDate', 'actualScrapDate', 'certificateUrl']
  const data = {}
  fields.forEach(k => {
    if (form[k] !== '' && form[k] !== null && form[k] !== undefined) {
      data[k] = form[k]
    }
  })
  if (isEdit.value) data.id = form.id
  return data
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = buildSubmitData()
    if (isEdit.value) {
      await assetApi.updateDevice(data)
    } else {
      // 新增：先创建设备，如有合格证文件再上传
      const res = await assetApi.createDevice(data)
      const deviceId = res.data?.id
      if (deviceId && certificateFile.value) {
        const fd = new FormData()
        fd.append('file', certificateFile.value)
        fd.append('deviceId', deviceId)
        await assetApi.uploadCertificate(fd)
      }
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (_) { /* ignore */ }
  finally { submitLoading.value = false }
}

async function handleDelete(row) {
  try {
    await assetApi.deleteDeviceBatch([row.id])
    ElMessage.success('删除成功')
    fetchData()
  } catch (_) { /* ignore */ }
}

async function handleBatchDelete() {
  try {
    const ids = selectedRows.value.map(r => r.id)
    await assetApi.deleteDeviceBatch(ids)
    ElMessage.success('批量删除成功')
    fetchData()
  } catch (_) { /* ignore */ }
}

async function toggleEnable(row) {
  try {
    const newStatus = row.enableStatus === 1 ? 0 : 1
    await assetApi.updateDeviceStatus(row.id, newStatus)
    row.enableStatus = newStatus
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  } catch (_) { /* ignore */ }
}

function handleSelectionChange(val) {
  selectedRows.value = val
}

function resetQuery() {
  Object.assign(query, {
    deviceName: '', deviceCode: '', typeId: null, locationId: null,
    onlineStatus: null, enableStatus: null, brand: ''
  })
  pageNum.value = 1
  fetchData()
}

onMounted(() => { loadTrees(); fetchData() })
</script>

<style scoped>
.page-container { padding: 0; }
</style>
