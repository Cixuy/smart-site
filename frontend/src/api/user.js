import request from '@/utils/request'

export default {
  /** 分页查询用户列表 */
  getPage(params) {
    return request({ url: '/api/sys/user/page', method: 'get', params })
  },

  /** 查询用户详情 */
  getById(id) {
    return request({ url: `/api/sys/user/${id}`, method: 'get' })
  },

  /** 新增用户 */
  create(data) {
    return request({ url: '/api/sys/user', method: 'post', data })
  },

  /** 修改用户 */
  update(data) {
    return request({ url: '/api/sys/user', method: 'put', data })
  },

  /** 批量删除用户 */
  deleteBatch(ids) {
    return request({ url: `/api/sys/user/${ids}`, method: 'delete' })
  },

  /** 修改用户状态（启用/禁用/解锁） */
  updateStatus(id, data) {
    return request({ url: `/api/sys/user/${id}/status`, method: 'put', data })
  },

  /** 重置用户密码 */
  resetPassword(id) {
    return request({ url: `/api/sys/user/${id}/resetPwd`, method: 'put' })
  },

}
