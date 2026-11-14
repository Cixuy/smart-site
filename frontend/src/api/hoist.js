import request from '@/utils/request'

export default {
  /** 查询升降机最新实时数据 */
  getLatest(params) {
    return request({ url: '/api/hoist/realtime/latest', method: 'get', params })
  },

  /** 查询升降机历史数据（时序曲线） */
  getHistory(params) {
    return request({ url: '/api/hoist/realtime/history', method: 'get', params })
  },

  /** 查询升降机联锁告警列表 */
  getInterlockAlarms(params) {
    return request({ url: '/api/hoist/realtime/interlock-alarms', method: 'get', params })
  },

  /** 查询升降机各指标阈值配置 */
  getThresholds(params) {
    return request({ url: '/api/hoist/thresholds', method: 'get', params })
  },

  /** 修改升降机阈值配置 */
  updateThresholds(data) {
    return request({ url: '/api/hoist/thresholds', method: 'put', data })
  },

  /** 查询升降机作业记录列表 */
  getWorkRecords(params) {
    return request({ url: '/api/hoist/work-records', method: 'get', params })
  },

  /** 获取升降机作业统计 */
  getStatistics(params) {
    return request({ url: '/api/hoist/work-records/statistics', method: 'get', params })
  }
  // 2026-08-15 清理：文件末尾曾重复定义 getThresholds / updateThresholds（与上方同名方法完全一致，
  // 且后者会覆盖前者），已删除冗余定义，仅保留上方唯一一份。
}
