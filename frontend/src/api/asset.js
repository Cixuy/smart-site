import request from '@/utils/request'

/** 设备资产管理 API */
export default {
  // ==================== 设备类型管理 ====================
  /** 查询设备类型树 */
  getTypeTree(params) {
    return request({ url: '/api/asset/type/tree', method: 'get', params })
  },

  /** 分页查询设备类型列表(平铺) */
  getTypePage(params) {
    return request({ url: '/api/asset/type/list', method: 'get', params })
  },

  /** 新增设备类型 */
  createType(data) {
    return request({ url: '/api/asset/type', method: 'post', data })
  },

  /** 修改设备类型 */
  updateType(data) {
    return request({ url: '/api/asset/type', method: 'put', data })
  },

  /** 查询设备类型详情 */
  getTypeById(id) {
    return request({ url: `/api/asset/type/${id}`, method: 'get' })
  },

  /** 批量删除设备类型 */
  deleteTypeBatch(ids) {
    return request({ url: `/api/asset/type/${ids}`, method: 'delete' })
  },

  // ==================== 设备台账管理 ====================
  /** 分页查询设备台账 */
  getDevicePage(params) {
    return request({ url: '/api/asset/device/page', method: 'get', params })
  },

  /** 查询设备详情 */
  getDeviceById(id) {
    return request({ url: `/api/asset/device/${id}`, method: 'get' })
  },

  /** 新增设备 */
  createDevice(data) {
    return request({ url: '/api/asset/device', method: 'post', data })
  },

  /** 修改设备 */
  updateDevice(data) {
    return request({ url: '/api/asset/device', method: 'put', data })
  },

  /** 批量删除设备 */
  deleteDeviceBatch(ids) {
    return request({ url: `/api/asset/device/${ids.join(',')}`, method: 'delete' })
  },

  /** 修改设备启用状态 */
  updateDeviceStatus(id, enableStatus) {
    return request({ url: `/api/asset/device/${id}/status`, method: 'put', data: { enableStatus } })
  },

  /** 获取设备二维码 */
  getDeviceQrCode(id) {
    return request({ url: `/api/asset/device/${id}/qrCode`, method: 'get' })
  },

  /** 设备在线率统计 */
  getDeviceOnlineStats(params) {
    return request({ url: '/api/asset/device/onlineStats', method: 'get', params })
  },

  /** 单设备在线率 */
  getDeviceOnlineRate(id, params) {
    return request({ url: `/api/asset/device/${id}/onlineRate`, method: 'get', params })
  },

  /** 上传设备合格证附件 */
  uploadCertificate(data) {
    return request({ url: '/api/asset/device/upload/certificate', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } })
  },

  // ==================== 传感器管理 ====================
  /** 分页查询传感器列表（parentType: 0-无父级，1-设备，2-环境监测点；与 parentId 组合筛选） */
  getSensorPage(params) {
    return request({ url: '/api/asset/sensor/page', method: 'get', params })
  },

  /** 查询传感器详情（返回 parentId/parentType/parentName） */
  getSensorById(id) {
    return request({ url: `/api/asset/sensor/${id}`, method: 'get' })
  },

  /** 新增传感器（parentType 必填；parentType=1 传设备ID，parentType=2 传环境监测点ID，parentType=0 不传 parentId） */
  createSensor(data) {
    return request({ url: '/api/asset/sensor', method: 'post', data })
  },

  /** 修改传感器（父级关系 parentId/parentType 不在修改请求中变更） */
  updateSensor(data) {
    return request({ url: '/api/asset/sensor', method: 'put', data })
  },

  /** 批量删除传感器（逻辑删除，历史环境数据保留；ids 逗号分隔） */
  deleteSensorBatch(ids) {
    return request({ url: `/api/asset/sensor/${ids}`, method: 'delete' })
  },

  /** 修改传感器运行状态（1-运行，0-停止，2-故障） */
  updateSensorStatus(id, status) {
    return request({ url: `/api/asset/sensor/${id}/status`, method: 'put', data: { status } })
  },

  /** 传感器数据上报（须 sensor 的 parentType=2 才写入环境监测历史数据） */
  pushSensorData(data) {
    return request({ url: '/api/asset/sensor/data/push', method: 'post', data })
  },

  // ==================== 位置树管理 ====================
  /** 查询位置树（可传 { status: 1 } 仅返回启用区域） */
  getLocationTree(params) {
    return request({ url: '/api/asset/location/tree', method: 'get', params })
  },

  /** 查询位置详情 */
  getLocationById(id, opts) {
    return request({ url: `/api/asset/location/${id}`, method: 'get', ...opts })
  },

  /** 新增位置 */
  createLocation(data) {
    return request({ url: '/api/asset/location', method: 'post', data })
  },

  /** 修改位置 */
  updateLocation(data) {
    return request({ url: '/api/asset/location', method: 'put', data })
  },

  /** 删除位置 */
  deleteLocation(id) {
    return request({ url: `/api/asset/location/${id}`, method: 'delete' })
  },

  // ==================== 空间位置管理（GIS扩展） ====================
  /** 获取GIS坐标数据（地图打点用） */
  getLocationMap() {
    return request({ url: '/api/asset/location/map', method: 'get' })
  },

  /** 批量更新位置坐标 */
  batchUpdateCoordinates(data) {
    return request({ url: '/api/asset/location/coordinates', method: 'put', data })
  },

  /** 搜索附近位置 */
  searchNearby(params) {
    return request({ url: '/api/asset/location/nearby', method: 'get', params })
  },

  /** 获取位置树（含坐标，平铺列表） */
  getLocationList(params) {
    return request({ url: '/api/asset/location/list', method: 'get', params })
  }
}
