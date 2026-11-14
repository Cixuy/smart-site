import request from '@/utils/request'

export default {
  /** 分页查询登录日志 */
  getLoginLogPage(params) {
    return request({ url: '/api/sys/log/login/page', method: 'get', params })
  },

  /** 分页查询操作日志 */
  getOperLogPage(params) {
    return request({ url: '/api/sys/log/oper/page', method: 'get', params })
  },

  /** 查询操作日志详情 */
  getOperLogById(id) {
    return request({ url: `/api/sys/log/oper/${id}`, method: 'get' })
  }
}
