<template>
  <div class="page-container space-y-4">
    <!-- 2026-08-15 修改：无设备选择器时移除 flex-col items-start——该组合与 items-center 冲突，
          Tailwind 中实际生效的是 items-center，导致标题水平居中；改为纯 flex items-center（主轴默认靠左），
          与有设备选择器的其他阈值页面左侧标题样式保持一致 -->
    <div :class="showDeviceSelect ? 'flex items-center justify-between' : 'flex items-center'">
      <!-- 2026-08-14 新增：backPath 非空时显示返回上一级按钮（子页面跳回主页面） -->
      <div class="flex items-center gap-3">
        <el-button v-if="backPath" :icon="ArrowLeft" circle text @click="router.push(backPath)" />
        <div>
          <h2 class="text-xl font-bold text-gray-800">{{ title }}</h2>
          <p class="text-sm text-gray-400 mt-1">{{ description }}</p>
        </div>
      </div>
      <div v-if="showDeviceSelect" class="flex items-center gap-3">
        <el-select v-model="deviceId" :placeholder="devicePlaceholder" style="width:200px" @change="onDeviceChange">
          <el-option v-for="d in deviceList" :key="getDeviceValue(d)" :label="getDeviceLabel(d)" :value="getDeviceValue(d)" />
        </el-select>
      </div>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <!-- 2026-08-14 新增：工具条。编辑按钮在表格行上，当表为空（后端无阈值记录）时没有任何操作入口，
           形成“没数据→没按钮→写不了数据”的死循环，故提供独立的「新增阈值」入口（showAdd 开启才显示） -->
      <div v-if="showAdd" class="flex items-center justify-between mb-3">
        <span class="text-sm text-gray-400">共 {{ tableData.length }} 条</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openAdd">新增阈值</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border style="width:100%">
        <!-- 动态表格列 -->
        <template v-for="col in columns" :key="col.prop">
          <el-table-column
            v-if="col.type !== 'tag'"
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
          />
          <el-table-column v-else :label="col.label" :width="col.width" :align="col.align || 'center'">
            <template #default="{ row }">
              <el-tag :type="row[col.prop] ? 'success' : 'info'" size="small">
                {{ row[col.prop] ? (col.trueText || '是') : (col.falseText || '否') }}
              </el-tag>
            </template>
          </el-table-column>
        </template>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 额外内容插槽（如联动可视化） -->
    <slot name="extra" :data="tableData" :deviceId="deviceId" :refresh="fetchData" />

    <!-- 动态编辑/新增弹窗 -->
    <el-dialog v-model="dialogVisible" :title="`${isAddMode ? '新增' : '编辑'}${metricLabel || '阈值'}`" width="480px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <template v-for="field in formFields" :key="field.prop">
          <!-- 禁用输入框（新增模式改为下拉选择：环境监测选“传感器”，塔吊/升降机选“指标类型”） -->
          <el-form-item
            v-if="field.type === 'input' && field.disabled"
            :label="field.label"
          >
            <!-- 2026-08-15 新增：环境监测维度——新增模式下“传感器”字段渲染为下拉，
                 选中后联动同步 sensorId/指标类型/单位（提交体以 sensorId 关联阈值行） -->
            <el-select
              v-if="isAddMode && field.addType === 'sensor' && sensorOptions.length"
              v-model="form.sensorName"
              placeholder="请选择传感器"
              style="width:100%"
              @change="onSensorChange"
            >
              <el-option v-for="s in sensorOptions" :key="s.sensorId" :label="s.sensorName" :value="s.sensorName" />
            </el-select>
            <!-- 2026-08-15 修改：指标类型下拉仅渲染在第一个禁用字段处（field.prop === firstDisabledProp），
                 避免有多个禁用字段的页面（如环境监测的 传感器/指标/单位）重复渲染多个下拉 -->
            <el-select
              v-else-if="isAddMode && !field.addType && metricOptions.length && field.prop === firstDisabledProp"
              v-model="form.metricType"
              placeholder="请选择指标类型"
              style="width:100%"
              @change="onMetricChange"
            >
              <el-option v-for="opt in metricOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-input v-else :model-value="form[field.prop]" disabled />
          </el-form-item>

          <!-- 联动开关 -->
          <el-form-item
            v-else-if="field.type === 'switch'"
            :label="field.label"
          >
            <el-switch v-model="form[field.prop]" />
          </el-form-item>

          <!-- 数字输入（带单位后缀） -->
          <el-form-item
            v-else
            :label="field.label"
            :prop="field.prop"
            v-show="!field.showIf || form[field.showIf.prop] === field.showIf.value"
          >
            <el-input v-model.number="form[field.prop]" :placeholder="field.placeholder || '请输入'">
              <template v-if="field.unitProp && form[field.unitProp]" #append>
                {{ form[field.unitProp] }}
              </template>
            </el-input>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Plus, ArrowLeft } from '@element-plus/icons-vue'

const router = useRouter()

// 2026-08-15 新增：设备/监测点切换事件（父组件据此联动刷新“新增阈值”的传感器下拉等）
const emit = defineEmits(['device-change'])

const props = defineProps({
  /** 页面标题 */
  title: { type: String, required: true },
  /** 描述文字 */
  description: { type: String, default: '' },
  /** API 对象 { getThresholds, updateThresholds } */
  api: { type: Object, required: true },
  /** 表格列定义 [{ prop, label, width?, minWidth?, align?, type?, trueText?, falseText? }] */
  columns: { type: Array, required: true },
  /** 表单字段定义 [{ prop, label, type:'input'|'number'|'switch', required?, disabled?, placeholder?, unitProp?, showIf? }] */
  formFields: { type: Array, required: true },
  /** API 返回数据的路径映射函数，从 response 提取 rows 数组 */
  responseMapper: { type: Function, default: (res) => res?.data?.list || res?.data?.rows || res?.data || [] },
  /** API 请求参数的构建函数 (row) => requestBody */
  submitMapper: { type: Function, default: (form) => ({ ...form }) },
  /** 是否显示设备选择器 */
  showDeviceSelect: { type: Boolean, default: false },
  /** 设备列表 */
  deviceList: { type: Array, default: () => [] },
  /** 默认设备 */
  defaultDevice: { type: String, default: '' },
  /** 设备选择器占位文字 */
  devicePlaceholder: { type: String, default: '选择设备' },
  /** 2026-08-15 新增：设备选择器变化后传给查询接口的参数名。
   *  塔吊/升降机为 deviceId，环境监测按监测点筛选为 monitorPointId */
  queryParam: { type: String, default: 'deviceId' },
  /** 2026-08-14 新增：返回上一级的路由路径，非空时标题旁显示返回按钮（如 '/monitor/towerCrane'） */
  backPath: { type: String, default: '' },
  /** 2026-08-14 新增：是否显示「新增阈值」按钮（表格无数据时的写入入口，默认关闭） */
  showAdd: { type: Boolean, default: false },
  /** 2026-08-14 新增：可新增的指标类型 [{ value: metricType(大写), label, unit }] */
  metricOptions: { type: Array, default: () => [] },
  /** 2026-08-15 新增：可新增的传感器列表（环境监测按传感器维度配置阈值），
   *  格式 [{ sensorId, sensorName, metricType, metricTypeText, unit }]，
   *  非空时新增弹窗优先渲染“传感器”下拉（选中后联动指标/单位），塔吊/升降机传空数组不受影响 */
  sensorOptions: { type: Array, default: () => [] }
})

// 生命周期守卫
let isAlive = true
onBeforeUnmount(() => { isAlive = false })

// 设备选择
const deviceId = ref(props.defaultDevice)

// 修复：父组件（如塔吊阈值页）的设备列表是异步加载的，组件挂载时 props.defaultDevice 可能还是空串，
// 这里监听 defaultDevice 回填，自动选中默认设备并重新拉取阈值数据，否则下拉框为空且表格无法加载
watch(
  () => props.defaultDevice,
  (val) => {
    if (val && !deviceId.value) {
      deviceId.value = val
      if (isAlive) fetchData()
    }
  }
)

// 兼容 deviceId / deviceCode 两种设备字段
function getDeviceValue(d) {
  if (d && typeof d === 'object') return d.deviceId ?? d.deviceCode ?? d.id ?? ''
  return d ?? ''
}

function getDeviceLabel(d) {
  if (d && typeof d === 'object') return d.deviceName ?? d.name ?? getDeviceValue(d)
  return d ?? ''
}
const loading = ref(false)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const tableData = ref([])
// 2026-08-14 新增：是否为“新增”模式（新增时指标名称可从下拉选择，编辑时只读）
const isAddMode = ref(false)

// 动态 form 数据
const form = reactive({})

// 当前编辑指标名称（用于弹窗标题）
const metricLabel = computed(() => {
  const nameField = props.formFields.find(f => f.disabled)
  return nameField ? form[nameField.prop] : ''
})

// 2026-08-15 新增：第一个禁用输入字段的 prop。
// 新增模式下指标类型下拉只渲染在该字段处（原逻辑对每个禁用字段都渲染，页面有多个禁用字段时会出现重复下拉）
const firstDisabledProp = computed(() => {
  const f = props.formFields.find(f => f.type === 'input' && f.disabled)
  return f?.prop || ''
})

// 动态生成校验规则
const formRules = computed(() => {
  const rules = {}

  // 2026-08-14 修改：自动“预警<告警”首尾比较仅作用于未声明 autoCompare:false 的字段。
  // 塔吊/升降机阈值页是预警/告警各自上下限四字段结构，上下限大小关系由字段级 compare 显式声明，
  // 此处退出自动首尾比较，避免把“预警上限”与“告警下限”错误地做大小比较；环境监测页行为不变。
  const warnField = props.formFields.find(f => f.required && !f.disabled && f.type !== 'switch' && f.autoCompare !== false)
  const alarmField = props.formFields.slice().reverse().find(f => f.required && !f.disabled && f.type !== 'switch' && f.autoCompare !== false)
  const warnProp = warnField?.prop
  const alarmProp = alarmField?.prop

  props.formFields.forEach(field => {
    if (field.disabled || field.type === 'switch') return

    const fieldRules = []

    if (field.required) {
      fieldRules.push({ required: true, message: `请输入${field.label}`, trigger: 'blur' })
    }

    fieldRules.push({ type: 'number', min: 0, message: `${field.label}必须为非负数`, trigger: 'blur' })

    // 预警/告警大小校验
    if (field.prop === warnProp && alarmProp) {
      fieldRules.push({
        validator: (_r, _v, cb) => {
          const w = Number(form[warnProp])
          const a = Number(form[alarmProp])
          if (w !== '' && a !== '' && w >= a) cb(new Error('预警阈值必须小于告警阈值'))
          else cb()
        },
        trigger: 'blur'
      })
    }
    if (field.prop === alarmProp && warnProp) {
      fieldRules.push({
        validator: (_r, _v, cb) => {
          const w = Number(form[warnProp])
          const a = Number(form[alarmProp])
          if (w !== '' && a !== '' && a <= w) cb(new Error('告警阈值必须大于预警阈值'))
          else cb()
        },
        trigger: 'blur'
      })
    }

    // 2026-08-14 新增：显式字段比较校验（用于“预警下限 <= 预警上限”“告警下限 <= 告警上限”等），
    // 通过字段配置声明：compare: { prop: 对比字段, type: 'gte'|'gt'|'lte'|'lt', message: 错误提示 }
    if (field.compare) {
      const cmp = field.compare
      fieldRules.push({
        validator: (_r, value, cb) => {
          // 任一侧为空时不比较（必填校验另行负责），避免用户未填完就报错
          if (value === '' || value === null || value === undefined
              || form[cmp.prop] === '' || form[cmp.prop] === null || form[cmp.prop] === undefined) {
            cb()
            return
          }
          const self = Number(value)
          const other = Number(form[cmp.prop])
          const ok = cmp.type === 'gte' ? self >= other
            : cmp.type === 'gt' ? self > other
            : cmp.type === 'lte' ? self <= other
            : self < other
          ok ? cb() : cb(new Error(cmp.message || `${field.label}取值不合法`))
        },
        trigger: 'blur'
      })
    }

    rules[field.prop] = fieldRules
  })

  return rules
})

// 初始化 form 并获取数据
function initForm() {
  const initial = {}
  props.formFields.forEach(f => {
    initial[f.prop] = f.type === 'switch' ? false : (f.disabled ? '' : '')
  })
  Object.assign(form, initial)
}

// 2026-08-15 新增：设备/监测点下拉切换——先通知父组件联动（刷新传感器下拉），再重新拉取阈值表格
function onDeviceChange(val) {
  emit('device-change', val)
  fetchData()
}

async function fetchData() {
  if (!isAlive) return
  loading.value = true
  try {
    // 2026-08-15 修改：参数名由 queryParam 决定（塔吊/升降机 deviceId，环境监测 monitorPointId）；
    // 为空时不携带该参数（后端返回全部阈值），避免空串参数导致后端校验失败
    const params = props.showDeviceSelect && deviceId.value ? { [props.queryParam]: deviceId.value } : {}
    const res = await props.api.getThresholds(params)
    if (!isAlive) return
    tableData.value = Array.isArray(res?.data) ? res.data : props.responseMapper(res)
  } catch (_) {
    if (isAlive) tableData.value = []
  } finally {
    if (isAlive) loading.value = false
  }
}

function openEdit(row, isAdd = false) {
  // 2026-08-14 修复：新增模式由参数显式传入，不再无条件重置为 false。
  // 此前 openAdd() 先置 isAddMode=true 再调用 openEdit()，而 openEdit 第一行
  // 把它重置回 false，导致弹窗内指标名称始终渲染为禁用的输入框（只能看到“起重量”），
  // 无法通过下拉选择其余 6 类指标，看起来就像“固定死只有起重量”。
  isAddMode.value = isAdd
  props.formFields.forEach(f => {
    if (f.type === 'switch') {
      form[f.prop] = row[f.prop] ?? false
    } else {
      form[f.prop] = row[f.prop] ?? (f.disabled ? '' : '')
    }
  })
  // 附加未在 formFields 中声明的字段
  Object.keys(row).forEach(k => {
    if (!(k in form)) form[k] = row[k]
  })
  dialogVisible.value = true
}

// 2026-08-14 新增：打开“新增阈值”弹窗（表格无数据时也能写入阈值配置）
function openAdd() {
  // 有设备选择器时，必须先选设备（后端以 parent_id 关联设备台账，deviceId 为空会插入失败）
  if (props.showDeviceSelect && !deviceId.value) {
    ElMessage.warning('请先选择设备')
    return
  }
  // 2026-08-15 新增：环境监测按传感器维度新增——优先使用传感器列表，
  // 默认选中第一个传感器并联动带出 sensorId/指标类型/单位（提交体以 sensorId 关联）
  if (props.sensorOptions.length) {
    const first = props.sensorOptions[0]
    const row = {
      sensorId: first.sensorId,
      sensorName: first.sensorName,
      metricType: first.metricType,
      metricTypeText: first.metricTypeText || first.metricType,
      unit: first.unit ?? ''
    }
    openEdit(row, true)
    return
  }
  // 2026-08-15 修改：文案更通用（环境监测走传感器维度，塔吊/升降机走指标类型维度）
  if (!props.metricOptions.length) {
    ElMessage.warning('暂无可新增的传感器/指标类型，请先在设备资产中配置')
    return
  }
  // 默认选中第一个指标类型，指标名/单位随选择联动，与后端 METRIC_TYPE_TEXTS/DEFAULT_UNITS 对齐
  const first = props.metricOptions[0]
  const row = {
    metricType: first.value,
    metricTypeText: first.label,
    unit: first.unit ?? '',
    deviceId: deviceId.value
  }
  // 2026-08-14 修复：显式传入 isAdd=true，让弹窗内指标名称渲染为下拉框（可选全部指标类型），
  // 而不是被 openEdit 重置后变成禁用的输入框
  openEdit(row, true)
}

// 2026-08-14 新增：切换新增的指标类型时，同步指标名称文本与单位
function onMetricChange(val) {
  const opt = props.metricOptions.find(o => o.value === val)
  form.metricTypeText = opt?.label ?? val
  form.unit = opt?.unit ?? ''
}

// 2026-08-15 新增：切换新增的传感器时，同步传感器ID（int64 直传、禁止 Number 强转）、指标类型文本与单位
function onSensorChange(val) {
  const s = props.sensorOptions.find(o => o.sensorName === val)
  if (!s) return
  form.sensorId = s.sensorId
  form.metricType = s.metricType
  form.metricTypeText = s.metricTypeText || s.metricType
  form.unit = s.unit ?? ''
}

function resetForm() {
  initForm()
  formRef.value?.resetFields()
}

async function handleSubmit() {
  if (!isAlive) return
  // 2026-08-14 新增：新增模式下 deviceId 必须存在（后端按设备写入阈值行）。
  // 2026-08-15 修改：环境监测为传感器维度新增（sensorOptions 非空），提交体以 sensorId 关联，
  // 无需 deviceId，故跳过该校验（顶部 openAdd 已校验“已选择监测点”）
  if (isAddMode.value && props.showDeviceSelect && !props.sensorOptions.length && !form.deviceId) {
    ElMessage.warning('请先选择设备')
    return
  }
  // 2026-08-15 新增：环境监测无设备选择器，新增模式下必须已选传感器（提交体以 sensorId 关联）
  if (isAddMode.value && !props.showDeviceSelect && props.sensorOptions.length && !form.sensorId) {
    ElMessage.warning('请选择传感器')
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await props.api.updateThresholds(props.submitMapper(form))
    if (!isAlive) return
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (_) { /* ignore */ }
  finally {
    if (isAlive) submitLoading.value = false
  }
}

onMounted(() => {
  initForm()
  if (isAlive) fetchData()
})

defineExpose({ fetchData })
</script>

<style scoped>
.page-container { padding: 0; }
</style>
