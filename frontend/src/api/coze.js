import axios from 'axios'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

/**
 * Coze 智能体模块 —— 对接独立对话智能体服务（agent，FastAPI，port 8000）。
 *
 * 注意：该服务与 Spring Boot 后端（port 8080）是两套独立进程，接口路径同为 /api/agent/**。
 * - 开发环境：Vite 代理将 /api/agent 转发到 8000（见 vite.config.js），无需跨域。
 * - 生产环境：通过 VITE_AGENT_Address 指定智能体服务地址（可含协议），留空时以同源相对
 *   路径访问，由网关/反向代理把 /api/agent 路由到智能体服务。
 *
 * 认证：与主后端一致，均使用平台的 Bearer Token（本模块自行注入，复用 utils/auth）。
 * 所有接口返回 Result<T>：{ code, msg, data }，code=0 表示成功。
 */

/**
 * 构建智能体服务 baseURL
 * - 默认同源相对路径（''）：开发环境由 Vite 代理将 /api/agent 转发到 8000；
 *   生产环境由网关/反向代理把 /api/agent 路由到智能体服务。
 * - 也可通过 VITE_AGENT_Address 显式指定智能体服务地址（如 http://x.x.x.x:8000）。
 */
function buildAgentBaseURL() {
  const addr = import.meta.env.VITE_AGENT_Address || ''
  if (!addr) return ''
  return addr.startsWith('http://') || addr.startsWith('https://') ? addr : `http://${addr}`
}

/** 专用 axios 实例（不经过主请求拦截器，避免 code!=0 时的登录跳转误伤） */
const agentRequest = axios.create({
  baseURL: buildAgentBaseURL(),
  timeout: 60000
})

agentRequest.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
    config.headers['Content-Type'] = 'application/json'
  }
  return config
})

agentRequest.interceptors.response.use(
  (response) => unpack(response),
  (error) => {
    const body = error.response?.data
    const msg = body?.msg || (error.response ? `智能体接口异常（HTTP ${error.response.status}）` : '无法连接智能体服务')
    const err = new Error(msg)
    err.code = body?.code ?? error.response?.status ?? 0
    err.data = body?.data ?? null
    return Promise.reject(err)
  }
)

function unpack(response) {
  const body = response?.data
  if (body && body.code !== undefined && body.code !== 0) {
    const msg = body.msg || '智能体接口调用失败'
    if (!body.silent && body.code !== 302 && body.code !== 401) {
      ElMessage({ showClose: true, message: msg, duration: 2000, type: 'error' })
    }
    const err = new Error(msg)
    err.code = body.code
    err.data = body.data
    return Promise.reject(err)
  }
  return body
}

export default {
  /**
   * 分页查询会话列表
   * GET /api/agent/sessions?projectId=&pageNo=&pageSize=
   */
  listSessions(params = {}) {
    return agentRequest({ url: '/api/agent/sessions', method: 'get', params })
  },

  /**
   * 新建会话
   * POST /api/agent/sessions  body: { title?, projectId? }
   */
  createSession(data = {}) {
    return agentRequest({ url: '/api/agent/sessions', method: 'post', data })
  },

  /**
   * 获取会话历史消息（时间正序分页）
   * GET /api/agent/sessions/{sessionId}/messages?pageNo=&pageSize=
   */
  listMessages(sessionId, params = {}) {
    return agentRequest(
      { url: `/api/agent/sessions/${encodeURIComponent(sessionId)}/messages`, method: 'get', params }
    )
  },

  /**
   * 发送消息给智能体
   * POST /api/agent/sessions/{sessionId}/messages
   * body: { content, projectId?, context? }
   * 返回 { userMessageId, assistantMessageId, status, streamUrl }
   */
  sendMessage(sessionId, data) {
    return agentRequest(
      { url: `/api/agent/sessions/${encodeURIComponent(sessionId)}/messages`, method: 'post', data }
    )
  },

  /**
   * 建立 SSE 流式获取回复（fetch 读取流，携带 Bearer Token）
   * GET {streamUrl}
   *
   * 返回解析函数：
   *   - onDelta(delta)  : 收到增量文本
   *   - onDone(info)    : 收到 done 事件 { messageId, status, intentType, responseDurationMs }
   *   - onError(err)    : 出错
   * 返回 AbortController，可调用 .abort() 中断。
   */
  streamMessage(streamUrl, { onDelta, onDone, onError } = {}) {
    const controller = new AbortController()
    const base = buildAgentBaseURL()
    const url = streamUrl.startsWith('http')
      ? streamUrl
      : `${base.replace(/\/$/, '')}${streamUrl}`

    ;(async () => {
      try {
        const response = await fetch(url, {
          method: 'GET',
          headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
          signal: controller.signal
        })
        if (!response.ok) {
          throw new Error(`流式请求失败（HTTP ${response.status}）`)
        }
        if (!response.body) {
          throw new Error('当前浏览器不支持流式读取，请升级浏览器')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        const readLoop = async () => {
          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })
            const frames = buffer.split(/\r?\n\r?\n/)
            buffer = frames.pop() || ''
            for (const frame of frames) {
              handleFrame(frame)
            }
          }
        }

        const handleFrame = (frame) => {
          let event = 'message'
          let dataLines = []
          for (const line of frame.split(/\r?\n/)) {
            if (line.startsWith('event:')) event = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
          }
          if (!dataLines.length) return
          const data = dataLines.join('\n')
          if (event === 'delta') {
            try {
              const json = JSON.parse(data)
              if (typeof onDelta === 'function') onDelta(json.delta || '')
            } catch (_) { /* 忽略无法解析的数据帧 */ }
          } else if (event === 'done') {
            if (typeof onDone === 'function') {
              try { onDone(JSON.parse(data)) } catch (_) { /* ignore */ }
            }
            controller.abort()
          } else if (event === 'error') {
            if (typeof onError === 'function') {
              try { onError(JSON.parse(data)) } catch (_) { /* ignore */ }
            }
            controller.abort()
          }
        }

        // 处理读取与 flush 剩余 buffer
        readLoop().then(() => {
          if (buffer.trim()) handleFrame(buffer)
          if (!controller.signal.aborted) controller.abort()
        }).catch((err) => {
          if (controller.signal.aborted) return
          if (typeof onError === 'function') onError(err)
        })
      } catch (err) {
        if (controller.signal.aborted) return
        if (typeof onError === 'function') onError(err)
      }
    })()

    return controller
  }
}
