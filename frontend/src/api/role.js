import request from '@/utils/request'

export default {
  /** 分页查询角色列表 */
  getPage(params) {
    return request({ url: '/api/sys/role/page', method: 'get', params })
  },

  /** 查询角色详情 */
  getById(id) {
    return request({ url: `/api/sys/role/${id}`, method: 'get' })
  },

  /** 新增角色 */
  create(data) {
    return request({ url: '/api/sys/role', method: 'post', data })
  },

  /** 修改角色 */
  update(data) {
    return request({ url: '/api/sys/role', method: 'put', data })
  },

  /** 批量删除角色 */
  deleteBatch(ids) {
    return request({ url: `/api/sys/role/${ids}`, method: 'delete' })
  },

  /** 查询角色已分配的菜单权限 */
  getRoleMenus(id) {
    return request({ url: `/api/sys/role/${id}/menus`, method: 'get' })
  },

  /** 分配角色菜单权限 */
  assignMenus(id, data) {
    return request({ url: `/api/sys/role/${id}/menus`, method: 'put', data })
  },

  /** 查询所有角色 */
  getAll() {
    return request({ url: '/api/sys/role/all', method: 'get' })
  }
}
