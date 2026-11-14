import request from '@/utils/request'

export default {
  /** 用户登录 */
  login(data) {
    return request({ url: '/api/auth/login', method: 'post', data })
  },

  /** 用户登出 */
  logout() {
    return request({ url: '/api/auth/logout', method: 'post' })
  },

  /** 获取当前登录用户信息（用户/角色/权限/菜单树） */
  getCurrentUser() {
    return request({ url: '/api/auth/currentUser', method: 'get' })
  },

  /** 修改密码 */
  changePassword(data) {
    return request({ url: '/api/auth/password', method: 'put', data })
  },

  /** 刷新令牌 */
  refreshToken(data) {
    return request({ url: '/api/auth/refreshToken', method: 'post', data })
  }
}
