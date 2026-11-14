/**
 * 权限指令 v-permission
 * 用法: <el-button v-permission="['user:add']">新增</el-button>
 *       <el-button v-permission:role="['admin']">仅管理员</el-button>
 */
import store from '@/store'

function checkPermission(el, binding) {
  const { value, arg } = binding

  if (arg === 'role') {
    // 角色级权限
    const roles = store.getters['user/roles'] || []
    if (value && value.length > 0) {
      const hasRole = roles.some(role => value.includes(role))
      if (!hasRole) {
        el.parentNode?.removeChild(el)
      }
    }
  } else {
    // 按钮级权限
    const permissions = store.getters['user/permissions'] || store.state.app?.permissions || []
    if (value && value.length > 0) {
      const hasPermission = value.some(perm => permissions.includes(perm))
      if (!hasPermission) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}

export default {
  install(app) {
    app.directive('permission', {
      mounted(el, binding) {
        checkPermission(el, binding)
      },
      updated(el, binding) {
        checkPermission(el, binding)
      }
    })
  }
}
