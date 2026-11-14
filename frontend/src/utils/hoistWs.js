/**
 * 升降机实时数据 WebSocket 客户端
 *
 * 与塔吊 towerCraneWs.js 同构，按《升降机监控 API 文档》实现，
 * 与全局告警 wsManager（/ws/realtime，小写协议）相互独立，避免改动全局告警推送功能。
 *
 * 连接地址：ws://{base_url}/api/ws/hoist/realtime?token={jwtToken}&deviceId=HOIST001
 * 注意：后端 server.servlet.context-path=/api，WebSocket 握手路径同样受其约束，
 *       因此必须带 /api 前缀，否则返回 404。
 * 协议（消息 type 为大写枚举）：
 *   客户端 → 服务端：SUBSCRIBE / HEARTBEAT / UNSUBSCRIBE
 *   服务端 → 客户端：SUBSCRIBE_OK / REALTIME_DATA / HEARTBEAT_OK / ERROR
 *
 * 规则（与塔吊一致）：
 *   - 握手成功后发送 SUBSCRIBE；服务端回 SUBSCRIBE_OK 并立即推送一次最新数据，
 *     之后按 frequency（默认 1000ms）持续推送 REALTIME_DATA
 *   - 客户端每 30 秒发送 HEARTBEAT，服务端回 HEARTBEAT_OK
 *   - 客户端 60 秒未收到任何消息应主动断开重连；重连后需重新发送 SUBSCRIBE
 *
 * 用法：
 *   import hoistWs from '@/utils/hoistWs'
 *   hoistWs.onStatus = (status) => {}
 *   hoistWs.onData   = (data) => {}   // REALTIME_DATA 平铺字段
 *   hoistWs.onError  = (info) => {}
 *   hoistWs.connect(token)
 *   hoistWs.subscribe(deviceId)
 *   hoistWs.unsubscribe()
 *   hoistWs.disconnect()
 */

class HoistWs {
  constructor() {
    this.ws = null
    this.url = ''
    this.token = ''
    this.deviceId = ''           // 当前订阅的设备编码
    this._status = 'idle'        // idle / connecting / open / closed / reconnecting
    this.onStatus = null
    this.onData = null           // (data) => {}  收到 REALTIME_DATA
    this.onError = null          // (info) => {}

    this.heartbeatTimer = null   // 30s 心跳定时器
    this.staleTimer = null       // 60s 无消息检测定时器
    this.reconnectTimer = null
    this.reconnectCount = 0
    this.isManualClose = false
  }

  get status() {
    return this._status
  }

  _setStatus(s) {
    if (this._status !== s) {
      this._status = s
      if (typeof this.onStatus === 'function') {
        try { this.onStatus(s) } catch (_) { /* mute */ }
      }
    }
  }

  /** 拼接 WebSocket 地址：与 websocket.js 同一套 VITE_APP_Address 解析逻辑 */
  _buildUrl() {
    const addr = import.meta.env.VITE_APP_Address || ''
    let host, wsProtocol
    if (addr.startsWith('http://') || addr.startsWith('https://')) {
      try {
        const u = new URL(addr)
        host = u.host
        wsProtocol = u.protocol === 'https:' ? 'wss:' : 'ws:'
      } catch {
        host = location.host
        wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
      }
    } else if (addr) {
      host = addr
      wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    } else {
      host = location.host
      wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    }
    // token 放在 query 参数或 Authorization 请求头二选一，这里用 query 参数
    // 后端 context-path=/api，WS 握手路径同样受约束，因此带 /api 前缀
    return `${wsProtocol}//${host}/api/ws/hoist/realtime?token=${encodeURIComponent(this.token)}`
  }

  /** 建立连接（token 相同且已连接时忽略，避免重复连接） */
  connect(token) {
    if (this.ws && this._status === 'open' && this.token === token) return
    this.token = token || ''
    this.isManualClose = false
    this.reconnectCount = 0
    this.url = this._buildUrl()
    this._createConnection()
  }

  _createConnection() {
    this._clearTimers()
    if (this.ws) {
      try { this.ws.close() } catch (_) { /* mute */ }
      this.ws = null
    }

    this._setStatus('connecting')

    try {
      this.ws = new WebSocket(this.url)
    } catch (e) {
      console.error('[升降机WS] 创建连接失败:', e)
      this._setStatus('closed')
      this._scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      this._setStatus('open')
      this._startHeartbeat()
      // 重连后需重新发送 SUBSCRIBE
      if (this.deviceId) this.subscribe(this.deviceId)
    }

    this.ws.onmessage = (event) => {
      // 收到任何消息都刷新"最后消息时间"，供 60s 断线检测使用
      this._resetStaleTimer()
      let msg
      try {
        msg = JSON.parse(event.data)
      } catch (e) {
        console.warn('[升降机WS] 消息解析失败:', e)
        return
      }
      this._handleMessage(msg)
    }

    this.ws.onerror = () => {
      if (typeof this.onError === 'function') {
        try { this.onError({ type: 'connection_error' }) } catch (_) { /* mute */ }
      }
    }

    this.ws.onclose = () => {
      this._setStatus('closed')
      this._stopHeartbeat()
      if (!this.isManualClose) this._scheduleReconnect()
    }
  }

  /** 按消息枚举分发服务端消息 */
  _handleMessage(msg) {
    switch (msg.type) {
      case 'SUBSCRIBE_OK':
        console.log('[升降机WS] 订阅成功:', msg.msg || '')
        break
      case 'REALTIME_DATA':
        // 数据字段平铺（deviceId/loadWeight/height/runDirection/.../interlockAlarm），直接回调
        if (typeof this.onData === 'function') {
          try { this.onData(msg) } catch (e) { console.error('[升降机WS] 数据处理错误:', e) }
        }
        break
      case 'HEARTBEAT_OK':
        // 心跳确认，无需处理
        break
      case 'ERROR':
        console.warn('[升降机WS] 服务端错误:', msg.msg)
        if (typeof this.onError === 'function') {
          try { this.onError({ type: 'server_error', message: msg.msg }) } catch (_) { /* mute */ }
        }
        break
      default:
        break
    }
  }

  _send(payload) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false
    try {
      this.ws.send(JSON.stringify(payload))
      return true
    } catch (e) {
      console.error('[升降机WS] 发送失败:', e)
      return false
    }
  }

  /** 订阅设备（切换设备时内部先退订旧设备） */
  subscribe(deviceId, frequency = 1000) {
    if (!deviceId) return
    const prev = this.deviceId
    this.deviceId = deviceId
    if (this._status === 'open') {
      if (prev && prev !== deviceId) {
        this._send({ type: 'UNSUBSCRIBE', topic: 'hoistRealtime', deviceId: prev })
      }
      this._send({ type: 'SUBSCRIBE', topic: 'hoistRealtime', deviceId, frequency })
    }
  }

  /** 退订当前设备 */
  unsubscribe() {
    if (this.deviceId && this._status === 'open') {
      this._send({ type: 'UNSUBSCRIBE', topic: 'hoistRealtime', deviceId: this.deviceId })
    }
    this.deviceId = ''
  }

  // ==================== 心跳（30s） ====================

  _startHeartbeat() {
    this._stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        const now = new Date()
        const pad = (n) => String(n).padStart(2, '0')
        // clientTime 格式：yyyy-MM-dd HH:mm:ss.SSS（与文档示例一致）
        const clientTime =
          `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ` +
          `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}.${String(now.getMilliseconds()).padStart(3, '0')}`
        this._send({ type: 'HEARTBEAT', clientTime })
      }
    }, 30000)
  }

  _stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  // ==================== 60s 无消息断线检测 ====================

  _resetStaleTimer() {
    if (this.staleTimer) {
      clearTimeout(this.staleTimer)
      this.staleTimer = null
    }
    // 60 秒未收到任何消息应主动断开重连
    this.staleTimer = setTimeout(() => {
      console.warn('[升降机WS] 60s 未收到任何消息，主动断开重连')
      if (this.ws) {
        try { this.ws.close() } catch (_) { /* mute */ }
      }
    }, 60000)
  }

  _clearStaleTimer() {
    if (this.staleTimer) {
      clearTimeout(this.staleTimer)
      this.staleTimer = null
    }
  }

  // ==================== 断线重连（指数退避，上限 30s） ====================

  _scheduleReconnect() {
    if (this.isManualClose) return
    this._setStatus('reconnecting')
    const delay = Math.min(3000 * Math.pow(2, this.reconnectCount), 30000)
    this.reconnectCount++
    this.reconnectTimer = setTimeout(() => {
      this._createConnection()
    }, delay)
  }

  _clearTimers() {
    this._stopHeartbeat()
    this._clearStaleTimer()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  /** 手动断开（页面卸载时调用） */
  disconnect() {
    this.isManualClose = true
    this.unsubscribe()
    this._clearTimers()
    if (this.ws) {
      try { this.ws.close(1000, 'client disconnect') } catch (_) { /* mute */ }
      this.ws = null
    }
    this._setStatus('closed')
    this.token = ''
  }
}

// 单例导出：整个应用共享一条升降机实时推送连接
const hoistWs = new HoistWs()
export default hoistWs
