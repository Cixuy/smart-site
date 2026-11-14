import request from '@/utils/request'

/**
 * 传感器推流 API
 * 按《传感器推流 API 文档》实现：
 *   - 元数据列表：GET /api/sensor/list（JWT Bearer，可选 parentId/parentType 筛选）
 *   - 订阅 / 取消订阅 / 心跳保活：WebSocket 实时推流，见 @/utils/sensorWs.js
 *
 * 文档约定：前端在建立 WebSocket 连接前/后调用本接口获取传感器静态配置
 * （名称、阈值、父节点等），存入本地 Map；WS 仅推送精简的 4 字段数据
 * （id / monitor_type / value / unit），前端通过 id 匹配此列表中的元数据进行渲染。
 *
 * 2026-08-17 修正：后端 SensorMetadataController 的 @RequestParam 绑定的是
 * 驼峰参数名 parentId / parentType（非下划线），而接口文档示例为下划线
 * parent_id / parent_type。调用方建议双传（parentId + parent_id 均带上），
 * Spring 会忽略未绑定的多余参数，从而兼容两端。
 */
export default {
  /**
   * 获取传感器元数据列表 GET /api/sensor/list
   * @param {Object} params
   *   - parentId / parent_id:    number 可选，按设备/监测点ID筛选（推荐双传）
   *   - parentType / parent_type: number 可选，父节点类型：1-设备，2-监测点（推荐双传）
   * @returns {Promise<Result<SensorMeta[]>>} 统一 Result<T>（code/message/data）
   *   返回字段兼容驼峰（sensorName/monitorType…）与下划线（sensor_name/monitor_type…）
   */
  getSensorList(params = {}) {
    return request({ url: '/api/sensor/list', method: 'get', params })
  }
}
