/**
 * 传感器实时推流 WebSocket 客户端（单例）
 *
 * 2026-08-17 对照后端 SensorRealtimeWebSocketHandler 实际实现修正协议：
 *  1. 后端【只注册了 /api/ws/sensor/subscribe 一个端点】；取消订阅、心跳保活都在
 *     这同一条连接上通过消息 type 区分（文档里的 /unsubscribe、/ping 仅为提示，
 *     不作为独立端点存在，单独建连接会握手 404）。
 *  2. 连接建立后【必须主动发送订阅消息】才会收到推流：
 *        {"type":"subscribe","data":{"sensor_ids":[1,2,3]}}
 *     服务端回 subscribed 后按各传感器 refresh_rate 持续推送 sensor_data。
 *  3. 服务端消息 type 均为小写枚举：
 *        subscribed / sensor_data / unsubscribed / pong / error
 *  4. 推流数据格式（TCP 尚未上报该传感器时 value 为 null，前端按“等待数据”处理）：
 *        {"type":"sensor_data","data":{"id":1,"monitor_type":"wind_speed","value":12.5,"unit":"m/s","collect_time":"..."}}
 *
 * 用法：
 *   import sensorWs from '@/utils/sensorWs'
 *   sensorWs.onSubscribe   = (data) => {}   // subscribed
 *   sensorWs.onData        = (data) => {}   // sensor_data
 *   sensorWs.onUnsubscribe = (data) => {}   // unsubscribed
 *   sensorWs.onError       = (info) => {}   // 连接错误 / 服务端 error 通知
 *   sensorWs.onPong        = (data) => {}   // 心跳响应
 *   sensorWs.onStatus      = (status) => {} // idle/connecting/open/closed/reconnecting
 *   sensorWs.connect(token)                 // 建立连接（token 相同且已连接时忽略）
 *   sensorWs.subscribe([1,2,3])             // 订阅（连接未就绪会暂存，onopen 后自动补发）
 *   sensorWs.unsubscribe([1,2])             // 同一连接上取消订阅（可选，disconnect 也会被服务端清理）
 *   sensorWs.clearCallbacks()               // 页面卸载时清空回调，避免回调残留
 *   sensorWs.disconnect()                   // 手动断开（页面卸载时调用）
 */

class SensorWs {
  constructor() {
    this.ws = null             // 唯一连接（/api/ws/sensor/subscribe）
    this.token = ''
    this.sensorIds = []        // 当前订阅的传感器ID（重连后自动补订）
    this._status = 'idle'      // idle / connecting / open / closed / reconnecting
    this.onStatus = null
    this.onSubscribe = null    // (data) => {}  收到 subscribed
    this.onData = null         // (data) => {}  收到 sensor_data
    this.onUnsubscribe = null  // (data) => {}  收到 unsubscribed
    this.onError = null        // (info) => {}  连接错误 / 服务端 error
    this.onPong = null         // (data) => {}  收到 pong

    this.heartbeatTimer = null // 30s 心跳定时器（同一连接发送 ping）
    this.staleTimer = null     // 60s 无消息检测定时器
    this.reconnectTimer = null
    this.reconnectCount = 0
    this.isManualClose = false // 手动断开标记
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

  /** 拼接 WebSocket 地址：与 towerCraneWs/hoistWs 同一套 VITE_APP_Address 解析逻辑 */
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
    // token 放 query 参数；context-path=/api 需带 /api 前缀（否则 404）
    return `${wsProtocol}//${host}/api/ws/sensor/subscribe?token=${encodeURIComponent(this.token)}`
  }

  /** 建立连接（token 相同且已连接时忽略，避免重复连接） */
  connect(token) {
    if (!token) return
    if (this.ws && this._status === 'open' && this.token === token) return
    this.token = token
    this.isManualClose = false
    this.reconnectCount = 0
    this._createConnection()
  }

  /**
   * 订阅传感器：记录 ID 并发送订阅消息。
   * 连接未就绪时先暂存到 this.sensorIds，onopen 后自动补发（同样覆盖重连场景）。
   */
  subscribe(sensorIds) {
    const ids = (Array.isArray(sensorIds) ? sensorIds : [])
      .map(Number)
      .filter((n) => Number.isFinite(n))
    this.sensorIds = ids
    if (ids.length && this.ws && this.ws.readyState === WebSocket.OPEN) {
      this._send({ type: 'subscribe', data: { sensor_ids: ids } })
    }
  }

  /** 取消订阅：同一连接上发送退订消息（不关闭连接，可随后继续订阅其他传感器） */
  unsubscribe(sensorIds) {
    const ids = (Array.isArray(sensorIds) ? sensorIds : [])
      .map(Number)
      .filter((n) => Number.isFinite(n))
    if (ids.length && this.ws && this.ws.readyState === WebSocket.OPEN) {
      this._send({ type: 'unsubscribe', data: { sensor_ids: ids } })
    }
  }

  _createConnection() {
    this._clearTimers()
    if (this.ws) {
      try { this.ws.onopen = this.ws.onmessage = this.ws.onerror = this.ws.onclose = null; this.ws.close() } catch (_) { /* mute */ }
      this.ws = null
    }

    this._setStatus('connecting')

    let ws
    try {
      ws = new WebSocket(this._buildUrl())
    } catch (e) {
      console.error('[传感器WS] 创建连接失败:', e)
      this._setStatus('closed')
      this._scheduleReconnect()
      return
    }
    this.ws = ws

    ws.onopen = () => {
      if (this.ws !== ws) return
      this._setStatus('open')
      this._startHeartbeat()
      // 连接即订阅：onopen 后补发当前订阅的传感器（首次连接与断线重连都走这里）
      if (this.sensorIds.length) {
        this._send({ type: 'subscribe', data: { sensor_ids: this.sensorIds } })
      }
    }

    ws.onmessage = (event) => {
      if (this.ws !== ws) return
      // 收到任何消息都刷新"最后消息时间"，供 60s 断线检测使用
      this._resetStaleTimer()
      let msg
      try {
        msg = JSON.parse(event.data)
      } catch (e) {
        console.warn('[传感器WS] 消息解析失败:', e)
        return
      }
      this._handleMessage(msg)
    }

    ws.onerror = () => {
      if (this.ws !== ws) return
      if (typeof this.onError === 'function') {
        try { this.onError({ type: 'connection_error', message: '传感器推流连接异常' }) } catch (_) { /* mute */ }
      }
    }

    ws.onclose = () => {
      if (this.ws !== ws) return
      this._setStatus('closed')
      this._stopHeartbeat()
      // 手动断开时不自动重连
      if (!this.isManualClose) this._scheduleReconnect()
    }
  }

  /** 按消息枚举分发服务端消息 */
  _handleMessage(msg) {
    const type = msg && msg.type ? String(msg.type).toLowerCase() : ''
    const data = (msg && msg.data) || {}
    switch (type) {
      case 'subscribed':
        // data: { sensor_ids: [1,2,3], message: "成功订阅 3 个传感器" }
        console.log('[传感器WS] 订阅成功:', data.message || '')
        if (typeof this.onSubscribe === 'function') {
          try { this.onSubscribe(data) } catch (e) { console.error('[传感器WS] 订阅回调错误:', e) }
        }
        break
      case 'sensor_data':
        // data: { id: 1, monitor_type: "wind_speed", value: 12.5, unit: "m/s", collect_time }
        if (typeof this.onData === 'function' && data && data.id != null) {
          try { this.onData(data) } catch (e) { console.error('[传感器WS] 数据处理错误:', e) }
        }
        break
      case 'unsubscribed':
        // data: { sensor_ids: [1,2], message: "成功取消订阅 2 个传感器" }
        if (typeof this.onUnsubscribe === 'function') {
          try { this.onUnsubscribe(data) } catch (_) { /* mute */ }
        }
        break
      case 'pong':
        // data: { timestamp }
        if (typeof this.onPong === 'function') {
          try { this.onPong(data) } catch (_) { /* mute */ }
        }
        break
      case 'error':
        // data: { code: 4001, message: "传感器ID不存在: 999" }
        console.warn('[传感器WS] 服务端错误:', data)
        if (typeof this.onError === 'function') {
          try {
            this.onError({ type: 'server_error', code: data.code, message: data.message })
          } catch (_) { /* mute */ }
        }
        break
      default:
        console.warn('[传感器WS] 未知消息:', msg)
    }
  }

  _send(payload) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false
    try {
      this.ws.send(JSON.stringify(payload))
      return true
    } catch (e) {
      console.error('[传感器WS] 发送失败:', e)
      return false
    }
  }

  // ==================== 心跳保活（同一连接每 30s 发送 ping） ====================

  _startHeartbeat() {
    this._stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this._send({ type: 'ping' })
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
      console.warn('[传感器WS] 60s 未收到任何消息，主动断开重连')
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
      this.reconnectTimer = null
      if (!this.isManualClose) this._createConnection()
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

  /** 手动断开（页面卸载时调用；连接关闭后服务端自动清理订阅） */
  disconnect() {
    this.isManualClose = true
    this._clearTimers()
    if (this.ws) {
      try { this.ws.onopen = this.ws.onmessage = this.ws.onerror = this.ws.onclose = null; this.ws.close(1000, 'client disconnect') } catch (_) { /* mute */ }
      this.ws = null
    }
    this.token = ''
    this.sensorIds = []
    this._setStatus('closed')
  }

  /** 清空所有回调（组件卸载时调用，避免回调残留覆盖其他页面） */
  clearCallbacks() {
    this.onStatus = null
    this.onSubscribe = null
    this.onData = null
    this.onUnsubscribe = null
    this.onError = null
    this.onPong = null
  }
}

// 单例导出：整个应用共享一条传感器实时推送连接
const sensorWs = new SensorWs()
export default sensorWs
