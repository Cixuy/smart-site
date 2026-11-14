/**
 * 告警管理 - 字典常量
 * 与后端 DictData.java / 需求文档保持一致
 */

/** 告警来源 1-设备监测 2-环境监测 3-AI识别 */
export const ALARM_SOURCE = {
  1: { label: '设备监测', tag: 'warning' },
  2: { label: '环境监测', tag: 'success' },
  3: { label: 'AI识别', tag: 'info' }
}

/** 告警级别 1-预警 2-警报 3-控制 */
export const ALARM_LEVEL = {
  1: { label: '预警', tag: 'warning' },
  2: { label: '警报', tag: 'warning' },
  3: { label: '控制', tag: 'danger' }
}

/** 处置状态 0-未处置 1-处置中 2-已处置 */
export const ALARM_STATUS = {
  0: { label: '未处置', tag: 'danger' },
  1: { label: '处置中', tag: 'warning' },
  2: { label: '已处置', tag: 'success' }
}

/** 处置结论 1-属实 2-误报 3-其他 */
export const DISPOSAL_CONCLUSION = {
  1: { label: '属实', tag: 'danger' },
  2: { label: '误报', tag: 'warning' },
  3: { label: '其他', tag: 'info' }
}

/** 推送渠道 1-短信 2-企业微信 3-钉钉 4-邮件 */
export const ALARM_PUSH_CHANNEL = {
  1: { label: '短信', tag: 'primary' },
  2: { label: '企业微信', tag: 'success' },
  3: { label: '钉钉', tag: 'info' },
  4: { label: '邮件', tag: 'warning' }
}

/** 推送状态 0-待推送 1-推送成功 2-推送失败 */
export const ALARM_PUSH_STATUS = {
  0: { label: '待推送', tag: 'info' },
  1: { label: '推送成功', tag: 'success' },
  2: { label: '推送失败', tag: 'danger' }
}

/** 告警趋势统计粒度 DAY-日 WEEK-周 MONTH-月 */
export const TREND_GRANULARITY = {
  DAY: { label: '按日' },
  WEEK: { label: '按周' },
  MONTH: { label: '按月' }
}

/** 告警分布维度 ALARM_TYPE-告警类型 DEVICE_TYPE-设备类型 */
export const DISTRIBUTION_DIMENSION = {
  ALARM_TYPE: { label: '按告警类型' },
  DEVICE_TYPE: { label: '按设备类型' }
}

/** 报表导出格式 XLSX / CSV */
export const EXPORT_FORMAT = {
  XLSX: { label: 'Excel (XLSX)' },
  CSV: { label: 'CSV' }
}

/** 告警类型编码（按来源分组，含描述） */
export const ALARM_TYPE_GROUPS = [
  {
    source: 1,
    label: '设备监测',
    options: [
      { code: 'OVERLOAD', desc: '超载（升降机）' },
      { code: 'MOMENT_EXCEED', desc: '力矩超标（塔吊）' },
      { code: 'WIND_EXCEED', desc: '风速超标' },
      { code: 'LOAD_EXCEED', desc: '吊重超标（塔吊）' },
      { code: 'DOOR_OPEN', desc: '双门同开（升降机联锁）' }
    ]
  },
  {
    source: 2,
    label: '环境监测',
    options: [
      { code: 'PM25_EXCEED', desc: 'PM2.5超标' },
      { code: 'PM10_EXCEED', desc: 'PM10超标' },
      { code: 'NOISE_EXCEED', desc: '噪声超标' }
    ]
  },
  {
    source: 3,
    label: 'AI识别',
    options: [
      { code: 'NO_HELMET', desc: '安全帽未佩戴' },
      { code: 'NO_VEST', desc: '安全服未穿' },
      { code: 'SMOKING', desc: '现场吸烟' },
      { code: 'FIRE', desc: '现场明火' }
    ]
  }
]

/** 取告警类型描述（按编码） */
export function getAlarmTypeDesc(code) {
  if (!code) return ''
  const hit = ALARM_TYPE_GROUPS.flatMap(g => g.options).find(o => o.code === code)
  return hit ? hit.desc : code
}

/** 取字典 Meta（含标签颜色） */
export function dictMeta(dict, value) {
  const key = value === null || value === undefined || value === '' ? null : Number(value)
  return dict[key] || { label: '-', tag: 'info' }
}
