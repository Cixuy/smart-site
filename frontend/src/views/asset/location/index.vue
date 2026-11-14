<template>
  <div class="page-container space-y-4">
    <!-- 页头 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">空间位置管理</h2>
        <p class="text-sm text-gray-400 mt-1">GIS地图选点 · 区域树形结构 · 坐标拾取配置</p>
      </div>
      <div class="flex items-center gap-3">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="tree">
            <el-icon><List /></el-icon> 树形列表
          </el-radio-button>
          <el-radio-button value="amap">
            <el-icon><MapLocation /></el-icon> 高德地图
          </el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增位置</el-button>
      </div>
    </div>

    <!-- 树形表格视图 -->
    <template v-if="viewMode === 'tree'">
      <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
        <el-table
          :data="tableData" v-loading="loading" row-key="id" border
          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
          default-expand-all style="width:100%"
        >
          <el-table-column prop="locationName" label="位置名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="level" label="层级" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.level === 1 ? '' : row.level === 2 ? 'success' : 'warning'" size="small">
                L{{ row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="经度" width="120" align="right">
            <template #default="{ row }">
              <span class="text-xs font-mono">{{ row.gisLon?.toFixed(4) ?? '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="纬度" width="120" align="right">
            <template #default="{ row }">
              <span class="text-xs font-mono">{{ row.gisLat?.toFixed(4) ?? '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="70" align="center" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 移除：按需求不再展示负责人ID，避免暴露内部用户ID -->
          
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" :icon="Aim" @click="openMapPicker(row)">坐标</el-button>
              <el-button text type="primary" size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除？" @confirm="handleDelete(row)">
                <template #reference>
                  <el-button text type="danger" size="small" :icon="Delete">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 高德地图视图 -->
    <LocationMapAmap
      v-if="viewMode === 'amap'"
      :data="tableData"
      @edit="openDialog"
      @select="onMapSelect"
    />

    <!-- 增/改对话框（含坐标拾取折叠面板） -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑位置' : '新增位置'"
      width="580px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="位置名称" prop="locationName">
          <el-input v-model="form.locationName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="父级位置">
          <el-tree-select
            v-model="form.parentId"
            :data="tableData"
            :props="{ label: 'locationName', value: 'id', children: 'children' }"
            check-strictly clearable placeholder="无（根节点）"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" size="small" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1" :inactive-value="0"
            active-text="启用" inactive-text="禁用"
          />
        </el-form-item>
        <!-- 移除：按需求不再维护/展示负责人ID -->

        <!-- GIS 坐标拾取 -->
        <el-divider content-position="left">
          <el-icon><Aim /></el-icon> GIS坐标配置
        </el-divider>
        <el-form-item label="经度 (X)" prop="gisLon">
          <el-input-number
            v-model="form.gisLon" :precision="4" :step="0.001"
            :min="-180" :max="180" controls-position="right" style="width:100%"
            @change="syncCoordToPicker"
          />
        </el-form-item>
        <el-form-item label="纬度 (Y)" prop="gisLat">
          <el-input-number
            v-model="form.gisLat" :precision="4" :step="0.001"
            :min="-90" :max="90" controls-position="right" style="width:100%"
            @change="syncCoordToPicker"
          />
        </el-form-item>
        <el-form-item label="地图选点">
          <CoordinatePickerAmap
            ref="amapPickerRef"
            :model-value="pickerCoord"
            :zoom="15"
            @update:model-value="onPickerUpdate"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 快捷坐标编辑弹窗（无其他字段，仅坐标） -->
    <el-dialog
      v-model="mapPickerVisible"
      title="位置坐标拾取"
      width="540px"
      :close-on-click-modal="false"
    >
      <div v-if="pickingRow" class="space-y-3">
        <div class="flex items-center gap-3">
          <span class="text-sm text-gray-500">位置：</span>
          <el-tag>{{ pickingRow.locationName }}</el-tag>
        </div>
        <CoordinatePickerAmap
          ref="quickAmapPickerRef"
          :model-value="quickCoord"
          :zoom="15"
          @update:model-value="onQuickPickerUpdate"
        />
        <div class="flex justify-end">
          <el-button type="primary" :loading="quickLoading" @click="saveQuickCoord">保存坐标</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete, List, MapLocation, Aim } from '@element-plus/icons-vue'
import { assetApi } from '@/api'
import CoordinatePickerAmap from '@/views/monitor/components/CoordinatePickerAmap.vue'
import LocationMapAmap from '@/views/monitor/components/LocationMapAmap.vue'

// 视图模式
const viewMode = ref('tree')

// 树形数据
const loading = ref(false)
const tableData = ref([])

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const amapPickerRef = ref(null)

const form = reactive({
  // 移除 responsibleUserId 字段：界面不再维护负责人ID，提交时也不会带上该字段
  id: null, locationName: '', parentId: null, sort: 0,
  status: 1,
  gisLon: undefined, gisLat: undefined
})

const rules = {
  locationName: [{ required: true, message: '请输入位置名称', trigger: 'blur' }]
}

// 坐标拾取器双向同步
const pickerCoord = computed(() => ({
  x: form.gisLon ?? 116.43,
  y: form.gisLat ?? 39.92
}))

function syncCoordToPicker() {
  // 自动触发更新
}

function onPickerUpdate(coord) {
  form.gisLon = coord.x
  form.gisLat = coord.y
}

// 快捷坐标编辑弹窗
const mapPickerVisible = ref(false)
const pickingRow = ref(null)
const quickLoading = ref(false)
const quickAmapPickerRef = ref(null)
const quickCoordLocal = reactive({ x: 116.43, y: 39.92 })

const quickCoord = computed(() => ({
  x: quickCoordLocal.x,
  y: quickCoordLocal.y
}))

function openMapPicker(row) {
  pickingRow.value = row
  quickCoordLocal.x = row.gisLon ?? 116.43
  quickCoordLocal.y = row.gisLat ?? 39.92
  mapPickerVisible.value = true
}

function onQuickPickerUpdate(coord) {
  quickCoordLocal.x = coord.x
  quickCoordLocal.y = coord.y
}

async function saveQuickCoord() {
  quickLoading.value = true
  try {
    await assetApi.updateLocation({
      id: pickingRow.value.id,
      gisLon: quickCoordLocal.x,
      gisLat: quickCoordLocal.y
    })
    ElMessage.success('坐标已更新')
    mapPickerVisible.value = false
    fetchData()
  } catch (_) { /* ignore */ }
  finally { quickLoading.value = false }
}

// ========== 带超时的网络请求包装 ==========
function withTimeout(promise, ms = 5000) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('TIMEOUT')), ms))
  ])
}

// ========== CRUD ==========
async function fetchData() {
  loading.value = true
  try {
    // 并行请求：树结构 + 平铺列表（含status/负责人）
    const [treeRes, listRes] = await Promise.all([
      withTimeout(assetApi.getLocationTree(), 5000),
      withTimeout(assetApi.getLocationList({}), 5000)
    ])
    const tree = treeRes?.data?.tree ?? []
    const list = listRes?.data?.list ?? []

    // 用列表数据补充树节点缺失的 status 字段（不再补充负责人字段，界面不展示）
    const extraMap = new Map(list.map(item => [item.id, item]))
    function enrich(nodes) {
      for (const node of nodes) {
        const extra = extraMap.get(node.id)
        if (extra) {
          node.status = extra.status
        }
        if (node.children?.length) {
          enrich(node.children)
        }
      }
    }
    enrich(tree)
    tableData.value = Array.isArray(tree) ? tree : []
  } catch (err) {
    tableData.value = []
    if (err?.message === 'TIMEOUT') {
      ElMessage.warning('网络连接异常，请检查网络后刷新重试')
    }
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    form.id = row.id
    form.locationName = row.locationName
    form.parentId = row.parentId ?? null
    form.sort = row.sort ?? 0
    form.status = row.status ?? 1
    form.gisLon = row.gisLon
    form.gisLat = row.gisLat
  } else {
    form.id = null
    form.locationName = ''
    form.parentId = null
    form.sort = 0
    form.status = 1
    form.gisLon = undefined
    form.gisLat = undefined
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    // 过滤空值，避免 null/undefined 覆盖后端已有数据
    const data = Object.fromEntries(
      Object.entries({ ...form }).filter(([, v]) => v !== null && v !== undefined && v !== '')
    )
    if (isEdit.value) await assetApi.updateLocation(data)
    else await assetApi.createLocation(data)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (_) { /* ignore */ }
  finally { submitLoading.value = false }
}

async function handleDelete(row) {
  try {
    await assetApi.deleteLocation(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (_) { /* ignore */ }
}

function onMapSelect(row) {
  if (row) openMapPicker(row)
}

onMounted(() => fetchData())
</script>

<style scoped>
.page-container { padding: 0; }
:deep(.el-divider__text) {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}
</style>
