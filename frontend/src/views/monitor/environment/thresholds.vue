<template>
  <!-- 2026-08-15 修改：新增阈值传感器下拉随右上角监测点联动（@device-change 时刷新）；
       监测点列表经 deviceList 传入，右上角显示“选择监测点”下拉框（showDeviceSelect:true + queryParam:monitorPointId） -->
  <ThresholdConfig
    v-bind="config"
    ref="thresholdRef"
    :device-list="deviceList"
    :default-device="defaultDevice"
    :sensor-options="sensorList"
    @device-change="onPointChange"
  />
</template>

<script setup>
import { ref, onMounted, markRaw } from 'vue'
import { environmentApi } from '@/api'
import ThresholdConfig from '../components/ThresholdConfig.vue'

const thresholdRef = ref(null)

// 2026-08-15 按《环境监测 API 文档》改造：metricType 枚举与中文名映射
const METRIC_TEXT = { PM25: 'PM2.5', PM10: 'PM10', NOISE: '噪声', TEMP: '温度', HUMIDITY: '湿度', WIND: '风速' }

// ==================== 监测点下拉（右上角） ====================
// 2026-08-15 新增：监测点列表与默认监测点。
// 复用主页面逻辑：/api/env-monitor/points/page 返回 data.list（元素含 monitorPointId/pointName，
// 同一监测点可能因多个指标返回多条记录），按 monitorPointId 去重；
// 转成组件要求的 { deviceId, deviceName } 结构后传给 ThresholdConfig
const deviceList = ref([])
const defaultDevice = ref('')

async function loadPoints() {
  try {
    // 2026-08-15 修改：pageSize 保持 100（后端 points/page 接口限制 pageSize 最大 100，超过报 arg1 校验错误）
    const res = await environmentApi.getMonitorPoints({ pageNum: 1, pageSize: 100 })
    // 兼容 list / records 两种容器，优先按实际返回的 list 解析
    const list = Array.isArray(res.data?.list)
      ? res.data.list
      : (Array.isArray(res.data?.records) ? res.data.records : [])
    const map = new Map()
    list.forEach(p => {
      // monitorPointId 为 int64，禁止 Number() 强转
      const id = p.monitorPointId ?? p.id
      if (id == null || map.has(id)) return
      map.set(id, { deviceId: id, deviceName: p.pointName || p.name || `监测点${id}` })
    })
    const points = [...map.values()]
    deviceList.value = points
    if (points.length) {
      // 默认选中第一个监测点；组件内部 watch(defaultDevice) 会自动回填并拉取该监测点阈值
      defaultDevice.value = points[0].deviceId
      // 传感器下拉与默认监测点保持一致（供“新增阈值”弹窗选择该监测点下的传感器）
      loadSensorList(points[0].deviceId)
    }
  } catch (_) { /* 接口异常时保留空列表，页面显示空提示 */ }
}

// 2026-08-15 新增：用户切换右上角监测点时，联动刷新“新增阈值”的传感器下拉
function onPointChange(id) {
  loadSensorList(id)
}

// ==================== 新增阈值的传感器下拉 ====================
// 2026-08-15 新增：可新增的传感器列表，供“新增阈值”弹窗下拉选择。
// 按 API 文档：GET /api/env-monitor/thresholds 传 monitorPointId 时返回该监测点下传感器
// （asset_device_sensor.parentType=2，阈值字段可能为 null）。
// 2026-08-15 修复：若该监测点阈值表为空（尚无任何阈值记录），按监测点查询会返回空数组，
// 导致“新增阈值”误报“暂无可新增的指标类型”。故在按监测点查不到时退化为不传参数，
// 查询“全部环境传感器”（文档：不传 monitorPointId 则查询全部环境传感器）——即使阈值表为空，
// 只要后端存在 parentType=2 的环境传感器即可正常新增写入（PUT 提交体只依赖 sensorId）。
const sensorList = ref([])

// 2026-08-15 新增：向 Map 写入一条传感器（sensorId 为 int64，用 String 去重、禁止 Number() 强转）
function pushSensor(map, { sensorId, sensorName, metricType, unit }) {
  if (sensorId == null) return
  const key = String(sensorId)
  if (!map.has(key)) {
    map.set(key, {
      sensorId,
      sensorName: sensorName || `传感器${sensorId}`,
      metricType,
      metricTypeText: METRIC_TEXT[metricType] || metricType || '--',
      unit: unit ?? ''
    })
  }
}

// 2026-08-15 修改：按监测点查询传感器（monitorPointId 直传原始值，避免 int64 丢精度）；
// 查不到时退化为查全部环境传感器，保证阈值表为空时也能打开新增弹窗
async function loadSensorList(monitorPointId) {
  try {
    const extract = (res) =>
      Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
    let records = []
    // 1) 优先按当前监测点精准查询
    if (monitorPointId != null) {
      const res = await environmentApi.getThresholds({ monitorPointId })
      records = extract(res)
    }
    // 2) 该监测点阈值表为空时，退化查全部环境传感器（阈值字段可为 null）
    if (!records.length) {
      const res = await environmentApi.getThresholds({})
      records = extract(res)
    }
    const map = new Map()
    // 按文档返回字段 sensorId 提取；保留 r.id 兼容以防后端字段命名差异
    records.forEach(r => pushSensor(map, {
      sensorId: r.sensorId ?? r.id,
      sensorName: r.sensorName,
      metricType: r.metricType,
      unit: r.unit
    }))
    sensorList.value = [...map.values()]
  } catch (_) { /* 接口异常时保持空列表（新增入口自动隐藏） */ }
}

onMounted(() => { loadPoints() })

const config = markRaw({
  title: '环境阈值',
  description: '配置环境监测传感器预警、报警与喷淋阈值',
  // 返回上一级（环境监测实时监控主页面）
  backPath: '/monitor/environment',
  api: environmentApi,
  // 2026-08-15 新增：右上角显示“选择监测点”下拉（监测点列表/默认监测点经 props 传入）。
  // queryParam: 'monitorPointId' 让查询参数名与环境接口对齐（塔吊/升降机仍为 deviceId）
  showDeviceSelect: true,
  devicePlaceholder: '选择监测点',
  queryParam: 'monitorPointId',
  columns: [
    { prop: 'sensorName', label: '传感器', minWidth: 150 },
    { prop: 'metricTypeText', label: '指标名称', width: 100 },
    // 2026-08-15 修改：与 EnvSensorThreshold 模型对齐，预警/报警各拆为上下限两列，另加喷淋阈值列
    { prop: 'warningUpper', label: '预警上限', width: 110 },
    { prop: 'warningLower', label: '预警下限', width: 110 },
    { prop: 'alarmUpper', label: '报警上限', width: 110 },
    { prop: 'alarmLower', label: '报警下限', width: 110 },
    { prop: 'sprayThreshold', label: '喷淋阈值', width: 110 },
    { prop: 'unit', label: '单位', width: 70 }
  ],
  formFields: [
    // 2026-08-15 修改：标记 addType:'sensor'，新增模式下该字段渲染为“传感器”下拉（联动指标/单位）
    { prop: 'sensorName', label: '传感器', type: 'input', disabled: true, addType: 'sensor' },
    { prop: 'metricTypeText', label: '指标名称', type: 'input', disabled: true },
    { prop: 'unit', label: '单位', type: 'input', disabled: true },
    // 2026-08-15 修改：预警/报警各拆为上、下限两个输入（对应 warning_upper/lower、alarm_upper/lower）。
    // autoCompare:false 退出组件自动“首尾比较”，改用 compare 显式校验“下限 <= 上限”；
    // 2026-08-15 新增：按 API 文档“预警上限必须小于报警上限”，warningUpper/alarmUpper 互相比对（lt/gt）；
    // 喷淋阈值可选（留空表示不触发喷淋），故不设 required
    { prop: 'warningUpper', label: '预警上限', type: 'number', required: true, unitProp: 'unit', autoCompare: false, compare: { prop: 'alarmUpper', type: 'lt', message: '预警上限必须小于报警上限' } },
    { prop: 'warningLower', label: '预警下限', type: 'number', required: true, unitProp: 'unit', autoCompare: false, compare: { prop: 'warningUpper', type: 'lte', message: '预警下限必须小于等于预警上限' } },
    { prop: 'alarmUpper', label: '报警上限', type: 'number', required: true, unitProp: 'unit', autoCompare: false, compare: { prop: 'warningUpper', type: 'gt', message: '预警上限必须小于报警上限' } },
    { prop: 'alarmLower', label: '报警下限', type: 'number', required: true, unitProp: 'unit', autoCompare: false, compare: { prop: 'alarmUpper', type: 'lte', message: '报警下限必须小于等于报警上限' } },
    { prop: 'sprayThreshold', label: '喷淋阈值', type: 'number', unitProp: 'unit', autoCompare: false }
  ],
  // 2026-08-15 新增：开启「新增阈值」入口（蓝色按钮，与塔吊/升降机阈值页保持一致）。
  // 新增时从 sensorList 选择传感器（传感器确定其指标类型/单位），提交体以 sensorId 关联
  showAdd: true,
  // 2026-08-15 修改：阈值接口返回分页对象 {records:[...]}（原为裸数组），并把 metricType 转为中文展示
  responseMapper: (res) => {
    const records = Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
    return records.map(r => ({
      ...r,
      metricTypeText: METRIC_TEXT[r.metricType] || r.metricType || '--'
    }))
  },
  // 2026-08-15 修改：提交体与 EnvSensorThresholdUpdateRequest 对齐（sensorId + 上下限 + 喷淋阈值）
  submitMapper: (form) => ({
    sensorId: form.sensorId,
    warningUpper: Number(form.warningUpper),
    warningLower: Number(form.warningLower),
    alarmUpper: Number(form.alarmUpper),
    alarmLower: Number(form.alarmLower),
    // 喷淋阈值留空时提交 null（表示不触发喷淋）
    sprayThreshold: form.sprayThreshold === '' || form.sprayThreshold == null ? null : Number(form.sprayThreshold)
  })
})
</script>
