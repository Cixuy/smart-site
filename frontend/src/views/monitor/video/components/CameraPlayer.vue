<template>
  <div class="camera-player relative w-full h-full bg-black overflow-hidden select-none">
    <video
      ref="videoRef"
      class="w-full h-full"
      :class="fit === 'cover' ? 'object-cover' : 'object-contain'"
      :muted="muted"
      :controls="controls"
      :autoplay="autoplay"
      playsinline
      @playing="onMediaReady"
      @canplay="onMediaReady"
      @waiting="onBufferingStart"
      @error="onVideoError"
    ></video>

    <!-- 加载 / 缓冲中 -->
    <div v-if="status === 'loading' || status === 'buffering'" class="camera-overlay">
      <div class="flex flex-col items-center gap-2">
        <div class="w-7 h-7 rounded-full border-2 border-gray-500 border-t-white animate-spin"></div>
        <p class="text-xs text-gray-400">{{ status === 'loading' ? '正在获取视频流…' : '缓冲中…' }}</p>
        <p v-if="gatewayNote" class="text-[11px] text-yellow-400/80 max-w-[90%] text-center">{{ gatewayNote }}</p>
      </div>
    </div>

    <!-- 摄像头离线 -->
    <div v-else-if="status === 'offline'" class="camera-overlay">
      <div class="flex flex-col items-center gap-2 text-gray-500">
        <el-icon :size="36"><VideoCamera /></el-icon>
        <p class="text-sm">摄像头离线</p>
        <p v-if="streamUrl" class="text-xs opacity-60 px-3 truncate w-full text-center">{{ streamUrl }}</p>
      </div>
    </div>

    <!-- RTSP/RTMP 无法直接播放 -->
    <div v-else-if="status === 'rtsp'" class="camera-overlay">
      <div class="flex flex-col items-center gap-2 text-gray-500 px-4 text-center">
        <el-icon :size="36"><VideoCamera /></el-icon>
        <p class="text-sm">{{ protocolLabel }} 流无法在浏览器中直接播放</p>
        <p class="text-xs opacity-70">需经媒体网关转码为 HLS/FLV 后播放</p>
        <p v-if="streamUrl" class="text-xs opacity-50 px-3 truncate w-full">{{ streamUrl }}</p>
      </div>
    </div>

    <!-- 播放失败 / 无视频流 -->
    <div v-else-if="status === 'error' || status === 'empty'" class="camera-overlay">
      <div class="flex flex-col items-center gap-2 text-gray-500 px-4 text-center">
        <el-icon :size="36"><Warning /></el-icon>
        <p class="text-sm">{{ status === 'error' ? (errorMsg || '视频流加载失败') : '该摄像头暂无可播放的视频流' }}</p>
        <el-button size="small" :icon="Refresh" @click.stop="retry">重试</el-button>
      </div>
    </div>

    <!-- 播放中 LIVE 标识 -->
    <div v-if="showLiveBadge && status === 'playing'" class="absolute top-2 right-2 flex items-center gap-1 bg-black/50 rounded px-1.5 py-0.5">
      <span class="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse"></span>
      <span class="text-[10px] text-white font-medium leading-none">LIVE</span>
    </div>

    <!-- 摄像头名称 -->
    <div v-if="showInfo && camera?.cameraName" class="absolute bottom-2 left-2 text-xs text-white bg-black/50 rounded px-2 py-0.5 truncate max-w-[80%]">
      {{ camera.cameraName }}
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Refresh, VideoCamera, Warning } from '@element-plus/icons-vue'
import Hls from 'hls.js'
import { videoApi } from '@/api'
import { extractStreamId, getProtocolType, isRtspUrl } from '@/utils/videoStream'

// flv.js 为本地 UMD 库（src/js/flv.min.js），动态加载避免影响首屏
const flvjsPromise = import('@/js/flv.min.js')

// flv.js 销毁 SourceBuffer 时，其内部 Web Worker 异步回调偶发访问已移除的 SourceBuffer，
// 抛出 InvalidStateError（已知无害 bug：不影响播放与切换，仅污染控制台）。
// 模块级注册一次全局过滤器，精确匹配并静默该错误，避免 Uncaught (in promise) 噪音。
let flvRejectionGuardInstalled = false
function installFlvRejectionGuard() {
  if (flvRejectionGuardInstalled || typeof window === 'undefined') return
  flvRejectionGuardInstalled = true
  window.addEventListener('unhandledrejection', (ev) => {
    const msg = ev?.reason?.message || String(ev?.reason || '')
    if (msg.includes('SourceBuffer') && msg.includes('removed from the parent media source')) {
      ev.preventDefault()
    }
  })
}
installFlvRejectionGuard()

const props = defineProps({
  /** 摄像头对象（需含 id，用于调用 /api/video/cameras/{id}/stream-url 获取流地址） */
  camera: { type: Object, default: null },
  /** 直接指定流地址（跳过 stream-url 接口），默认由组件调用接口获取 */
  streamUrl: { type: String, default: '' },
  autoplay: { type: Boolean, default: true },
  muted: { type: Boolean, default: true },
  controls: { type: Boolean, default: false },
  /** 是否在画面内叠加摄像头名称 */
  showInfo: { type: Boolean, default: false },
  /** 播放中是否显示 LIVE 角标 */
  showLiveBadge: { type: Boolean, default: true },
  /** 画面裁切方式：cover 填满 / contain 完整显示 */
  fit: { type: String, default: 'contain' },
  /** 播放中断后自动重连间隔(秒)，0 = 不自动重连 */
  reconnectInterval: { type: Number, default: 0 }
})

const emit = defineEmits(['loaded', 'state-change'])

const videoRef = ref(null)
const status = ref('loading') // loading | buffering | playing | offline | rtsp | error | empty
const streamUrl = ref('')
const protocolType = ref('')
const onlineStatus = ref(1)
const errorMsg = ref('')
const gatewayNote = ref('')

let hls = null
let flvPlayer = null
let candidateTimer = null
let reconnectTimer = null
let bufferingTimer = null // 播放中进入缓冲（waiting）后的超时兜底：持续无画面则判中断
let playQueue = [] // 待尝试的候选流（网关转码时可能含 hls/flv 两个候选）
let markedOffline = false // 后端静态标记离线（创建时默认 0，不代表实时状态，仅用于失败时的提示文案）
let destroyed = false

/** 媒体网关（可选）：RTSP/RTMP 摄像头经网关转码为 HLS/FLV 后即可在浏览器播放。
 *  配置 VITE_MEDIA_GATEWAY（如 http://192.168.1.50:8888）后，组件会自动尝试
 *  {gateway}/hls/{streamId}.m3u8 与 {gateway}/live/{streamId}.flv 两种常见转码地址。 */
const gateway = (import.meta.env.VITE_MEDIA_GATEWAY || '').replace(/\/+$/, '')
const gatewayProtocol = (import.meta.env.VITE_MEDIA_GATEWAY_PROTOCOL || 'hls').toLowerCase()

const protocolLabel = computed(() => (protocolType.value || '未知').toUpperCase())

function setStatus(s) {
  status.value = s
  emit('state-change', s)
}

function setError(msg) {
  errorMsg.value = msg || '视频流加载失败'
  setStatus('error')
}

/* ---------------- 生命周期 ---------------- */
// 只监听「值」（cameraId / streamUrl）变化，而非 camera 对象引用：
// 父组件（如数据大屏）受时钟/WS 推流驱动会每秒重渲染并生成新的 camera 对象，
// 若按引用比较会每秒误触发 load() → 重复请求 /stream-url 并反复重建播放器。
watch(
  () => `${props.camera?.id ?? ''}|${props.streamUrl ?? ''}`,
  () => load(),
  { immediate: true }
)

onBeforeUnmount(() => {
  destroyed = true
  clearCandidateTimer()
  clearReconnectTimer()
  clearBufferingTimer()
  cleanup()
})

/* ---------------- 流地址获取（调用 stream-url 接口） ---------------- */
async function load() {
  if (destroyed) return
  clearReconnectTimer()
  clearBufferingTimer()
  cleanup()
  playQueue = []
  setStatus('loading')
  errorMsg.value = ''
  gatewayNote.value = ''

  const cam = props.camera || {}
  let url = props.streamUrl || ''
  let proto = cam.protocolType || ''
  let online = cam.onlineStatus ?? 1

  if (!url) {
    // 核心需求：通过 GET /api/video/cameras/{id}/stream-url 获取摄像头视频流
    if (!cam.id) {
      setStatus('empty')
      return
    }
    try {
      const res = await videoApi.getCameraStreamUrl(cam.id)
      const data = res?.data || {}
      url = data.streamUrl || cam.streamUrl || ''
      proto = data.protocolType || proto
      online = data.onlineStatus ?? online
    } catch (_) {
      // 接口异常时降级使用列表返回的流地址，保证页面可用
      url = cam.streamUrl || ''
      proto = cam.protocolType || proto
      online = cam.onlineStatus ?? online
    }
  }

  streamUrl.value = url
  protocolType.value = proto || (url ? getProtocolType(url).toUpperCase() : '')
  onlineStatus.value = online
  emit('loaded', { cameraId: cam.id, streamUrl: url, protocolType: protocolType.value, onlineStatus: online })

  // 后端 onlineStatus 为创建摄像头时写入的静态字段（默认 0=离线），不代表实时在线状态。
  // 因此即使标记离线也继续尝试播放：播放成功即为在线；流确实无法播放时再按标记提示离线。
  markedOffline = online === 0
  if (!url) {
    setStatus('empty')
    return
  }
  startPlay(url)
}

/* ---------------- 播放调度（支持候选队列 + 失败回退） ---------------- */
function startPlay(url) {
  const type = getProtocolType(url)
  if (isRtspUrl(url)) {
    // RTSP/RTMP：配置了媒体网关则尝试转码播放，否则提示
    if (gateway) {
      const sid = extractStreamId(url) || String(props.camera?.id || '')
      const order = gatewayProtocol === 'flv' ? ['flv', 'hls'] : ['hls', 'flv']
      playQueue = order.map((p) => ({
        url: `${gateway}/${p === 'hls' ? `hls/${sid}.m3u8` : `live/${sid}.flv`}`,
        protocol: p
      }))
      gatewayNote.value = '正在通过媒体网关转码播放…'
      playNext()
    } else {
      setStatus('rtsp')
    }
    return
  }
  playQueue = [{ url, protocol: type === 'hls' ? 'hls' : type === 'flv' ? 'flv' : 'native' }]
  playNext()
}

function playNext() {
  clearCandidateTimer()
  const cand = playQueue.shift()
  if (!cand) {
    if (status.value !== 'playing') setError(errorMsg.value || '视频流加载失败')
    return
  }
  if (cand.protocol === 'hls') initHls(cand.url)
  else if (cand.protocol === 'flv') initFlv(cand.url)
  else initNative(cand.url)
}

/** 当前候选播放失败：若有下一个候选则回退，否则报错 */
function onCandidateFail(msg) {
  clearCandidateTimer()
  cleanup()
  if (playQueue.length) {
    errorMsg.value = msg
    playNext()
  } else if (markedOffline) {
    // 标记离线仅来自后端静态字段（创建摄像头时写入，默认 0=离线），不代表流一定不可用：
    // 仍按 reconnectInterval 自动重连，待 mock/网络恢复后即可自动出画面
    setStatus('offline')
    scheduleReconnect()
  } else {
    setError(msg)
    // 配置了自动重连时（如大屏 reconnectInterval=10），失败后按间隔自动恢复播放，
    // 无需手动反复点重试
    scheduleReconnect()
  }
}

function initHls(url) {
  const el = videoRef.value
  if (!el) {
    onCandidateFail('播放器初始化失败')
    return
  }
  if (Hls.isSupported()) {
    hls = new Hls({ liveSyncDurationCount: 3, enableWorker: true })
    hls.loadSource(url)
    hls.attachMedia(el)
    hls.on(Hls.Events.ERROR, (_evt, data) => {
      if (data?.fatal) {
        const map = { networkError: 'HLS 网络错误', mediaError: 'HLS 媒体解码错误', otherError: 'HLS 播放错误' }
        onCandidateFail(map[data.type] || 'HLS 播放失败')
      }
    })
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      if (props.autoplay) el.play().catch(() => {})
    })
    if (props.autoplay) startCandidateWatchdog()
  } else if (el.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari 原生支持 m3u8
    el.src = url
    if (props.autoplay) el.play().catch(() => {})
    if (props.autoplay) startCandidateWatchdog()
  } else {
    onCandidateFail('当前浏览器不支持 HLS 播放')
  }
}

async function initFlv(url) {
  const el = videoRef.value
  if (!el) {
    onCandidateFail('播放器初始化失败')
    return
  }
  let flvjs
  try {
    // flv.min.js 为 UMD 库，经 Vite 动态 import 后导出结构在不同环境不一致：
    // 可能是 mod.default / mod.default.default / mod.flvjs，浏览器运行时也会挂到 window.flvjs。
    // 逐级兜底解析，避免取到空的模块 namespace 对象。
    const mod = await flvjsPromise
    flvjs = mod?.default?.default || mod?.default || mod?.flvjs || window.flvjs || self.flvjs
  } catch (_) {
    onCandidateFail('FLV 播放器加载失败')
    return
  }
  if (!flvjs || typeof flvjs.isSupported !== 'function') {
    onCandidateFail('FLV 播放器加载失败')
    return
  }
  flvPlayer = flvjs.createPlayer(
    { type: 'flv', isLive: true, url },
    // enableStashBuffer 必须开启：关闭（低延迟）时 MSE 缓冲几乎为零，
    // HTTP-FLV 直播流一旦推流抖动或关键帧间隔较大，video 元素会频繁触发 waiting，
    // 表现为"偶尔闪现画面又卡住、反复缓冲中"。开启后由 flv.js 先行缓冲，播放更稳定。
    { enableStashBuffer: true, autoCleanupSourceBuffer: true }
  )
  // flv.js 错误需分级处理：网络层错误（断流/连接失败）才应判失败；媒体/MSE 类错误
  // （如 SourceBuffer 满、解码暂缓）多为可恢复，直接判失败会导致直播流一抖动就中断。
  // 可恢复错误由 buffering 超时兜底（持续无画面则自动重连）。
  flvPlayer.on(flvjs.Events.ERROR, (errorType) => {
    if (errorType === flvjs.ErrorTypes.NETWORK_ERROR) {
      onCandidateFail('FLV 网络错误')
    }
  })
  flvPlayer.attachMediaElement(el)
  flvPlayer.load()
  if (props.autoplay) {
    try {
      const p = flvPlayer.play()
      if (p && typeof p.catch === 'function') p.catch(() => {})
    } catch (_) { /* ignore */ }
    startCandidateWatchdog()
  }
}

function initNative(url) {
  const el = videoRef.value
  if (!el) {
    onCandidateFail('播放器初始化失败')
    return
  }
  el.src = url
  if (props.autoplay) el.play().catch(() => {})
  if (props.autoplay) startCandidateWatchdog()
}

/** 媒体开始播放/可播放：候选成功 */
function onMediaReady() {
  clearCandidateTimer()
  clearBufferingTimer()
  gatewayNote.value = ''
  if (status.value !== 'playing') setStatus('playing')
}

/** 播放中进入缓冲（waiting）：10s 内未恢复画面视为流中断，自动重连（reconnectInterval>0 时） */
function onBufferingStart() {
  // 初始加载阶段（loading）由 candidateWatchdog 兜底，这里只处理播放中转入的缓冲
  if (status.value !== 'playing') return
  status.value = 'buffering'
  emit('state-change', 'buffering')
  clearBufferingTimer()
  bufferingTimer = setTimeout(() => {
    if (status.value !== 'buffering') return
    setError('视频流中断')
    scheduleReconnect()
  }, 10000)
}

function clearBufferingTimer() {
  if (bufferingTimer) {
    clearTimeout(bufferingTimer)
    bufferingTimer = null
  }
}

/** 视频元素错误事件 */
function onVideoError() {
  if (status.value === 'error') return
  if (playQueue.length) {
    onCandidateFail('视频流加载失败')
    return
  }
  if (status.value === 'playing' || status.value === 'buffering') {
    // 播放中/缓冲中断流：提示并自动重连（reconnectInterval>0 时）
    setError('视频流中断')
    scheduleReconnect()
  } else {
    setError('视频流加载失败')
  }
}

/* ---------------- 看门狗 / 重连 / 清理 ---------------- */
/** 候选播放超时兜底：autoplay 模式下 12s 未出画面视为失败 */
function startCandidateWatchdog() {
  clearCandidateTimer()
  candidateTimer = setTimeout(() => onCandidateFail('连接超时'), 12000)
}

function clearCandidateTimer() {
  if (candidateTimer) {
    clearTimeout(candidateTimer)
    candidateTimer = null
  }
}

function scheduleReconnect() {
  clearReconnectTimer()
  if (props.reconnectInterval > 0) {
    reconnectTimer = setTimeout(() => load(), props.reconnectInterval * 1000)
  }
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function cleanup() {
  if (hls) {
    try { hls.destroy() } catch (_) { /* ignore */ }
    hls = null
  }
  if (flvPlayer) {
    // flv.js 销毁时序问题：其内部 Web Worker（transmuxer）异步回调可能在 destroy 后
    // 仍访问已被移除的 SourceBuffer，抛出 InvalidStateError（已知无害 bug）。
    // 先暂停并停止加载、解绑媒体元素，再销毁，可显著降低该异步回调的触发概率
    try { flvPlayer.pause() } catch (_) { /* ignore */ }
    try { flvPlayer.unload() } catch (_) { /* ignore */ }
    try { flvPlayer.detachMediaElement() } catch (_) { /* ignore */ }
    try { flvPlayer.destroy() } catch (_) { /* ignore */ }
    flvPlayer = null
  }
  const el = videoRef.value
  if (el) {
    el.pause()
    el.removeAttribute('src')
    el.load()
  }
  clearCandidateTimer()
  clearBufferingTimer()
}

function retry() {
  load()
}
</script>

<style scoped>
.camera-player {
  background: #000;
}
.camera-overlay {
  @apply absolute inset-0 flex items-center justify-center bg-black/60;
}
</style>
