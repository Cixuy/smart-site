import request from '@/utils/request'

/**
 * 系统当前唯一项目 ID
 * 说明：平台只有一个项目，无需前端选择/切换项目，接口统一携带该固定值。
 */
export const DEFAULT_PROJECT_ID = 1

// ==================== 数据大屏配置 ====================
export default {
  /** 查询数据大屏配置列表 (status 可选: 1启用 0停用) */
  getConfigList(params = {}) {
    return request({
      url: '/api/screen/config/list',
      method: 'get',
      params: { ...params, projectId: DEFAULT_PROJECT_ID }
    })
  },

  /** 新增数据大屏配置 */
  createConfig(data = {}) {
    return request({
      url: '/api/screen/config/create',
      method: 'post',
      data: { ...data, projectId: DEFAULT_PROJECT_ID }
    })
  },

  /** 查询数据大屏配置详情 */
  getConfigDetail(screenId) {
    return request({ url: `/api/screen/config/detail/${screenId}`, method: 'get' })
  },

  /** 修改数据大屏配置 */
  updateConfig(screenId, data = {}) {
    return request({
      url: `/api/screen/config/update/${screenId}`,
      method: 'put',
      data: { ...data, projectId: DEFAULT_PROJECT_ID }
    })
  },

  /** 删除数据大屏配置 */
  deleteConfig(screenId) {
    return request({ url: `/api/screen/config/delete/${screenId}`, method: 'delete' })
  },

  /** 设置项目默认数据大屏 */
  setDefaultScreen(screenId) {
    return request({ url: `/api/screen/config/set-default/${screenId}`, method: 'put' })
  },

  // ==================== 大屏实时数据 ====================
  /** 查询数据大屏实时数据 (设备状态/告警滚动/塔吊/升降机/环境/告警趋势/视频轮播) */
  getRealtimeData(screenId) {
    return request({ url: `/api/screen/realtime/get-data/${screenId}`, method: 'get' })
  },

  // ==================== 大屏视频轮播配置 ====================
  /** 查询大屏视频轮播配置 */
  getVideoRotationConfig(screenId) {
    return request({ url: `/api/screen/video-rotation/list/${screenId}`, method: 'get' })
  },

  /** 批量保存大屏视频轮播配置 */
  saveVideoRotationConfig(screenId, data) {
    return request({ url: `/api/screen/video-rotation/save/${screenId}`, method: 'put', data })
  }
}
