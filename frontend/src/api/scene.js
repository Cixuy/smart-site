import request from '@/utils/request'

export default {
  // ==================== 3D场景配置 ====================
  /** 按项目查询3D场景配置 */
  getByProject(projectId) {
    return request({ url: `/api/scene/config/get-by-project/${projectId}`, method: 'get' })
  },

  /** 新增3D场景配置 */
  createConfig(data) {
    return request({ url: '/api/scene/config/create', method: 'post', data })
  },

  /** 查询3D场景配置详情 */
  getConfigDetail(sceneId) {
    return request({ url: `/api/scene/config/detail/${sceneId}`, method: 'get' })
  },

  /** 修改3D场景配置 */
  updateConfig(sceneId, data) {
    return request({ url: `/api/scene/config/update/${sceneId}`, method: 'put', data })
  },

  /** 删除3D场景配置 */
  deleteConfig(sceneId) {
    return request({ url: `/api/scene/config/delete/${sceneId}`, method: 'delete' })
  },

  /** 上传3D模型文件（支持 obj/fbx/gltf/glb，返回模型访问地址和MD5） */
  uploadModel(sceneId, formData) {
    return request({
      url: `/api/scene/model/upload/${sceneId}`,
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // ==================== 设备3D坐标映射 ====================
  /** 新增设备3D坐标映射 */
  createDeviceCoordinate(sceneId, data) {
    return request({ url: `/api/scene/device-coordinate/create/${sceneId}`, method: 'post', data })
  },

  /** 修改设备3D坐标映射 */
  updateDeviceCoordinate(coordinateId, data) {
    return request({ url: `/api/scene/device-coordinate/update/${coordinateId}`, method: 'put', data })
  },

  /** 删除设备3D坐标映射 */
  deleteDeviceCoordinate(coordinateId) {
    return request({ url: `/api/scene/device-coordinate/delete/${coordinateId}`, method: 'delete' })
  },

  // ==================== 场景设备 ====================
  /** 查询3D场景设备坐标与实时状态（iconType：1塔吊 2升降机 3摄像头 4环境监测点 5喷淋设备） */
  getDeviceList(sceneId, iconType) {
    return request({ url: `/api/scene/device/list/${sceneId}`, method: 'get', params: { iconType } })
  },

  /** 查询3D设备详情卡片 */
  getDeviceDetail(sceneId, deviceId) {
    return request({ url: `/api/scene/device/detail/${sceneId}/${deviceId}`, method: 'get' })
  },

  /** 查询3D场景告警高亮设备 */
  getAlarmDevices(sceneId) {
    return request({ url: `/api/scene/alarm-device/list/${sceneId}`, method: 'get' })
  },

  // ==================== 3D视频叠加配置 ====================
  /** 查询3D场景视频叠加配置 */
  getVideoOverlayList(sceneId) {
    return request({ url: `/api/scene/video-overlay/list/${sceneId}`, method: 'get' })
  },

  /** 新增3D视频叠加配置 */
  createVideoOverlay(data) {
    return request({ url: '/api/scene/video-overlay/create', method: 'post', data })
  },

  /** 修改3D视频叠加配置 */
  updateVideoOverlay(overlayId, data) {
    return request({ url: `/api/scene/video-overlay/update/${overlayId}`, method: 'put', data })
  },

  /** 删除3D视频叠加配置 */
  deleteVideoOverlay(overlayId) {
    return request({ url: `/api/scene/video-overlay/delete/${overlayId}`, method: 'delete' })
  }
}
