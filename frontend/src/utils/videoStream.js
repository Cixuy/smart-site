/**
 * 视频流地址解析工具
 * 根据流地址判定协议类型，供播放器组件与页面复用。
 */

/** 判定流地址协议类型：rtsp | rtmp | hls | flv | http | ws | '' */
export function getProtocolType(url = '') {
  const u = String(url || '').trim()
  if (!u) return ''
  if (/^rtsps?:/i.test(u)) return 'rtsp'
  if (/^rtmp:?/i.test(u) || /^rtmps:/i.test(u)) return 'rtmp'
  if (/\.m3u8(\?|#|$)/i.test(u)) return 'hls'
  if (/\.flv(\?|#|$)/i.test(u)) return 'flv'
  if (/^https?:/i.test(u)) return 'http'
  if (/^wss?:/i.test(u)) return 'ws'
  return ''
}

/** 是否为浏览器无法直接播放的 RTSP/RTMP 流 */
export function isRtspUrl(url = '') {
  const t = getProtocolType(url)
  return t === 'rtsp' || t === 'rtmp'
}

/** 是否为 HLS 流（m3u8） */
export function isHlsUrl(url = '') {
  return getProtocolType(url) === 'hls'
}

/** 是否为 HTTP-FLV 流 */
export function isFlvUrl(url = '') {
  return getProtocolType(url) === 'flv'
}

/** 是否可被浏览器直接播放（排除 RTSP/RTMP/WS，需转码或特殊处理） */
export function isPlayableInBrowser(url = '') {
  const t = getProtocolType(url)
  return !!url && t !== '' && t !== 'rtsp' && t !== 'rtmp' && t !== 'ws'
}

/** 友好协议名 */
export function protocolLabel(url = '') {
  const t = getProtocolType(url)
  const map = { rtsp: 'RTSP', rtmp: 'RTMP', hls: 'HLS', flv: 'HTTP-FLV', http: 'HTTP', ws: 'WebSocket' }
  return map[t] || (url ? '未知' : '')
}

/**
 * 从流地址中提取流 ID（媒体网关转码时使用）。
 * 例：rtsp://192.168.1.100:554/stream1 -> stream1；rtsp://host/live/ch1 -> ch1
 */
export function extractStreamId(url = '') {
  const clean = String(url || '').replace(/[?#].*$/, '').replace(/\/+$/, '')
  const seg = clean.split('/')
  const last = seg[seg.length - 1]
  if (!last) return ''
  return last.replace(/\.(flv|m3u8|mp4|ts)$/i, '') || ''
}
