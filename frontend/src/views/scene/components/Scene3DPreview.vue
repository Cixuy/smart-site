<template>
  <div ref="container" class="scene3d-preview relative w-full h-full overflow-hidden">
    <!-- 2026-08-15 修复：空状态判断追加 !templateLoading —— 设备模板（如内置 tdcf.glb）加载期间
         显示"模型加载中..."而非误导性的"暂无模型内容"（此前 22MB 模板加载的几秒里会误显示空态） -->
    <div v-if="!hasSource && !hasDevices && !loading && !templateLoading && !error" class="absolute inset-0 z-10 flex flex-col items-center justify-center text-gray-400 gap-2">
      <el-icon :size="40"><Box /></el-icon>
      <!-- 2026-08-14 需求调整：上传功能已注释，提示文案不再引导用户上传 -->
      <p class="text-sm">暂无模型内容</p>
    </div>
    <!-- 加载中：底图模型（loading）或设备模板（templateLoading）任一在加载均显示 -->
    <div v-if="loading || templateLoading" class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-black/30 text-white">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <p class="text-sm">模型加载中...</p>
    </div>
    <!-- 加载失败 -->
    <div v-if="error" class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-black/40 text-white">
      <el-icon :size="32"><WarningFilled /></el-icon>
      <p class="text-sm px-4 text-center">{{ error }}</p>
      <!-- 2026-08-14 需求调整：上传功能已注释，失败提示改为检查内置模型资源 -->
      <p class="text-xs text-gray-300">可尝试刷新页面或检查内置模型资源</p>
    </div>
    <!-- 操作提示 -->
    <div class="absolute top-2 right-3 z-10 text-[11px] text-white/60 bg-black/30 px-2 py-1 rounded">左键旋转 · 右键平移 · 滚轮缩放</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

const props = defineProps({
  /** 服务端模型地址（如 /files/models/tdcf.glb） */
  modelUrl: { type: String, default: '' },
  /** 本地文件 ObjectURL，优先于 modelUrl */
  fileUrl: { type: String, default: '' },
  /** 设备坐标绑定列表（iconType: 1塔吊/2升降机/3摄像头/4环境监测/5喷淋） */
  devices: { type: Array, default: () => [] },
  /** 当前选中的模型类型（iconType）：该类型的设备用 deviceModelUrl 模板 clone 渲染，其余类型用程序化网格 */
  modelType: { type: Number, default: 0 },
  /** 真实设备模型模板（如 /files/models/tdcf.glb）：选中类型加载一次后按坐标 clone 多份，不传则用程序化网格 */
  deviceModelUrl: { type: String, default: '' },
  /** 2026-08-18 新增：当前选中的设备编码（deviceCode），对应设备底部显示高亮光环；由父组件（场景详情/告警面板）联动传入 */
  activeDeviceCode: { type: String, default: '' },
  /** 2026-08-18 新增：有告警的设备编码集合（Array<string>）。命中该集合的设备，其头上的名称标签文字改为红色，
      用于在大屏上一眼识别处于告警状态的塔吊；由父组件按告警接口结果推导传入 */
  alarmDeviceCodes: { type: Array, default: () => [] }
})

// 2026-08-18 新增：点击 3D 设备时向外抛事件（payload 含 deviceCode/deviceId/deviceName/iconType；点击空白区域为 null）
const emit = defineEmits(['device-click'])

const container = ref(null)
const loading = ref(false)
const error = ref('')
const hasSource = ref(false)
/** 2026-08-15 新增：设备模板（deviceModelUrl）加载中状态，用于加载期间显示"模型加载中..."而非"暂无模型内容" */
const templateLoading = ref(false)
/** 是否有设备坐标（设备独立于底图渲染，无底图时也能展示设备） */
const hasDevices = computed(() => (props.devices || []).length > 0)

let renderer = null
let scene = null
let camera = null
let controls = null
let currentModel = null
let deviceGroup = null
let deviceTemplate = null
let gridHelper = null
let animationId = 0
let loader = null

// ---- 设备外观配置 ----
const ICON_COLORS = { 1: 0xf59e0b, 2: 0x3b82f6, 3: 0x22c55e, 4: 0xa855f7, 5: 0x06b6d4 }
const ICON_NAMES = { 1: '塔吊', 2: '升降机', 3: '摄像头', 4: '环境监测', 5: '喷淋' }
const LABEL_HEIGHT = { 1: 60, 2: 44, 3: 9, 4: 13, 5: 6 }

function dMat(color, opts = {}) {
  return new THREE.MeshStandardMaterial({ color, roughness: 0.6, metalness: 0.15, ...opts })
}

/** 程序化生成设备实体（非塔吊类型用） */
function buildDeviceMesh(iconType, color) {
  const g = new THREE.Group()
  const main = dMat(color)
  const dark = dMat(0x374151)
  if (iconType === 2) {
    // 升降机：立柱 + 轿厢
    const rail = new THREE.Mesh(new THREE.BoxGeometry(2, 44, 2), dark)
    rail.position.y = 22
    const cage = new THREE.Mesh(new THREE.BoxGeometry(4, 5, 6), main)
    cage.position.set(3, 8, 0)
    ;[rail, cage].forEach(m => g.add(m))
  } else if (iconType === 3) {
    // 摄像头：立杆 + 球机
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.3, 9, 10), dark)
    pole.position.y = 4.5
    const dome = new THREE.Mesh(new THREE.SphereGeometry(1.1, 16, 12), main)
    dome.position.set(1.3, 7.6, 0)
    dome.scale.y = 0.9
    ;[pole, dome].forEach(m => g.add(m))
  } else if (iconType === 4) {
    // 环境监测：立杆 + 设备箱
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.25, 8, 10), dark)
    pole.position.y = 4
    const box = new THREE.Mesh(new THREE.BoxGeometry(2.4, 1.6, 1.2), main)
    box.position.y = 9
    ;[pole, box].forEach(m => g.add(m))
  } else {
    // 喷淋：立杆 + 横管
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.25, 6, 10), dark)
    pole.position.y = 3
    const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 8, 10), main)
    pipe.rotation.z = Math.PI / 2
    pipe.position.y = 5.6
    ;[pole, pipe].forEach(m => g.add(m))
  }
  return g
}

/**
 * 文字标签。
 * 2026-08-18 修改：新增 color 参数（默认白色），供告警设备名称标红使用——
 * 正常设备文字白色、告警设备文字红色，颜色通过 Canvas 纹理直接绘制，无需重建 DOM。
 */
function makeLabel(text, color = '#ffffff') {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 64
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = 'rgba(15,23,42,0.65)'
  ctx.fillRect(0, 0, 256, 64)
  ctx.font = 'bold 30px sans-serif'
  ctx.fillStyle = color
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 128, 32)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }))
  sprite.scale.set(26, 6.5, 1)
  return sprite
}

function clearDevices() {
  // 2026-08-18 新增：设备重建前隐藏选中光环，避免位置失效的旧光环残留
  if (selectionRing) selectionRing.visible = false
  if (deviceGroup) {
    scene.remove(deviceGroup)
    deviceGroup.traverse((o) => {
      // 来自真实模板的 clone 共享几何/材质，不在此释放
      if (o.userData && o.userData.shared) return
      if (o.isMesh || o.isSprite) {
        if (o.geometry) o.geometry.dispose()
        const ms = o.material
        if (Array.isArray(ms)) ms.forEach(m => m && m.dispose())
        else if (ms) {
          if (ms.map) ms.map.dispose()
          ms.dispose()
        }
      }
    })
    deviceGroup = null
  }
}

/** 根据设备坐标列表摆放设备：塔吊用真实模板 clone + 运行时坐标/旋转/缩放 */
function buildDevices() {
  clearDevices()
  if (!props.devices || !props.devices.length) return
  deviceGroup = new THREE.Group()
  props.devices.forEach((d, i) => {
    const type = d.iconType || 1
    const pos = d.position || {}
    const rot = d.rotation || {}
    const sc = d.scale || {}
    const scale = sc.x ?? 1
    const rx = THREE.MathUtils.degToRad(rot.x || 0)
    const ry = THREE.MathUtils.degToRad(rot.y || 0)
    const rz = THREE.MathUtils.degToRad(rot.z || 0)

    let mesh
    if (props.modelType && type === props.modelType && deviceTemplate) {
      // 选中类型（iconType === modelType）的设备：用上传的真实模型模板 clone，共享几何/材质，只改摆放
      mesh = deviceTemplate.clone(true)
      mesh.traverse(o => { o.userData.shared = true })
      mesh.rotation.set(rx, ry, rz)
      mesh.scale.set(scale, sc.y ?? scale, sc.z ?? scale)
      // 底部对齐到配置的 y（贴地）
      const box = new THREE.Box3().setFromObject(mesh)
      mesh.position.set(pos.x ?? 0, (pos.y ?? 0) - box.min.y, pos.z ?? 0)
    } else {
      // 其他类型设备：程序化网格
      mesh = buildDeviceMesh(type, ICON_COLORS[type] || 0xffffff)
      mesh.position.set(pos.x ?? 0, pos.y ?? 0, pos.z ?? 0)
      mesh.rotation.set(rx, ry, rz)
      mesh.scale.set(scale, sc.y ?? scale, sc.z ?? scale)
    }
    // 2026-08-18 新增：每个设备包一层 wrapper 并登记设备信息（deviceCode/deviceId/deviceName/iconType），
    // 供 raycast 点击命中后向上识别设备、以及选中高亮使用；mesh 与标签都挂在 wrapper 下
    const wrapper = new THREE.Group()
    wrapper.userData.deviceCode = d.deviceCode ?? null
    wrapper.userData.deviceId = d.deviceId ?? null
    wrapper.userData.deviceName = d.deviceName || ''
    wrapper.userData.iconType = type
    wrapper.add(mesh)

    // 2026-08-18 修复：标签直接显示台账设备名称，不再拼接序号(i+1)，保证模型内名称与设备台账/坐标表格一致
    // 2026-08-18 新增：deviceCode 命中 alarmDeviceCodes 的设备（处于告警状态）名称文字改为红色，
    // 让大屏上能一眼识别"哪个塔吊在报警"；未告警设备保持白色
    const alarmed = (props.alarmDeviceCodes || []).some(c => String(c) === String(d.deviceCode))
    const label = makeLabel(`${d.deviceName || ICON_NAMES[type] || '设备'}`, alarmed ? '#ef4444' : '#ffffff')
    label.position.set(pos.x ?? 0, (pos.y ?? 0) + (LABEL_HEIGHT[type] ?? 10) * (sc.y ?? scale), pos.z ?? 0)
    wrapper.add(label)

    deviceGroup.add(wrapper)
  })
  scene.add(deviceGroup)
  // 2026-08-18 新增：设备重建后恢复选中高亮（坐标/模板变化时保持选中态，右侧告警面板联动不丢）
  setActiveDevice(activeCode)
}

// ===== 2026-08-18 新增：设备点击选中与高亮（3D 塔吊 <-> 右侧告警面板双向联动） =====
/** 当前选中设备编码（内部状态，与 props.activeDeviceCode 同步） */
let activeCode = ''
/** 选中高亮光环：设备底部青色圆环，选中后持续旋转 + 呼吸闪烁 */
let selectionRing = null
/** 拖拽判定：记录 pointerdown 坐标，位移超过 5px 视为旋转/平移，不触发选中 */
let pointerDownX = 0
let pointerDownY = 0

/** 从射线命中的对象向上查找设备 wrapper（其 userData.deviceCode 非空即设备根节点） */
function findDeviceRoot(obj) {
  let cur = obj
  while (cur) {
    if (cur.userData && cur.userData.deviceCode != null) return cur
    cur = cur.parent
  }
  return null
}

/**
 * 设置选中设备：在设备底部显示/隐藏高亮光环。
 * 说明：设备 mesh 多来自模板 clone（共享几何/材质），不能直接改材质颜色，
 * 故用独立 RingGeometry 光环跟随设备坐标实现高亮，安全且不影响共享资源。
 */
function setActiveDevice(code) {
  activeCode = code || ''
  if (!scene || !deviceGroup) return
  if (!selectionRing) {
    selectionRing = new THREE.Mesh(
      new THREE.RingGeometry(9, 11.5, 48),
      new THREE.MeshBasicMaterial({ color: 0x22d3ee, transparent: true, opacity: 0.9, side: THREE.DoubleSide, depthWrite: false })
    )
    selectionRing.rotation.x = -Math.PI / 2
    selectionRing.position.y = 0.4
    scene.add(selectionRing)
  }
  const dev = (props.devices || []).find(d => d.deviceCode != null && String(d.deviceCode) === String(activeCode))
  if (!dev || !activeCode) {
    selectionRing.visible = false
    return
  }
  const p = dev.position || {}
  selectionRing.position.x = p.x ?? 0
  selectionRing.position.z = p.z ?? 0
  selectionRing.visible = true
}

/** pointerdown：记录按下位置，用于区分点击与拖拽 */
function onPointerDown(e) {
  pointerDownX = e.clientX
  pointerDownY = e.clientY
}

/** pointerup：位移小（<=5px）视为点击，发射射线命中设备后向外抛 device-click 事件 */
function onPointerUp(e) {
  const dx = e.clientX - pointerDownX
  const dy = e.clientY - pointerDownY
  if (dx * dx + dy * dy > 25) return // 拖拽操作，忽略
  handleDevicePick(e)
}

/** 拾取点击位置对应的设备：命中设备则 emit 设备信息，命中空白则 emit null（父组件据此取消选中） */
function handleDevicePick(e) {
  if (!renderer || !deviceGroup || !camera) return
  const rect = renderer.domElement.getBoundingClientRect()
  const ndc = new THREE.Vector2(
    ((e.clientX - rect.left) / rect.width) * 2 - 1,
    -((e.clientY - rect.top) / rect.height) * 2 + 1
  )
  const raycaster = new THREE.Raycaster()
  raycaster.setFromCamera(ndc, camera)
  const hits = raycaster.intersectObjects(deviceGroup.children, true)
  for (const hit of hits) {
    const root = findDeviceRoot(hit.object)
    if (root && root.userData.deviceCode != null) {
      emit('device-click', {
        deviceCode: root.userData.deviceCode,
        deviceId: root.userData.deviceId,
        deviceName: root.userData.deviceName,
        iconType: root.userData.iconType
      })
      return
    }
  }
  emit('device-click', null)
}

/** 加载真实设备模型模板（上传的模型），供选中类型（iconType === modelType）的设备 clone 使用 */
async function loadDeviceTemplate(url) {
  // 2026-08-15 修复：模板加载期间置 templateLoading=true（覆盖下载+解析全程），
  // 避免 22MB 内置模型 tdcf.glb 加载的几秒内误显示"暂无模型内容"
  templateLoading.value = true
  try {
    if (deviceTemplate) {
      deviceTemplate.traverse((o) => {
        if (o.isMesh) {
          if (o.geometry) o.geometry.dispose()
          const m = o.material
          if (Array.isArray(m)) m.forEach(x => x && x.dispose())
          else if (m) { if (m.map) m.map.dispose(); m.dispose() }
        }
      })
      deviceTemplate = null
    }
    if (!url) { buildDevices(); return }
    try {
      const gltf = await loader.loadAsync(url)
      deviceTemplate = gltf.scene
    } catch (_) {
      deviceTemplate = null
    }
    buildDevices()
    buildGrid()
    frameView()
  } finally {
    templateLoading.value = false
  }
}

function init() {
  const el = container.value
  const width = el.clientWidth || 600
  const height = el.clientHeight || 480

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  el.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x141e30)

  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 5000)
  camera.position.set(60, 40, 60)

  // 灯光
  const ambient = new THREE.AmbientLight(0xffffff, 0.7)
  scene.add(ambient)
  const dir = new THREE.DirectionalLight(0xffffff, 1.2)
  dir.position.set(80, 120, 60)
  dir.castShadow = true
  scene.add(dir)
  const fill = new THREE.DirectionalLight(0x8ab4ff, 0.5)
  fill.position.set(-60, 40, -80)
  scene.add(fill)

  // 地面网格：按设备/模型范围动态生成（见 buildGrid），保证所有设备都在网格线之内
  buildGrid()

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.maxDistance = 2000

  // 2026-08-18 新增：绑定设备点击拾取（pointerdown/up 组合用于区分拖拽与点击）
  renderer.domElement.addEventListener('pointerdown', onPointerDown)
  renderer.domElement.addEventListener('pointerup', onPointerUp)

  animate()
}

function animate() {
  animationId = requestAnimationFrame(animate)
  if (controls) controls.update()
  // 2026-08-18 新增：选中光环旋转 + 呼吸闪烁，让选中设备更醒目
  if (selectionRing && selectionRing.visible) {
    selectionRing.rotation.z += 0.03
    selectionRing.material.opacity = 0.55 + 0.4 * (0.5 + 0.5 * Math.sin(Date.now() / 160))
  }
  if (renderer) renderer.render(scene, camera)
}

function clearModel() {
  if (currentModel) {
    scene.remove(currentModel)
    currentModel.traverse((obj) => {
      if (obj.isMesh) {
        if (obj.geometry) obj.geometry.dispose()
        const mat = obj.material
        if (Array.isArray(mat)) mat.forEach((m) => m && m.dispose())
        else if (mat) mat.dispose()
      }
    })
    currentModel = null
  }
}

function frameModel(model) {
  const box = new THREE.Box3().setFromObject(model)
  // 设备实体也纳入取景范围
  if (deviceGroup) box.union(new THREE.Box3().setFromObject(deviceGroup))
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  const radius = Math.max(size.x, size.y, size.z) / 2 || 10
  camera.position.set(center.x + radius * 2.2, center.y + radius * 1.6, center.z + radius * 2.2)
  controls.target.copy(center)
  controls.update()
}

/** 计算当前场景需要覆盖的范围（底图模型与全部设备坐标取最大），网格据此动态生成 */
function computeExtentRadius() {
  let max = 60 // 默认至少覆盖 60 半径，避免空场景网格过小
  if (currentModel) {
    const box = new THREE.Box3().setFromObject(currentModel)
    const s = box.getSize(new THREE.Vector3())
    max = Math.max(max, s.x / 2, s.z / 2)
  }
  ;(props.devices || []).forEach((d) => {
    const p = d.position || {}
    max = Math.max(max, Math.abs(p.x || 0), Math.abs(p.z || 0))
  })
  return max
}

/** 按设备/模型坐标范围动态生成地面网格（外扩 30 再向上取整到 20 的倍数），保证所有设备都在网格线之内 */
function buildGrid() {
  if (!scene) return
  if (gridHelper) {
    scene.remove(gridHelper)
    gridHelper.geometry.dispose()
    gridHelper.material.dispose()
    gridHelper = null
  }
  const radius = computeExtentRadius()
  const size = Math.max(120, Math.ceil(((radius + 30) * 2) / 20) * 20)
  const divisions = Math.round(size / 20)
  gridHelper = new THREE.GridHelper(size, divisions, 0x64748b, 0x334155)
  gridHelper.position.y = -0.01
  scene.add(gridHelper)
}

/** 取景：有底图模型时以模型为中心取景；无底图时以设备坐标包围盒为中心，保证设备在视野内 */
function frameView() {
  if (currentModel) { frameModel(currentModel); return }
  if (deviceGroup) {
    const box = new THREE.Box3().setFromObject(deviceGroup)
    if (!box.isEmpty()) {
      const size = box.getSize(new THREE.Vector3())
      const center = box.getCenter(new THREE.Vector3())
      const radius = Math.max(size.x, size.y, size.z) / 2 || 10
      camera.position.set(center.x + radius * 2.2, center.y + radius * 1.6, center.z + radius * 2.2)
      controls.target.copy(center)
      controls.update()
      return
    }
  }
  // 无任何内容：回到默认视角
  camera.position.set(60, 40, 60)
  controls.target.set(0, 0, 0)
  controls.update()
}

async function loadModel(url) {
  clearModel()
  clearDevices()
  error.value = ''
  if (!url) {
    loading.value = false
    hasSource.value = false
    buildDevices()
    buildGrid()
    frameView()
    return
  }
  hasSource.value = true
  loading.value = true
  try {
    const gltf = await loader.loadAsync(url)
    const model = gltf.scene
    scene.add(model)
    currentModel = model
    buildDevices()
    buildGrid()
    frameView()
    loading.value = false
  } catch (e) {
    loading.value = false
    error.value = '模型加载失败：文件无法访问或格式不受支持'
  }
}

function onResize() {
  if (!renderer || !container.value) return
  const width = container.value.clientWidth
  const height = container.value.clientHeight
  if (!width || !height) return
  renderer.setSize(width, height)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

watch(
  () => [props.modelUrl, props.fileUrl],
  ([url, file]) => {
    const target = file || url
    loadModel(target)
  }
)

// 设备坐标变化：重建设备、重建网格并重新取景（保证新设备在网格与视野内）
watch(
  () => props.devices,
  () => {
    buildDevices()
    buildGrid()
    frameView()
  },
  { deep: true }
)

// 设备模型模板变化：重新加载模板并重建设备
watch(
  () => props.deviceModelUrl,
  (url) => {
    loadDeviceTemplate(url)
  }
)

// 选中模型类型变化：决定哪些设备走真实模板渲染，需重建设备、重建网格并重新取景
watch(
  () => props.modelType,
  () => {
    buildDevices()
    buildGrid()
    frameView()
  }
)

// 2026-08-18 新增：外部选中设备变化（如点击右侧告警面板）→ 同步 3D 场景中的高亮光环
watch(
  () => props.activeDeviceCode,
  (code) => {
    setActiveDevice(code)
  }
)

// 2026-08-18 新增：告警设备集合变化（如告警接口加载完成/刷新）→ 重建设备，
// 使名称标签文字颜色即时更新（红=告警，白=正常）；重建设备后选中光环由 buildDevices 自动恢复
watch(
  () => props.alarmDeviceCodes,
  () => {
    buildDevices()
  },
  { deep: true }
)

onMounted(async () => {
  await nextTick()
  try {
    init()
  } catch (e) {
    error.value = '当前环境不支持 WebGL，无法渲染 3D 模型'
    return
  }
  loader = new GLTFLoader()
  window.addEventListener('resize', onResize)
  const target = props.fileUrl || props.modelUrl
  if (target) loadModel(target)
  if (props.deviceModelUrl) loadDeviceTemplate(props.deviceModelUrl)
})

onUnmounted(() => {
  cancelAnimationFrame(animationId)
  // 2026-08-18 新增：移除点击拾取监听，并释放选中光环资源
  if (renderer && renderer.domElement) {
    renderer.domElement.removeEventListener('pointerdown', onPointerDown)
    renderer.domElement.removeEventListener('pointerup', onPointerUp)
  }
  if (selectionRing) {
    scene.remove(selectionRing)
    selectionRing.geometry.dispose()
    selectionRing.material.dispose()
    selectionRing = null
  }
  if (deviceTemplate) {
    deviceTemplate.traverse((o) => {
      if (o.isMesh) {
        if (o.geometry) o.geometry.dispose()
        const m = o.material
        if (Array.isArray(m)) m.forEach(x => x && x.dispose())
        else if (m) { if (m.map) m.map.dispose(); m.dispose() }
      }
    })
    deviceTemplate = null
  }
  if (gridHelper) {
    scene.remove(gridHelper)
    gridHelper.geometry.dispose()
    gridHelper.material.dispose()
    gridHelper = null
  }
  if (controls) controls.dispose()
  if (renderer) {
    renderer.dispose()
    if (container.value && renderer.domElement) {
      container.value.removeChild(renderer.domElement)
    }
    renderer = null
  }
  window.removeEventListener('resize', onResize)
})
</script>
