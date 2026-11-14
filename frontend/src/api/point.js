import request from '@/utils/request'

/**
 * 环境监测点管理 API
 * 对应 /api/env-monitor/points 系列接口（设备资产-监测点管理模块）。
 * 说明：环境监测点管理自身信息，传感器通过 parentId 关联到监测点。
 */
export default {
  /** 分页查询环境监测点（支持名称、安装位置模糊查询） */
  getPointPage(params) {
    return request({ url: '/api/env-monitor/points/page', method: 'get', params })
  },

  /** 查询环境监测点详情（含关联传感器摘要） */
  getPointById(id) {
    return request({ url: `/api/env-monitor/points/${id}`, method: 'get' })
  },

  /** 新增环境监测点（名称必填，位置可选） */
  createPoint(data) {
    return request({ url: '/api/env-monitor/points', method: 'post', data })
  },

  /** 修改环境监测点（仅支持修改名称和安装位置） */
  updatePoint(id, data) {
    return request({ url: `/api/env-monitor/points/${id}`, method: 'put', data })
  },

  /** 删除环境监测点（存在关联运行传感器时后端将拒绝删除） */
  deletePoint(id) {
    return request({ url: `/api/env-monitor/points/${id}`, method: 'delete' })
  }
}
