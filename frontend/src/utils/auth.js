/**
 * Token 管理工具
 * 使用 Bearer Token 替代旧的 Admin-Token
 */
const TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken() {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/** 移除所有授权信息 */
export function removeAuth() {
  removeToken()
  removeRefreshToken()
  localStorage.removeItem('role')
  localStorage.removeItem('token')
}

/** 注入授权信息到请求头 */
export function addAuth(token) {
  setToken(token)
}

/** 获取授权信息 */
export function getAuth() {
  return !!getToken()
}
