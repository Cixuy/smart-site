import request from '@/utils/request'

export default {
  // ==================== 统一告警（/api/alarms） ====================
  /**
   * 分页查询统一告警列表
   * 默认排序：未处置置顶 → 控制>警报>预警 → 告警时间倒序
   * @param {Object} params
   *  pageNum/pageSize 分页
   *  projectId 项目ID（不传按当前用户数据权限）
   *  sourceType 告警来源 1-设备监测 2-环境监测 3-AI识别
   *  deviceTypeId 来源设备类型ID
   *  alarmLevel 告警级别 1-预警 2-警报 3-控制
   *  status 处置状态 0-未处置 1-处置中 2-已处置
   *  alarmTypeCode 告警类型编码
   *  deviceId/monitorPointId/cameraId 关联对象ID
   *  relatedAlarmId 关联告警组根告警ID
   *  keyword 告警编号/来源名称/告警内容 模糊搜索
   *  startTime/endTime 告警时间范围（含）
   */
  getPageList(params) {
    return request({ url: '/api/alarms', method: 'get', params })
  },

  /** 生成统一告警（供设备/环境/AI 模块写入 biz_alarm_record，服务端生成 alarmNo） */
  createAlarm(data) {
    return request({ url: '/api/alarms', method: 'post', data })
  },

  /**
   * 查询告警详情（供告警处置工作台使用）
   * 返回主记录、来源设备/监测点/摄像头、AI截图、处置历史、恢复记录、关联告警、推送状态
   */
  getDetail(id) {
    return request({ url: `/api/alarms/${id}`, method: 'get' })
  },

  /** 逻辑删除告警（仅系统管理员，未处置/处置中禁止删除） */
  removeAlarm(id) {
    return request({ url: `/api/alarms/${id}`, method: 'delete' })
  },

  // ==================== 告警处置闭环 ====================
  /** 接单处理告警（0-未处置 → 1-处置中，原子更新防多人并发接单） */
  claimAlarm(id) {
    return request({ url: `/api/alarms/${id}/claim`, method: 'post' })
  },

  /**
   * 告警处置（提交处置记录，更新告警状态）
   * @param {Object} data AlarmDisposalDTO
   *  alarmId 告警ID
   *  disposalContent 处置内容
   *  disposalConclusion 处置结论 1-属实 2-误报 3-其他
   *  falseReason 误报原因（结论为误报时必填）
   */
  dispose(data) {
    return request({ url: '/api/alarms/disposal', method: 'post', data })
  },

  /** 分页查询告警处置记录（按处置时间升序，独立查询 biz_alarm_disposal） */
  getDisposals(id, params) {
    return request({ url: `/api/alarms/${id}/disposals`, method: 'get', params })
  },

  /** 获取告警处置与恢复历史（完整闭环记录） */
  getHistory(id) {
    return request({ url: `/api/alarms/${id}/history`, method: 'get' })
  },

  /** 写入告警自动恢复记录（按 alarmId 幂等，恢复不替代人工处置状态） */
  recoverAlarm(data) {
    return request({ url: '/api/alarms/recovery', method: 'post', data })
  },

  /** 查询告警恢复记录（尚未恢复时成功返回 data=null，而不是404） */
  getRecovery(id) {
    return request({ url: `/api/alarms/${id}/recovery`, method: 'get' })
  },

  /** 查询关联告警组（展开同一事件的根告警及全部衍生告警） */
  getRelatedAlarms(id) {
    return request({ url: `/api/alarms/${id}/related`, method: 'get' })
  },

  // ==================== 告警推送 ====================
  /** 分页查询告警推送记录（渠道/接收方/发送状态/失败原因/重试次数） */
  getPushLogs(id, params) {
    return request({ url: `/api/alarms/${id}/push-logs`, method: 'get', params })
  },

  /** 重试失败的告警推送（仅系统管理员，仅 pushStatus=2 可重试） */
  retryPush(pushLogId) {
    return request({ url: `/api/alarms/push-logs/${pushLogId}/retry`, method: 'post' })
  },

  // ==================== 统计看板 ====================
  /** 告警总览（总数 + 按处置状态/级别/来源的数量和占比，前端每5秒刷新） */
  getOverview(params) {
    return request({ url: '/api/alarms/overview', method: 'get', params })
  },

  /** 告警趋势（按日/周/月返回数量变化曲线） */
  getTrend(params) {
    return request({ url: '/api/alarms/statistics/trend', method: 'get', params })
  },

  /** 告警分布（按告警类型或设备类型统计数量及占比，用于饼图展示和下钻） */
  getDistribution(params) {
    return request({ url: '/api/alarms/statistics/distribution', method: 'get', params })
  },

  /** 告警处置及时率（及时率=(规定分钟内完成处置数/已完成处置总数)*100%） */
  getTimeliness(params) {
    return request({ url: '/api/alarms/statistics/timeliness', method: 'get', params })
  },

  /** 告警统计看板数据（按天，projectId + date 必传） */
  getDailyStatistics(params) {
    return request({ url: '/api/alarms/statistics/daily', method: 'get', params })
  },

  /**
   * 导出告警统计报表（XLSX/CSV）
   * @returns {Promise<AxiosResponse>} 文件流响应，需自行触发下载
   */
  exportStatistics(params) {
    return request({
      url: '/api/alarms/statistics/export',
      method: 'get',
      params,
      responseType: 'blob'
    })
  },

  // ==================== 兼容旧版：告警记录（/api/alarms/record） ====================
  /**
   * 查询告警记录列表（多条件组合查询 + 分页 + 排序）
   * @param {Object} params
   *  pageNum/pageSize 分页
   *  projectId 所属项目ID
   *  sourceType 告警来源 1-设备监测 2-环境监测 3-AI识别
   *  alarmLevel 告警级别 1-预警 2-警报 3-控制
   *  alarmTypeCode 告警类型编码（精确匹配）
   *  status 处置状态 0-未处置 1-处置中 2-已处置
   *  deviceId/cameraId/monitorPointId 关联对象ID
   *  alarmNo 告警编号（精确匹配）
   *  alarmContent 告警内容（模糊匹配）
   *  relatedAlarmId 关联告警ID（告警链）
   *  isDeleted 逻辑删除标记 0-未删除 1-已删除
   *  startTime/endTime 告警时间范围 yyyy-MM-dd HH:mm:ss
   *  sortField 排序字段（alarm_time/id/alarm_level/status/created_at）
   *  sortOrder 排序方向 asc/desc
   */
  getRecordList(params) {
    return request({ url: '/api/alarms/record', method: 'get', params })
  }
}
