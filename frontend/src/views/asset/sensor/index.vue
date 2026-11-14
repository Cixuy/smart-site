<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">传感器管理</h2>
        <!-- 按用户要求移除 parentType 说明文字，保留简洁描述 -->
        <p class="text-sm text-gray-400 mt-1">管理设备与环境监测点下的传感器</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增传感器</el-button>
    </div>

    <!-- 搜索栏（2026-08-17 新增：与设备管理页查询模块保持同风格，支持名称/类型/父级类型/状态组合筛选） -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :model="query" :inline="true" size="default">
        <el-form-item label="传感器名称">
          <el-input v-model="query.sensorName" placeholder="请输入" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="传感器类型">
          <el-select v-model="query.monitorType" clearable placeholder="全部" style="width:180px">
            <el-option-group v-for="group in monitorTypeGroups" :key="group.label" :label="group.label">
              <el-option v-for="opt in group.options" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="父级类型">
          <el-select v-model="query.parentType" clearable placeholder="全部" style="width:140px">
            <el-option v-for="(label, val) in parentTypeLabels" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
            <el-option v-for="(label, val) in statusLabels" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sensorName" label="名称" min-width="140" show-overflow-tooltip />
        <!-- 新 API 无 deviceId，改用 parentType + parentName 展示父级信息 -->
        <!-- 2026-08-18 修改：支持 parentType=3 喷淋模块（绑定位置树），列表同样展示其标签与位置名称 -->
        <el-table-column label="所属父级" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.parentType === 1 || row.parentType === 2 || row.parentType === 3">
              <el-tag size="small" :type="row.parentType === 1 ? 'primary' : row.parentType === 2 ? 'success' : 'warning'" class="mr-1">
                {{ parentTypeLabels[row.parentType] }}
              </el-tag>
              <span>{{ row.parentName || `#${row.parentId}` }}</span>
            </template>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="传感器类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ monitorTypeLabels[row.monitorType] || row.monitorType }}</el-tag>
          </template>
        </el-table-column>
        <!-- 预警/报警为"超出区间才触发"，故展示为 ＜下限 或 ＞上限 的形式（如 ＜5 或 ＞30） -->
        <el-table-column label="预警范围" min-width="160" align="center">
          <template #default="{ row }">
            <template v-if="row.warningLower !== null && row.warningLower !== undefined || row.warningUpper !== null && row.warningUpper !== undefined">
              <span v-if="row.warningLower !== null && row.warningLower !== undefined">＜{{ row.warningLower }}</span>
              <span v-if="row.warningLower !== null && row.warningLower !== undefined && row.warningUpper !== null && row.warningUpper !== undefined"> 或 </span>
              <span v-if="row.warningUpper !== null && row.warningUpper !== undefined">＞{{ row.warningUpper }}</span>
            </template>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="报警范围" min-width="160" align="center">
          <template #default="{ row }">
            <template v-if="row.alarmLower !== null && row.alarmLower !== undefined || row.alarmUpper !== null && row.alarmUpper !== undefined">
              <span v-if="row.alarmLower !== null && row.alarmLower !== undefined">＜{{ row.alarmLower }}</span>
              <span v-if="row.alarmLower !== null && row.alarmLower !== undefined && row.alarmUpper !== null && row.alarmUpper !== undefined"> 或 </span>
              <span v-if="row.alarmUpper !== null && row.alarmUpper !== undefined">＞{{ row.alarmUpper }}</span>
            </template>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <!-- 按用户要求移除"最新值(lastValue)"与"最后采集(lastCollectTime)"两列，仅保留状态等关键信息，简化列表展示 -->
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'"
              size="small"
            >{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <!-- 状态修改走独立接口 PUT /api/asset/sensor/{id}/status -->
            <el-dropdown trigger="click" @command="(s) => handleStatusChange(row, s)">
              <el-button text type="warning" size="small">
                状态<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="1">运行</el-dropdown-item>
                  <el-dropdown-item command="0">停止</el-dropdown-item>
                  <el-dropdown-item command="2">故障</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-popconfirm title="逻辑删除，历史环境数据保留，确认删除？" @confirm="handleDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑传感器' : '新增传感器'" width="600px" :close-on-click-modal="false" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="sensorName">
          <el-input v-model="form.sensorName" placeholder="请输入" />
        </el-form-item>

        <!-- 父级关系：新增时选择，编辑时只读展示（接口规定 parentId/parentType 仅创建时确定，修改不改变） -->
        <template v-if="isEdit">
          <!-- 2026-08-18 修改：编辑回显支持 parentType=3 喷淋模块（warning 标签） -->
          <el-form-item label="所属父级">
            <el-tag size="small" :type="form.parentType === 1 ? 'primary' : form.parentType === 2 ? 'success' : form.parentType === 3 ? 'warning' : 'info'" class="mr-1">
              {{ parentTypeLabels[form.parentType] }}
            </el-tag>
            <span>{{ form.parentName || (form.parentId ? `#${form.parentId}` : '-') }}</span>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="父级类型" prop="parentType">
            <el-select v-model="form.parentType" placeholder="请选择父级类型" style="width:100%">
              <el-option label="设备" :value="1" />
              <el-option label="环境监测点" :value="2" />
              <!-- 2026-08-18 新增：喷淋模块，简化喷淋联动——PM2.5/PM10 传感器直接挂到位置树的区域节点下 -->
              <el-option label="喷淋模块" :value="3" />
              <el-option label="无父级关联" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.parentType === 1" label="所属设备" prop="parentId">
            <el-select v-model="form.parentId" filterable placeholder="请选择设备" style="width:100%">
              <!-- 按用户确认的需求：设备仅保留塔吊/升降机两类（摄像头、传感器等其他类型全部过滤），分组展示、同类放一起 -->
              <el-option-group v-if="deviceGroupOptions.tower.length" label="塔吊类">
                <el-option
                  v-for="dev in deviceGroupOptions.tower"
                  :key="dev.id"
                  :label="dev.deviceName || dev.name"
                  :value="dev.id"
                />
              </el-option-group>
              <el-option-group v-if="deviceGroupOptions.lift.length" label="升降机类">
                <el-option
                  v-for="dev in deviceGroupOptions.lift"
                  :key="dev.id"
                  :label="dev.deviceName || dev.name"
                  :value="dev.id"
                />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="form.parentType === 2" label="所属监测点" prop="parentId">
            <el-select v-model="form.parentId" filterable placeholder="请选择环境监测点" style="width:100%">
              <el-option v-for="p in pointOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <!-- 2026-08-18 新增：parentType=3 喷淋模块，所属喷淋模块直接绑定位置树（区域节点），
               简化喷淋联动——不再需要在设备/监测点下层层配置，PM2.5/PM10 传感器挂到区域节点即可 -->
          <el-form-item v-else-if="form.parentType === 3" label="所属喷淋模块" prop="parentId">
            <el-tree-select v-model="form.parentId" :data="locationTree"
              :props="{ label: 'locationName', value: 'id', children: 'children' }"
              check-strictly filterable placeholder="请选择区域" style="width:100%" />
          </el-form-item>
          <el-form-item v-else label="父级关联">
            <el-text type="info">无父级关联</el-text>
          </el-form-item>
        </template>

        <!-- monitorType 枚举按 API 文档：环境类大写(TEMP/HUMIDITY/PM25/PM10/NOISE/WIND)，荷载/风速/运行类小写 -->
        <!-- 2026-08-18 修改：选项改为 sensorTypeGroupsForForm，按已选父级（环境监测点/塔吊/升降机）过滤，只保留对应类型的传感器 -->
        <el-form-item label="传感器类型" prop="monitorType">
          <el-select v-model="form.monitorType" placeholder="请选择" style="width:100%">
            <el-option-group
              v-for="group in sensorTypeGroupsForForm"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="opt in group.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="如 ℃ / μg/m³" />
        </el-form-item>

        <el-divider content-position="left">阈值设置</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="预警上限">
              <el-input-number v-model="form.warningUpper" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警下限">
              <el-input-number v-model="form.warningLower" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <!-- 2026-08-18 修改：报警上下限均非必填（API 接口不要求必填），留空则该方向不告警 -->
            <el-form-item label="报警上限" prop="alarmUpper">
              <el-input-number v-model="form.alarmUpper" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报警下限">
              <el-input-number v-model="form.alarmLower" :precision="2" style="width:100%" />
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete, ArrowDown } from '@element-plus/icons-vue'
import { assetApi, pointApi } from '@/api'

const loading = ref(false); const submitLoading = ref(false)
const dialogVisible = ref(false); const isEdit = ref(false)
const formRef = ref(null); const tableData = ref([])
const pageNum = ref(1); const pageSize = ref(10); const total = ref(0)
const deviceGroupOptions = ref({ tower: [], lift: [] })
const pointOptions = ref([])
// 2026-08-18 新增：位置树数据（parentType=3 喷淋模块的父级候选，直接绑定位置树区域节点）
const locationTree = ref([])

// 查询参数（2026-08-17 新增：sensorName 模糊、monitorType/parentType/status 精确筛选，与设备管理页 query 风格一致）
const query = reactive({
  sensorName: '', monitorType: null, parentType: null, status: null
})

// 父级类型标签：0-无父级，1-设备，2-环境监测点，3-喷淋模块（2026-08-18 新增，简化喷淋联动）
const parentTypeLabels = { 0: '无父级', 1: '设备', 2: '环境监测点', 3: '喷淋模块' }

// monitorType 枚举（按《传感器管理 API 文档》）：
// 环境类：TEMP/HUMIDITY/PM25/PM10/NOISE/WIND（大写）
// 荷载/风速类：load/moment/moment_percent/load_percent/wind_speed/amplitude/height
// 运行状态类：passenger_count/speed/door_interlock
const monitorTypeGroups = [
  {
    label: '环境监测类',
    options: [
      { label: '温度', value: 'TEMP' },
      { label: '湿度', value: 'HUMIDITY' },
      { label: 'PM2.5', value: 'PM25' },
      { label: 'PM10', value: 'PM10' },
      { label: '噪声', value: 'NOISE' },
      { label: '风向', value: 'WIND' }
    ]
  },
  {
    label: '荷载/风速类',
    options: [
      { label: '荷载', value: 'load' },
      { label: '弯矩', value: 'moment' },
      { label: '弯矩百分比', value: 'moment_percent' },
      { label: '荷载百分比', value: 'load_percent' },
      { label: '风速', value: 'wind_speed' },
      { label: '振幅', value: 'amplitude' },
      { label: '起升高度', value: 'height' }
    ]
  },
  {
    label: '运行状态类',
    options: [
      { label: '乘客人数', value: 'passenger_count' },
      { label: '速度', value: 'speed' },
      { label: '门联锁', value: 'door_interlock' }
    ]
  }
]
const monitorTypeLabels = Object.fromEntries(monitorTypeGroups.flatMap(g => g.options.map(o => [o.value, o.label])))
const statusLabels = { 0: '停止', 1: '运行', 2: '故障' }

// 2026-08-18 新增：传感器类型按父级归属过滤（基于项目 SensorRealtime TYPE_META 权威分类）：
// env-环境监测点 / tower-塔吊 / lift-升降机；父级选定时，"传感器类型"下拉只展示对应归属的类型。
const SENSOR_TYPE_OWNERS = {
  env: ['TEMP', 'HUMIDITY', 'PM25', 'PM10', 'NOISE', 'WIND'],
  tower: ['load', 'moment', 'moment_percent', 'wind_speed', 'amplitude', 'height'],
  lift: ['load_percent', 'passenger_count', 'speed', 'door_interlock']
}
const allowedTypeSet = (owner) => new Set(SENSOR_TYPE_OWNERS[owner] || [])

/**
 * 新增/编辑弹窗中按父级过滤后的传感器类型分组：
 * - 父级=喷淋模块(3) → 仅 PM2.5 / PM10（2026-08-18 新增，喷淋降尘联动只关心这两类颗粒物）
 * - 父级=环境监测点(2) → 仅环境监测类
 * - 父级=设备(1) → 一律不展示环境监测类（2026-08-18 按用户要求修改）：
 *     已选塔吊 → 仅塔吊归属类型；已选升降机 → 仅升降机归属类型；未选具体设备 → 展示除环境类外的全部类型
 * - 未选父级 / 无父级(0) → 显示全部分组（与现状一致）
 * 保留原有分组结构，空分组自动移除。
 */
const sensorTypeGroupsForForm = computed(() => {
  // 喷淋模块只挂 PM2.5/PM10 颗粒物传感器，用于喷淋降尘联动
  if (form.parentType === 3) {
    return monitorTypeGroups
      .map(g => ({ ...g, options: g.options.filter(o => o.value === 'PM25' || o.value === 'PM10') }))
      .filter(g => g.options.length)
  }
  let owner = null
  if (form.parentType === 2) {
    owner = 'env'
  } else if (form.parentType === 1 && form.parentId != null) {
    if (deviceGroupOptions.value.tower.some(d => d.id === form.parentId)) owner = 'tower'
    else if (deviceGroupOptions.value.lift.some(d => d.id === form.parentId)) owner = 'lift'
  }
  // 父级=设备(1)：过滤掉环境监测类；未确定具体设备时展示其余全部类型，确定后按塔吊/升降机归属收窄
  if (form.parentType === 1) {
    const allow = owner ? allowedTypeSet(owner) : null
    return monitorTypeGroups
      .map(g => ({
        ...g,
        options: g.options.filter(o => allow ? allow.has(o.value) : !allowedTypeSet('env').has(o.value))
      }))
      .filter(g => g.options.length)
  }
  if (!owner) return monitorTypeGroups
  const allow = allowedTypeSet(owner)
  return monitorTypeGroups
    .map(g => ({ ...g, options: g.options.filter(o => allow.has(o.value)) }))
    .filter(g => g.options.length)
})



/** 传感器父级设备允许的类型关键词：只有塔吊/升降机需要挂传感器，其余设备类型（摄像头/传感器等）一律过滤 */
const SENSOR_PARENT_DEVICE_KEYWORDS = {
  tower: ['塔'], // 塔吊类：类型名含"塔"
  lift: ['升降'] // 升降机类：类型名含"升降"
}

/** 收集类型树中符合关键词的节点ID（含子孙节点），按塔吊/升降机两类分开返回 */
function collectSensorParentTypeIds(nodes, towerIds, liftIds) {
  for (const n of nodes || []) {
    if (SENSOR_PARENT_DEVICE_KEYWORDS.tower.some(k => (n.typeName || '').includes(k))) towerIds.push(n.id)
    if (SENSOR_PARENT_DEVICE_KEYWORDS.lift.some(k => (n.typeName || '').includes(k))) liftIds.push(n.id)
    if (n.children?.length) collectSensorParentTypeIds(n.children, towerIds, liftIds)
  }
}

/** 判断设备属于哪个分组：返回 'tower' | 'lift' | null（typeId 命中类型树或 typeName 兜底），非两类返回 null 即过滤 */
function classifyDevice(d, towerIds, liftIds) {
  const name = d.typeName || ''
  if (towerIds.includes(d.typeId) || SENSOR_PARENT_DEVICE_KEYWORDS.tower.some(k => name.includes(k))) return 'tower'
  if (liftIds.includes(d.typeId) || SENSOR_PARENT_DEVICE_KEYWORDS.lift.some(k => name.includes(k))) return 'lift'
  return null
}

/** 加载设备列表（parentType=1 的父级候选），按"塔吊类/升降机类"分组，同类放在一起。
 *  修复1：原 pageSize=999 超服务端上限(100)导致报错，改为按每页 100 循环拉取全部数据。
 *  修复2：按用户确认的需求——设备侧仅保留塔吊、升降机两类（摄像头/传感器等其他类型全部过滤），环境监测点由 parentType=2 单独选择 */
async function loadDeviceOptions() {
  try {
    const list = []
    let pageNum = 1
    let total = Infinity
    while (list.length < total) {
      const res = await assetApi.getDevicePage({ pageNum, pageSize: 100 })
      const records = res.data?.records ?? res.data?.list ?? []
      total = res.data?.total ?? records.length
      list.push(...records)
      if (records.length === 0) break // 防止 total 不准确时死循环
      pageNum++
    }
    // 从类型树收集"塔吊/升降机"类型ID（含子类型），两类分开收集
    let towerIds = [], liftIds = []
    try {
      const tRes = await assetApi.getTypeTree()
      collectSensorParentTypeIds(tRes.data?.tree ?? [], towerIds, liftIds)
    } catch (_) { towerIds = []; liftIds = [] }
    // 按分组归类，同类设备放在一起
    const groups = { tower: [], lift: [] }
    for (const d of list) {
      const g = classifyDevice(d, towerIds, liftIds)
      if (g) groups[g].push(d)
    }
    deviceGroupOptions.value = groups
  } catch (_) { deviceGroupOptions.value = { tower: [], lift: [] } }
}

/** 加载环境监测点列表（parentType=2 的父级候选）。
 *  修复：原 pageSize=999 超服务端上限(100)导致报错，改为按每页 100 循环拉取全部数据 */
async function loadPointOptions() {
  try {
    const list = []
    let pageNum = 1
    let total = Infinity
    while (list.length < total) {
      const res = await pointApi.getPointPage({ pageNum, pageSize: 100 })
      const records = res.data?.records ?? res.data?.list ?? []
      total = res.data?.total ?? records.length
      list.push(...records)
      if (records.length === 0) break // 防止 total 不准确时死循环
      pageNum++
    }
    pointOptions.value = list
  } catch (_) { pointOptions.value = [] }
}

/** 2026-08-18 新增：加载位置树（parentType=3 喷淋模块的父级候选，仅返回启用区域）。
 *  与视频监控页一致，取 res.data.tree 结构，树节点字段为 locationName/id/children */
async function loadLocationTree() {
  try {
    const res = await assetApi.getLocationTree({ status: 1 })
    locationTree.value = res?.data?.tree ?? []
  } catch (_) { locationTree.value = [] }
}

const form = reactive({
  id: null, sensorName: '', parentType: 1, parentId: null, parentName: '',
  monitorType: '', unit: '',
  warningUpper: undefined, warningLower: undefined,
  alarmUpper: undefined, alarmLower: undefined
  // 2026-08-18 修改：移除 sprayThreshold（喷淋阈值）、refreshRate（刷新频率）字段
})

// 父级切换后：若已选的传感器类型不属于新父级的允许集合，自动清空，避免提交无效类型。
// 注意：必须放在 form 定义之后（watch 初始化会立即求值 getter 收集依赖，前置会触发 TDZ 报错）。
watch(() => [form.parentType, form.parentId], () => {
  const allowed = new Set(sensorTypeGroupsForForm.value.flatMap(g => g.options.map(o => o.value)))
  if (form.monitorType && !allowed.has(form.monitorType)) form.monitorType = ''
})
const rules = {
  sensorName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  parentType: [{ required: true, message: '请选择父级类型', trigger: 'change' }],
  // 父级类型为设备(1)/环境监测点(2)/喷淋模块(3)时要求必填父级ID（2026-08-18 增加 3）
  parentId: [{
    validator: (_r, v, cb) => {
      if (form.parentType === 1 || form.parentType === 2 || form.parentType === 3) {
        if (v === null || v === undefined || v === '') cb(new Error('请选择父级'))
        else cb()
      } else cb()
    },
    trigger: 'change'
  }],
  monitorType: [{ required: true, message: '请选择传感器类型', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }]
  // 2026-08-18 修改：报警上限 alarmUpper 为非必填（API 不要求必填），已移除必填校验
}

async function fetchData() {
  loading.value = true
  try {
    // 2026-08-17 新增：组装查询条件，空值不传，与设备管理页 fetchData 逻辑一致
    const params = {}
    Object.entries(query).forEach(([k, v]) => {
      if (v !== null && v !== '') params[k] = v
    })
    const res = await assetApi.getSensorPage({ ...params, pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = res.data?.records ?? res.data?.list ?? []
    total.value = res.data?.total || 0
  } catch (_) { tableData.value = [] }
  finally { loading.value = false }
}

/** 重置查询条件并回到第一页重新加载（2026-08-17 新增） */
function resetQuery() {
  Object.assign(query, { sensorName: '', monitorType: null, parentType: null, status: null })
  pageNum.value = 1
  fetchData()
}

function openDialog(row) {
  isEdit.value = !!row
  // 2026-08-18 修改：新增/编辑都预加载设备、监测点与位置树候选。
  // 编辑时父级固定且只读，仍需按父级过滤"传感器类型"下拉，故也要拿到塔吊/升降机分组数据
  loadDeviceOptions(); loadPointOptions(); loadLocationTree()
  if (isEdit.value) {
    // 编辑：回显父级信息用于只读展示；父级关系不可修改
    Object.assign(form, {
      id: row.id, sensorName: row.sensorName,
      parentType: row.parentType ?? 1, parentId: row.parentId ?? null, parentName: row.parentName || '',
      monitorType: row.monitorType, unit: row.unit,
      warningUpper: row.warningUpper, warningLower: row.warningLower,
      alarmUpper: row.alarmUpper, alarmLower: row.alarmLower
    })
  } else {
    // 新增：默认挂到设备下
    Object.assign(form, {
      id: null, sensorName: '', parentType: 1, parentId: null, parentName: '',
      monitorType: '', unit: '',
      warningUpper: undefined, warningLower: undefined,
      alarmUpper: undefined, alarmLower: undefined
    })
  }
  dialogVisible.value = true
}

function resetForm() { formRef.value?.resetFields() }

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (isEdit.value) {
      // 编辑：仅提交可修改字段（id/sensorName/monitorType/unit/阈值/喷淋/刷新频率）
      // 按 API 文档：parentId/parentType/status 均不在修改请求中
      const data = {
        id: form.id,
        sensorName: form.sensorName,
        monitorType: form.monitorType,
        unit: form.unit,
        warningUpper: form.warningUpper,
        warningLower: form.warningLower,
        alarmUpper: form.alarmUpper,
        alarmLower: form.alarmLower
        // 2026-08-18 修改：提交移除 sprayThreshold、refreshRate（表单已删除对应字段）
      }
      await assetApi.updateSensor(data)
    } else {
      // 新增：parentType 必填；parentType=0 时不传 parentId
      const data = {
        sensorName: form.sensorName,
        parentType: form.parentType,
        monitorType: form.monitorType,
        unit: form.unit,
        warningUpper: form.warningUpper,
        warningLower: form.warningLower,
        alarmUpper: form.alarmUpper,
        alarmLower: form.alarmLower
        // 2026-08-18 修改：提交移除 sprayThreshold、refreshRate（表单已删除对应字段）
      }
      if (form.parentType !== 0) data.parentId = form.parentId
      await assetApi.createSensor(data)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false; fetchData()
  } catch (_) { /* 错误信息由 request 拦截器统一提示 */ }
  finally { submitLoading.value = false }
}

/** 修改运行状态：1-运行，0-停止，2-故障（独立接口 PUT /api/asset/sensor/{id}/status） */
async function handleStatusChange(row, status) {
  try {
    await assetApi.updateSensorStatus(row.id, Number(status))
    ElMessage.success('状态修改成功')
    fetchData()
  } catch (_) { /* ignore */ }
}

async function handleDelete(row) {
  try { await assetApi.deleteSensorBatch([row.id]); ElMessage.success('删除成功'); fetchData() } catch (_) { /* ignore */ }
}

onMounted(() => fetchData())
</script>

<style scoped>.page-container { padding: 0; }</style>
