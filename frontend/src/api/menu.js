import request from '@/utils/request'

export default {
  /** 查询菜单树 */
  getTree() {
    return request({ url: '/api/sys/menu/tree', method: 'get' })
  },

  /** 查询菜单列表（平铺） */
  getList(params) {
    return request({ url: '/api/sys/menu/list', method: 'get', params })
  },

  /** 查询菜单详情 */
  getById(id) {
    return request({ url: `/api/sys/menu/${id}`, method: 'get' })
  },

  /** 新增菜单 */
  create(data) {
    return request({ url: '/api/sys/menu', method: 'post', data })
  },

  /** 修改菜单 */
  update(data) {
    return request({ url: '/api/sys/menu', method: 'put', data })
  },

  /** 删除菜单 */
  delete(id) {
    return request({ url: `/api/sys/menu/${id}`, method: 'delete' })
  }
}
