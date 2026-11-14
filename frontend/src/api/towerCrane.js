import request from '@/utils/request'

export default {
  /** 查询塔吊最新实时数据 */
  getLatest(params) {
    return request({ url: '/api/tower-crane/realtime/latest', method: 'get', params })
  },

  /** 查询塔吊历史数据（时序曲线） */
  getHistory(params) {
    return request({ url: '/api/tower-crane/realtime/history', method: 'get', params })
  },

  /** 查询塔吊告警列表 */
  getAlarms(params) {
    return request({ url: '/api/tower-crane/realtime/alarms', method: 'get', params })
  },

  /** 查询塔吊各指标阈值配置 */
  getThresholds(params) {
    return request({ url: '/api/tower-crane/thresholds', method: 'get', params })
  },

  /** 修改塔吊阈值配置 */
  updateThresholds(data) {
    return request({ url: '/api/tower-crane/thresholds', method: 'put', data })
  },

  /** 查询塔吊作业记录列表 */
  getWorkRecords(params) {
    return request({ url: '/api/tower-crane/work-records', method: 'get', params })
  },

  /** 获取塔吊作业统计汇总 */
  getWorkStatistics(params) {
    return request({ url: '/api/tower-crane/work-records/statistics', method: 'get', params })
  }
}
