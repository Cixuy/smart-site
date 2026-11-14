import request from '@/utils/request'

/**
 * 环境监测 API
 * 2026-08-15 按《环境监测 API 文档》整体改造：
 * 1. 监测点接口由 /monitor-points 改为 /points、/points/page（分页）；
 * 2. 实时/历史接口去掉 realtime 前缀（/latest、/history）；
 * 3. 新增告警列表、日统计报表、趋势图、数据上报接口；
 * 4. 阈值接口路径不变，但请求/返回模型改为传感器维度（sensorId + warningUpper/Lower + alarmUpper/Lower + sprayThreshold）。
 * 所有接口统一返回 Result<T>（code/msg/data）。
 */
export default {
  // ==================== 监测点管理 ====================
  /** 分页查询环境监测点 GET /api/env-monitor/points/page */
  getMonitorPoints(params) {
    return request({ url: '/api/env-monitor/points/page', method: 'get', params })
  },

  /** 查询环境监测点详情 GET /api/env-monitor/points/{id} */
  getMonitorPointById(id) {
    return request({ url: `/api/env-monitor/points/${id}`, method: 'get' })
  },

  /** 新增环境监测点 POST /api/env-monitor/points */
  createMonitorPoint(data) {
    return request({ url: '/api/env-monitor/points', method: 'post', data })
  },

  /** 修改环境监测点 PUT /api/env-monitor/points/{id} */
  updateMonitorPoint(id, data) {
    return request({ url: `/api/env-monitor/points/${id}`, method: 'put', data })
  },

  /** 删除环境监测点 DELETE /api/env-monitor/points/{id} */
  deleteMonitorPoint(id) {
    return request({ url: `/api/env-monitor/points/${id}`, method: 'delete' })
  },

  // ==================== 环境实时/历史数据 ====================
  /** 查询最新环境监测数据 GET /api/env-monitor/latest（页面实时看板建议不超过5秒刷新一次） */
  getLatest(params) {
    return request({ url: '/api/env-monitor/latest', method: 'get', params })
  },

  /**
   * 查询环境监测历史数据（趋势图）GET /api/env-monitor/history
   * 2026-08-15 修正：后端并无 7 天窗口限制，仅要求 startTime/endTime 非空且结束不早于开始；
   * 结果按 collectTime 升序返回（分页）。页面侧仅限制结束时间不超过当前时间。
   */
  getHistory(params) {
    return request({ url: '/api/env-monitor/history', method: 'get', params })
  },

  /** 查询环境告警列表 GET /api/env-monitor/alarms（status: 1-预警, 2-报警） */
  getAlarms(params) {
    return request({ url: '/api/env-monitor/alarms', method: 'get', params })
  },

  // ==================== 环境日统计报表 ====================
  /** 查询环境日统计报表 GET /api/env-monitor/daily-stat（每日最大/最小/平均/告警次数） */
  getDailyStat(params) {
    return request({ url: '/api/env-monitor/daily-stat', method: 'get', params })
  },

  /** 获取环境趋势图数据（按日汇总）GET /api/env-monitor/daily-stat/trend */
  getDailyTrend(params) {
    return request({ url: '/api/env-monitor/daily-stat/trend', method: 'get', params })
  },

  // ==================== 数据上报 ====================
  /** 上报单条环境监测数据 POST /api/env-monitor/data */
  reportData(data) {
    return request({ url: '/api/env-monitor/data', method: 'post', data })
  },

  /** 批量上报环境监测数据 POST /api/env-monitor/data/batch（单次最多500条） */
  batchReportData(data) {
    return request({ url: '/api/env-monitor/data/batch', method: 'post', data })
  },

  /** 手动补录环境监测数据 POST /api/env-monitor/data/manual */
  manualReportData(data) {
    return request({ url: '/api/env-monitor/data/manual', method: 'post', data })
  },

  // ==================== 阈值配置 ====================
  /** 查询监测点下传感器阈值 GET /api/env-monitor/thresholds */
  getThresholds(params) {
    return request({ url: '/api/env-monitor/thresholds', method: 'get', params })
  },

  /** 修改传感器阈值配置 PUT /api/env-monitor/thresholds */
  updateThresholds(data) {
    return request({ url: '/api/env-monitor/thresholds', method: 'put', data })
  }
}
