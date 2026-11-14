import request from '@/utils/request'

export default {
  /** 分页查询 AI 告警列表 */
  getAlarms(params) {
    return request({ url: '/api/ai/alarms', method: 'get', params })
  },

  /** 获取 AI 告警详情 */
  getAlarmById(id) {
    return request({ url: `/api/ai/alarms/${id}`, method: 'get' })
  },

  /** 查询 AI 推理日志 */
  getInferenceLogs(params, config = {}) {
    return request({ url: '/api/ai/inference-logs', method: 'get', params, ...config })
  }
}
