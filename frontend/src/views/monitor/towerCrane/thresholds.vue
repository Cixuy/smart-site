<template>
  <ThresholdConfig v-bind="config" :deviceList="deviceList" :defaultDevice="defaultDevice" />
</template>

<script setup>
import { ref, onMounted, markRaw } from 'vue'
// 2026-08-15 修改：设备下拉框数据源由 latest 接口（依赖实时表有数据）改为设备台账接口 assetApi.getDevicePage，
// typeId=2 为塔吊设备类型，台账已登记的设备即可出现在下拉框，不再受实时数据影响
import { towerCraneApi, assetApi } from '@/api'
import ThresholdConfig from '../components/ThresholdConfig.vue'

const deviceList = ref([])
const defaultDevice = ref('')

// 2026-08-15 修改：设备下拉框数据源由 latest 接口改为设备台账接口
// GET /api/asset/device/page?typeId=2，typeId=2 为塔吊设备类型；records[].deviceCode 即设备编码（如 TC-001）
async function loadDevices() {
  try {
    const res = await assetApi.getDevicePage({ typeId: 2, pageSize: 200 })
    const list = Array.isArray(res.data?.records) ? res.data.records : []
    deviceList.value = list.map(d => ({ deviceId: d.deviceCode, deviceName: d.deviceName || d.deviceCode }))
    if (deviceList.value.length) {
      defaultDevice.value = deviceList.value[0].deviceId
    }
  } catch (_) { /* ignore */ }
}

onMounted(() => { loadDevices() })

const config = markRaw({
  title: '塔吊阈值',
  description: '配置塔吊各指标预警与告警阈值',
  // 2026-08-14 新增：返回上一级（塔吊实时监控主页面）
  backPath: '/monitor/towerCrane',
  api: towerCraneApi,
  columns: [
    { prop: 'metricTypeText', label: '指标名称', width: 140 },
    // 2026-08-14 修改：预警/告警各拆分为上下限两列展示（与库表 warning_upper/lower、alarm_upper/lower 对应）
    { prop: 'thresholdWarnUpper', label: '预警上限', width: 110 },
    { prop: 'thresholdWarnLower', label: '预警下限', width: 110 },
    { prop: 'thresholdAlarmUpper', label: '告警上限', width: 110 },
    { prop: 'thresholdAlarmLower', label: '告警下限', width: 110 },
    { prop: 'unit', label: '单位', width: 80 }
  ],
  formFields: [
    { prop: 'metricTypeText', label: '指标名称', type: 'input', disabled: true },
    // 2026-08-14 修改：预警/告警各自拆分为上、下限两个输入（对应库表 warning_upper/lower、alarm_upper/lower）。
    // autoCompare:false 退出组件自动“首尾比较”（四字段结构不再适用），改用 compare 显式校验上下限大小关系
    { prop: 'thresholdWarnUpper', label: '预警上限', type: 'number', required: true, unitProp: 'unit', autoCompare: false },
    { prop: 'thresholdWarnLower', label: '预警下限', type: 'number', required: true, unitProp: 'unit', autoCompare: false, compare: { prop: 'thresholdWarnUpper', type: 'lte', message: '预警下限必须小于等于预警上限' } },
    { prop: 'thresholdAlarmUpper', label: '告警上限', type: 'number', required: true, unitProp: 'unit', autoCompare: false },
    { prop: 'thresholdAlarmLower', label: '告警下限', type: 'number', required: true, unitProp: 'unit', autoCompare: false, compare: { prop: 'thresholdAlarmUpper', type: 'lte', message: '告警下限必须小于等于告警上限' } }
  ],
  submitMapper: (form) => ({
    deviceId: form.deviceId,
    metricType: form.metricType,
    metricTypeText: form.metricTypeText,
    // 2026-08-14 修改：与后端 TowerCraneThresholdUpdateRequest 对齐，
    // 预警/告警各自提交上下限四字段，不再把下限写死为 0
    thresholdWarnUpper: Number(form.thresholdWarnUpper),
    thresholdWarnLower: Number(form.thresholdWarnLower),
    thresholdAlarmUpper: Number(form.thresholdAlarmUpper),
    thresholdAlarmLower: Number(form.thresholdAlarmLower),
    unit: form.unit
  }),
  showDeviceSelect: true,
  devicePlaceholder: '选择塔吊设备',
  // 2026-08-14 新增：开启「新增阈值」入口（表格无数据时的写入入口），
  // 指标文本与单位对齐后端 TowerCraneService 的 METRIC_TYPE_TEXTS / DEFAULT_UNITS
  showAdd: true,
  metricOptions: [
    { value: 'LOAD_WEIGHT', label: '起重量', unit: 'kN' },
    { value: 'MOMENT', label: '力矩', unit: 'kN·m' },
    { value: 'MOMENT_PERCENT', label: '力矩百分比', unit: '%' },
    { value: 'WIND_SPEED', label: '风速', unit: 'm/s' },
    { value: 'HEIGHT', label: '高度', unit: 'm' },
    { value: 'AMPLITUDE', label: '幅度', unit: 'm' },
    { value: 'SLEW_ANGLE', label: '回转角度', unit: '°' }
  ]
})
</script>
