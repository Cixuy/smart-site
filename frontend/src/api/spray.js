import request from '@/utils/request'

export default {
  // ==================== 喷淋操作控制 ====================
  /** 手动启动喷淋 */
  start(data) {
    return request({ url: '/api/spray/operate/start', method: 'post', data })
  },

  /** 手动停止喷淋 */
  stop(data) {
    return request({ url: '/api/spray/operate/stop', method: 'post', data })
  },

  /** 查询区域喷淋运行状态 */
  getStatus(locationId) {
    return request({ url: '/api/spray/operate/status', method: 'get', params: { locationId } })
  },

  /** 批量查询喷淋运行状态（大屏用，parentLocationId 不传则全局） */
  getBatchStatus(parentLocationId) {
    return request({
      url: '/api/spray/operate/status/batch',
      method: 'get',
      params: parentLocationId == null ? {} : { parentLocationId }
    })
  },

  /** 自动联动触发（内部接口，环境监测服务在 PM2.5/PM10 超标时调用） */
  autoTrigger(data) {
    return request({ url: '/api/spray/operate/trigger', method: 'post', data })
  },

  // ==================== 喷淋操作日志 ====================
  /** 分页查询喷淋操作日志 */
  getOperationLogPage(params) {
    return request({ url: '/api/spray/operationLog/page', method: 'get', params })
  },

  /** 查询喷淋操作统计 */
  getStatistics(params) {
    return request({ url: '/api/spray/statistics', method: 'get', params })
  },

  /** 查询喷淋操作详情 */
  getLogDetail(id) {
    return request({ url: `/api/spray/operationLog/${id}`, method: 'get' })
  },

  /** 导出喷淋操作日志（CSV 文件） */
  exportLog(params) {
    return request({ url: '/api/spray/operationLog/export', method: 'get', params, responseType: 'blob' })
  },

  // ==================== 喷淋定时任务 ====================
  /** 分页查询定时任务 */
  getSchedulePage(params) {
    return request({ url: '/api/spray/schedule/page', method: 'get', params })
  },

  /** 查询定时任务详情 */
  getScheduleById(id) {
    return request({ url: `/api/spray/schedule/${id}`, method: 'get' })
  },

  /** 新增定时任务 */
  createSchedule(data) {
    return request({ url: '/api/spray/schedule', method: 'post', data })
  },

  /** 修改定时任务 */
  updateSchedule(data) {
    return request({ url: '/api/spray/schedule', method: 'put', data })
  },

  /** 删除定时任务 */
  deleteSchedule(id) {
    return request({ url: `/api/spray/schedule/${id}`, method: 'delete' })
  },

  /** 启用/禁用定时任务 */
  toggleSchedule(id, status) {
    return request({ url: `/api/spray/schedule/${id}/status`, method: 'put', data: { status } })
  },

  /** 检查定时任务时间冲突 */
  checkConflict(data) {
    return request({ url: '/api/spray/schedule/conflictCheck', method: 'get', params: data })
  },

  /** 查询所有启用中的定时任务 */
  getActiveSchedules() {
    return request({ url: '/api/spray/schedule/active', method: 'get' })
  }
}
