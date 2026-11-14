import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken, removeAuth } from '@/utils/auth'
import router from '@/router'

/** 防抖错误提示 */
let lastErrorMessage = ''
let lastErrorTime = 0

function errorMessage(msg, type = 'error') {
  const now = Date.now()
  if (msg === lastErrorMessage && now - lastErrorTime < 2000) return
  lastErrorMessage = msg
  lastErrorTime = now
  ElMessage({ showClose: true, message: msg, duration: 2000, type })
}

/** 清除缓存并跳转登录 */
function redirectToLogin() {
  removeAuth()
  // 异步清理 Vuex store 和动态路由（fire-and-forget，避免循环依赖）
  Promise.all([import('@/store'), import('@/router')])
    .then(([{ default: store }, { default: routerInstance }]) => {
      store.dispatch('user/clearAll')
      if (routerInstance.hasRoute('Layout')) {
        routerInstance.removeRoute('Layout')
        routerInstance.addRoute({ path: '/', name: 'Root', redirect: '/login' })
      }
    })
    .catch(() => {})
  router.push('/login')
}

/** 构建 baseURL：若地址已含协议则直接用，否则补 http:// */
function buildBaseURL() {
  const addr = import.meta.env.VITE_APP_Address || ''
  if (!addr) return ''
  if (addr.startsWith('http://') || addr.startsWith('https://')) return addr
  return `http://${addr}`
}

/** 创建 Axios 实例 */
const service = axios.create({
  baseURL: buildBaseURL(),
  timeout: 60000
})

/** Request 拦截器 */
service.interceptors.request.use(
  (config) => {
    // 注入 Bearer Token
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }

    // 处理 Content-Type：未显式设置时默认使用 JSON
    const contentType = config.headers['Content-Type'] || ''
    const isFormData = contentType.includes('multipart/form-data')

    if (!isFormData && config.data && typeof config.data === 'object') {
      if (!contentType) {
        config.headers['Content-Type'] = 'application/json'
      }
    }

    return config
  },
  (error) => Promise.reject(error)
)

/** Response 拦截器 */
service.interceptors.response.use(
  (response) => {
    // 文件流响应直接返回
    if (response.config.responseType === 'blob') return response

    const res = response.data

    // 统一响应 code !== 0
    if (res.code !== undefined && res.code !== 0) {
      if (res.code === 302 || res.code === 401) {
        if (res.data?.extra === 1) {
          ElMessageBox.confirm(
            `您的账号${res.data?.extraTime || ''}在别处登录。如非本人操作，密码可能已泄漏。`,
            '异地登录提醒',
            { confirmButtonText: '确定', showCancelButton: false, type: 'warning' }
          ).then(() => redirectToLogin())
        } else {
          redirectToLogin()
        }
      } else if (res.code === 1005) {
        redirectToLogin()
      } else if (res.msg && !response.config.silent) {
        errorMessage(res.msg)
      }
      return Promise.reject(res)
    }

    return res
  },
  (error) => {
    // 静默模式：不提示错误，直接 reject
    if (error.config?.silent) return Promise.reject(error)

    if (error.response) {
      const { status } = error.response
      if (status === 401 || status === 403) {
        redirectToLogin()
      } else if (status === 500) {
        errorMessage('服务器内部错误，请稍后重试')
      } else if (error.response.data?.msg) {
        errorMessage(error.response.data.msg)
      } else {
        errorMessage('网络错误，请检查您的网络连接')
      }
    } else if (error.code === 'ECONNABORTED') {
      errorMessage('请求超时，请检查网络连接')
    } else {
      errorMessage('网络连接异常')
    }
    return Promise.reject(error)
  }
)

export default service
