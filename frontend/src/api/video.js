import request from '@/utils/request'

export default {
  // ==================== 摄像头管理 ====================
  /** 查询摄像头列表 */
  getCameras(params) {
    return request({ url: '/api/video/cameras', method: 'get', params })
  },

  /** 新增摄像头 */
  createCamera(data) {
    return request({ url: '/api/video/cameras', method: 'post', data })
  },

  /** 修改摄像头信息 */
  updateCamera(id, data) {
    return request({ url: `/api/video/cameras/${id}`, method: 'put', data })
  },

  /** 删除摄像头 */
  deleteCamera(id) {
    return request({ url: `/api/video/cameras/${id}`, method: 'delete' })
  },

  /**
   * 获取摄像头视频流地址
   * 返回 Result<{ cameraId, cameraName, streamUrl, protocolType, onlineStatus }>
   * 由 CameraPlayer 播放器组件调用，用于实时监控卡片 / 实时预览 / 录像回放。
   */
  getCameraStreamUrl(id) {
    return request({ url: `/api/video/cameras/${id}/stream-url`, method: 'get' })
  },

  // ==================== 录像截图 ====================
  /** 查询录像/截图列表 */
  getVideoRecords(params) {
    return request({ url: '/api/video/video-records', method: 'get', params })
  },

  /** 获取AI告警截图列表 */
  getAiSnapshots(params) {
    return request({ url: '/api/video/video-records/ai-snapshots', method: 'get', params })
  },

  /** 获取录像分段时间轴 */
  getSegments(params) {
    return request({ url: '/api/video/video-records/segments', method: 'get', params })
  }
}
