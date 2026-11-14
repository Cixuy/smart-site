<template>
  <div class="page-container space-y-4">
    <!-- 头部 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">视频监控</h2>
        <p class="text-sm text-gray-400 mt-1">实时视频监控与录像回放</p>
      </div>
      <div class="flex gap-2 items-center">
        <el-radio-group v-model="viewCols" size="small">
          <el-radio-button :value="2">2列</el-radio-button>
          <el-radio-button :value="3">3列</el-radio-button>
          <el-radio-button :value="4">4列</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Plus" @click="openCameraDialog()">添加摄像头</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :model="query" :inline="true" size="default" @submit.prevent>
        <el-form-item label="摄像头名称">
          <el-input v-model="query.cameraName" placeholder="模糊查询" clearable style="width: 180px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="安装位置">
          <!-- 修复：树节点字段实际为 locationName（与资产-位置管理页一致），此前 label 配成 name 导致节点不显示文字 -->
          <el-tree-select v-model="query.locationId" :data="locationTree"
            :props="{ label: 'locationName', value: 'id', children: 'children' }" check-strictly clearable
            placeholder="全部" style="width: 180px" />
        </el-form-item>
        <el-form-item label="在线状态">
          <el-select v-model="query.onlineStatus" clearable placeholder="全部" style="width: 120px">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联设备">
          <el-select v-model="query.deviceId" filterable clearable placeholder="全部" style="width: 200px">
            <el-option v-for="d in deviceOptions" :key="d.deviceCode" :label="`${d.deviceCode}（${d.deviceName}）`" :value="d.deviceCode" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-tabs v-model="activeTab" class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <!-- 实时监控 -->
      <el-tab-pane label="实时监控" name="live">
        <div v-loading="camLoading">
          <el-empty v-if="!camLoading && cameraList.length === 0" description="暂无摄像头，点击右上角「添加摄像头」进行配置" />
          <div v-else class="grid gap-3" :style="{ gridTemplateColumns: `repeat(${viewCols}, 1fr)` }">
            <div v-for="cam in cameraList" :key="cam.id"
              class="video-card bg-gray-900 rounded-lg overflow-hidden relative cursor-pointer group"
              @click="openPreview(cam)">
              <!-- 视频画面：内部通过 /api/video/cameras/{id}/stream-url 获取流地址并播放 -->
              <div class="relative aspect-video bg-black">
                <div class="absolute inset-0">
                  <CameraPlayer :camera="cam" autoplay muted fit="cover" :show-live-badge="false"
                    @state-change="(s) => onCamStateChange(cam, s)" />
                </div>
                <div class="absolute inset-0 flex items-center justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity bg-black/40 pointer-events-none">
                  <el-icon :size="40" color="#fff"><VideoPlay /></el-icon>
                  <span class="text-white text-sm">点击放大</span>
                </div>
              </div>
              <!-- 顶部状态 -->
              <div class="absolute top-2 left-2 flex gap-2 items-center">
                <el-tag :type="cam.onlineStatus === 1 ? 'success' : 'danger'" size="small" effect="dark">
                  {{ cam.onlineStatusText || (cam.onlineStatus === 1 ? '在线' : '离线') }}
                </el-tag>
                <el-tag v-if="cam.protocolType" size="small" effect="plain" class="!border-gray-600 !text-gray-300 !bg-transparent">{{ cam.protocolType }}</el-tag>
              </div>
              <!-- 操作按钮 -->
              <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <el-button size="small" circle :icon="Edit" @click.stop="openCameraDialog(cam)" />
                <el-popconfirm title="删除该摄像头？" @confirm="delCamera(cam)">
                  <template #reference>
                    <el-button size="small" circle type="danger" :icon="Delete" @click.stop />
                  </template>
                </el-popconfirm>
              </div>
              <!-- 底部信息 -->
              <div class="p-3 bg-gradient-to-t from-black/80 to-transparent">
                <p class="text-white text-sm font-medium truncate">{{ cam.cameraName }}</p>
                <p class="text-gray-300 text-xs mt-0.5 truncate">{{ cam.locationName || '未绑定位置' }}{{ cam.deviceName ? ' · ' + cam.deviceName : '' }}</p>
                <div v-if="(cam.aiCapabilities || []).length" class="flex flex-wrap gap-1 mt-1.5">
                  <el-tag v-for="(cap, i) in cam.aiCapabilities.slice(0, 3)" :key="i" size="small" type="warning" effect="plain" class="!text-yellow-300 !border-yellow-700 !bg-transparent">{{ cap }}</el-tag>
                  <el-tag v-if="cam.aiCapabilities.length > 3" size="small" effect="plain" class="!border-gray-600 !text-gray-400 !bg-transparent">+{{ cam.aiCapabilities.length - 3 }}</el-tag>
                </div>
                <p v-if="cam.lastOnlineTime" class="text-gray-400 text-xs mt-1">最后在线：{{ cam.lastOnlineTime }}</p>
              </div>
            </div>
          </div>
          <div class="flex justify-end mt-4">
            <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
              :page-sizes="[6, 12, 24]" layout="total,sizes,prev,pager,next" @change="fetchCameras" />
          </div>
        </div>
      </el-tab-pane>

      <!-- 录像回放 -->
      <el-tab-pane label="录像回放" name="playback">
        <div class="flex gap-4 flex-col md:flex-row">
          <div class="md:w-60 flex-shrink-0">
            <div class="text-sm font-medium mb-2 text-gray-600 flex justify-between items-center">
              <span>摄像头列表</span><span class="text-xs text-gray-400">{{ allCameras.length }}个</span>
            </div>
            <div class="border rounded-lg overflow-hidden max-h-[420px] overflow-y-auto">
              <div v-if="!allCamerasLoading && allCameras.length === 0" class="py-8"><el-empty description="暂无摄像头" :image-size="60" /></div>
              <div v-for="cam in allCameras" :key="cam.id" @click="selectPlayback(cam)"
                class="flex items-center gap-2 px-3 py-2.5 cursor-pointer transition-colors hover:bg-blue-50 border-b border-gray-100"
                :class="selectedCam?.id === cam.id ? 'bg-blue-50' : ''">
                <span class="w-2 h-2 rounded-full flex-shrink-0" :class="cam.onlineStatus === 1 ? 'bg-green-500' : 'bg-gray-300'"></span>
                <span class="text-sm truncate flex-1">{{ cam.cameraName }}</span>
              </div>
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="bg-gray-900 rounded-lg overflow-hidden">
              <div v-if="selectedCam" class="aspect-video bg-black">
                <CameraPlayer :key="selectedCam.id" :camera="selectedCam" controls autoplay show-info @loaded="onPlaybackLoaded" />
              </div>
              <div v-else class="aspect-video flex flex-col items-center justify-center text-gray-500">
                <el-icon :size="64"><VideoPlay /></el-icon>
                <p class="mt-2 text-sm">选择左侧摄像头查看画面</p>
              </div>
            </div>
            <p class="mt-2 text-xs text-gray-400 truncate">
              {{ selectedCam ? `${selectedCam.cameraName} · ${selectedCam.locationName || '未知位置'} · ${playbackUrl || '暂无流地址'}` : '请选择左侧摄像头' }}
            </p>
          </div>
        </div>
      </el-tab-pane>

      <!-- AI 告警截图 -->
      <el-tab-pane label="AI告警截图" name="snapshots">
        <div v-loading="snapLoading">
          <el-empty v-if="!snapLoading && snapList.length === 0" description="暂无 AI 告警截图" />
          <div v-else class="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div v-for="s in snapList" :key="s.id" class="bg-gray-100 rounded-lg overflow-hidden cursor-pointer hover:shadow-md transition-shadow">
              <div class="aspect-video bg-gray-200 flex items-center justify-center overflow-hidden">
                <img v-if="s.imageUrl" :src="s.imageUrl" class="w-full h-full object-cover" :alt="s.cameraName" />
                <el-icon v-else :size="36" color="#bbb"><VideoCamera /></el-icon>
              </div>
              <div class="p-2">
                <div class="flex items-center justify-between gap-1">
                  <el-tag size="small" type="danger" class="!mr-0">{{ s.alarmType }}</el-tag>
                  <span class="text-xs text-gray-400 truncate">{{ s.time }}</span>
                </div>
                <p class="text-xs text-gray-500 mt-1 truncate">{{ s.cameraName }}</p>
              </div>
            </div>
          </div>
          <div class="flex justify-end mt-4">
            <el-pagination v-model:current-page="snapPageNum" v-model:page-size="snapPageSize" :total="snapTotal"
              :page-sizes="[8, 12, 24]" layout="total,sizes,prev,pager,next" @change="fetchSnapshots" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 摄像头编辑弹窗 -->
    <el-dialog v-model="camDialogVisible" :title="isCamEdit ? '编辑摄像头' : '新增摄像头'" width="620px" :close-on-click-modal="false" @closed="resetCamForm">
      <el-form ref="camFormRef" :model="camForm" :rules="camRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="摄像头名称" prop="cameraName">
              <el-input v-model="camForm.cameraName" placeholder="如：工地东门摄像头" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议类型" prop="protocolType">
              <el-select v-model="camForm.protocolType" style="width: 100%">
                <el-option v-for="p in PROTOCOL_TYPES" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装位置" prop="locationId">
              <!-- 修复：树节点字段实际为 locationName，label 配成 name 会显示空/No data -->
              <el-tree-select v-model="camForm.locationId" :data="locationTree"
                :props="{ label: 'locationName', value: 'id', children: 'children' }" check-strictly placeholder="请选择" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 修复：新增摄像头时“设备编码”改为文本输入，而不是从设备台账下拉选择已有设备。
                 后端 createCamera 要求 deviceId 在台账中不存在（requireUniqueDeviceCode 校验），
                 且保存时会自动把该编码登记为一条 MONITOR 类型设备；若下拉选已有设备必然报“deviceId 已存在”。
                 编辑时后端 updateCamera 不支持修改 deviceId，故置为只读。 -->
            <el-form-item label="设备编码" prop="deviceId">
              <el-input v-model="camForm.deviceId" :disabled="isCamEdit"
                placeholder="如：CAM001（唯一编码，保存后自动登记到设备台账）" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="视频流地址" prop="streamUrl">
              <el-input v-model="camForm.streamUrl" placeholder="rtsp:// / rtmp:// 或 http(s)://...m3u8" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="AI 能力">
              <el-select v-model="camForm.aiCapabilities" multiple collapse-tags collapse-tags-tooltip
                placeholder="选择该摄像头具备的 AI 识别能力" style="width: 100%">
                <el-option v-for="cap in AI_CAPABILITIES" :key="cap" :label="cap" :value="cap" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="camForm.remark" type="textarea" :rows="2" placeholder="备注信息（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="camDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="camSubmitting" @click="saveCamera">确定</el-button>
      </template>
    </el-dialog>

    <!-- 实时预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewCam?.cameraName || '实时预览'" width="880px" destroy-on-close>
      <div class="bg-gray-900 rounded-lg overflow-hidden">
        <div class="aspect-video bg-black">
          <CameraPlayer v-if="previewCam" :key="previewCam.id" :camera="previewCam" controls autoplay show-info
            reconnect-interval="10" @loaded="onPreviewLoaded" />
        </div>
      </div>
      <div class="mt-3 flex items-center justify-between gap-2 flex-wrap">
        <p class="text-xs text-gray-500 break-all">流地址：<span class="text-gray-400">{{ previewStreamUrl || '—' }}</span></p>
        <el-button size="small" :icon="CopyDocument" @click="copyStreamUrl">复制流地址</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete, VideoCamera, VideoPlay, CopyDocument } from '@element-plus/icons-vue'
import { videoApi, assetApi } from '@/api'
import CameraPlayer from './components/CameraPlayer.vue'

/* ---------------- 常量 ---------------- */
const PROTOCOL_TYPES = ['RTSP', 'RTMP', 'HLS']
const AI_CAPABILITIES = ['安全帽检测', '反光衣检测', '烟火检测', '吸烟检测', '区域入侵', '越界检测', '人员聚集', '车辆违停']
const DEFAULT_FORM = () => ({
  id: null,
  cameraName: '',
  deviceId: '',
  locationId: null,
  streamUrl: '',
  protocolType: 'RTSP',
  aiCapabilities: [],
  remark: ''
})

/* ---------------- 状态 ---------------- */
const activeTab = ref('live')
const viewCols = ref(3)
const camLoading = ref(false)
const cameraList = ref([])
const allCameras = ref([])
const allCamerasLoading = ref(false)
const query = reactive({ cameraName: '', locationId: null, onlineStatus: null, deviceId: '' })
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const locationTree = ref([])
const deviceOptions = ref([])

// 摄像头编辑弹窗
const camDialogVisible = ref(false)
const isCamEdit = ref(false)
const camSubmitting = ref(false)
const camFormRef = ref(null)
const camForm = reactive(DEFAULT_FORM())
const camRules = {
  cameraName: [{ required: true, message: '请输入摄像头名称', trigger: 'blur' }],
  // 修复：设备编码改为必填校验，与后端 createCamera 的 deviceId 非空校验保持一致
  deviceId: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  locationId: [{ required: true, message: '请选择安装位置', trigger: 'change' }],
  streamUrl: [{ required: true, message: '请输入视频流地址', trigger: 'blur' }],
  protocolType: [{ required: true, message: '请选择协议类型', trigger: 'change' }]
}

// 实时预览
const previewVisible = ref(false)
const previewCam = ref(null)
const previewStreamUrl = ref('')

// 录像回放
const selectedCam = ref(null)
const playbackUrl = ref('')

// AI 告警截图
const snapLoading = ref(false)
const snapList = ref([])
const snapPageNum = ref(1)
const snapPageSize = ref(12)
const snapTotal = ref(0)

/* ---------------- 摄像头列表 ---------------- */
async function fetchCameras() {
  camLoading.value = true
  try {
    const params = {}
    if (query.cameraName) params.cameraName = query.cameraName
    if (query.locationId !== null && query.locationId !== undefined) params.locationId = query.locationId
    if (query.onlineStatus !== null && query.onlineStatus !== '' && query.onlineStatus !== undefined) params.onlineStatus = query.onlineStatus
    if (query.deviceId) params.deviceId = query.deviceId
    const res = await videoApi.getCameras({ ...params, pageNum: pageNum.value, pageSize: pageSize.value })
    cameraList.value = res?.data?.records ?? res?.data?.list ?? []
    total.value = res?.data?.total ?? cameraList.value.length
  } catch (_) {
    cameraList.value = []
  } finally {
    camLoading.value = false
  }
}

function handleQuery() {
  pageNum.value = 1
  fetchCameras()
}

async function fetchAllCameras() {
  allCamerasLoading.value = true
  try {
    const pageSize = 100
    let pageNum = 1
    let fetched = []
    let pageTotal = 0
    do {
      const params = {}
      if (query.cameraName) params.cameraName = query.cameraName
      if (query.locationId !== null && query.locationId !== undefined) params.locationId = query.locationId
      if (query.onlineStatus !== null && query.onlineStatus !== '' && query.onlineStatus !== undefined) params.onlineStatus = query.onlineStatus
      if (query.deviceId) params.deviceId = query.deviceId
      const res = await videoApi.getCameras({ ...params, pageNum, pageSize })
      const records = res?.data?.records ?? res?.data?.list ?? []
      fetched = fetched.concat(records)
      pageTotal = res?.data?.total ?? records.length
      pageNum += 1
    } while (fetched.length < pageTotal)
    allCameras.value = fetched
  } catch (_) {
    allCameras.value = []
  } finally {
    allCamerasLoading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { cameraName: '', locationId: null, onlineStatus: null, deviceId: '' })
  pageNum.value = 1
  fetchCameras()
}

/* ---------------- 摄像头编辑 ---------------- */
function openCameraDialog(row) {
  isCamEdit.value = !!row
  Object.assign(camForm, row ? {
    id: row.id,
    cameraName: row.cameraName || '',
    deviceId: row.deviceId || '',
    locationId: row.locationId ?? null,
    streamUrl: row.streamUrl || '',
    protocolType: row.protocolType || 'RTSP',
    aiCapabilities: row.aiCapabilities ? [...row.aiCapabilities] : [],
    remark: row.remark || ''
  } : DEFAULT_FORM())
  camDialogVisible.value = true
}

function resetCamForm() {
  camFormRef.value?.clearValidate()
  Object.assign(camForm, DEFAULT_FORM())
}

async function saveCamera() {
  const valid = await camFormRef.value.validate().catch(() => false)
  if (!valid) return
  camSubmitting.value = true
  try {
    if (isCamEdit.value) {
      // 更新接口仅接收：cameraName / streamUrl / aiCapabilities / remark
      await videoApi.updateCamera(camForm.id, {
        cameraName: camForm.cameraName,
        streamUrl: camForm.streamUrl,
        aiCapabilities: camForm.aiCapabilities,
        remark: camForm.remark
      })
    } else {
      await videoApi.createCamera({
        cameraName: camForm.cameraName,
        deviceId: camForm.deviceId || undefined,
        locationId: camForm.locationId,
        streamUrl: camForm.streamUrl,
        protocolType: camForm.protocolType,
        aiCapabilities: camForm.aiCapabilities,
        remark: camForm.remark || undefined
      })
    }
    ElMessage.success(isCamEdit.value ? '修改成功' : '新增成功')
    camDialogVisible.value = false
    fetchCameras()
  } catch (_) {
    /* 错误由拦截器统一提示 */
  } finally {
    camSubmitting.value = false
  }
}

async function delCamera(row) {
  try {
    await videoApi.deleteCamera(row.id)
    ElMessage.success('删除成功')
    // 删除的是当前页最后一条时回退一页，避免停留在空页
    if (cameraList.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
    fetchCameras()
  } catch (_) {
    /* ignore */
  }
}

/* ---------------- 实时预览 ---------------- */
/**
 * 以实际播放结果校正摄像头的在线状态。
 * 后端 onlineStatus 为创建时写入的静态字段（默认 0=离线），不代表实时状态，
 * 因此以播放器能否真正出画面为准：playing → 在线，失败/离线 → 离线。
 */
function onCamStateChange(cam, s) {
  if (!cam) return
  if (s === 'playing') {
    cam.onlineStatus = 1
    cam.onlineStatusText = '在线'
  } else if (s === 'offline' || s === 'error') {
    cam.onlineStatus = 0
    cam.onlineStatusText = '离线'
  }
}

function openPreview(cam) {
  previewCam.value = cam
  previewStreamUrl.value = cam.streamUrl || ''
  previewVisible.value = true
}

/** CameraPlayer 内部通过 stream-url 接口拿到流地址后回调，用于展示/复制 */
function onPreviewLoaded({ streamUrl }) {
  previewStreamUrl.value = streamUrl || ''
}

async function copyStreamUrl() {
  if (!previewStreamUrl.value) return
  try {
    await navigator.clipboard.writeText(previewStreamUrl.value)
    ElMessage.success('流地址已复制')
  } catch (_) {
    ElMessage.error('复制失败，请手动复制')
  }
}

/* ---------------- 录像回放 ---------------- */
function selectPlayback(cam) {
  selectedCam.value = cam
  playbackUrl.value = cam.streamUrl || ''
}

function onPlaybackLoaded({ streamUrl }) {
  playbackUrl.value = streamUrl || ''
}

/* ---------------- AI 告警截图 ---------------- */
async function fetchSnapshots() {
  snapLoading.value = true
  try {
    const res = await videoApi.getAiSnapshots({ pageNum: snapPageNum.value, pageSize: snapPageSize.value })
    const list = res?.data?.records ?? res?.data?.list ?? (Array.isArray(res?.data) ? res.data : [])
    snapList.value = (list || []).map(s => ({
      id: s.id,
      imageUrl: s.imageUrl || s.snapshotUrl || s.url || '',
      cameraName: s.cameraName || s.camera?.cameraName || '未知摄像头',
      alarmType: s.alarmType || s.alertType || s.eventType || 'AI告警',
      time: s.createTime || s.captureTime || s.time || ''
    }))
    snapTotal.value = res?.data?.total ?? snapList.value.length
  } catch (_) {
    snapList.value = []
  } finally {
    snapLoading.value = false
  }
}

/* ---------------- 初始化 ---------------- */
async function loadLocationTree() {
  try {
    // 修复：后端 /api/asset/location/tree 返回结构为 { code, data: { tree: [...] } }，
    // 需取 res.data.tree；此前误用 Array.isArray(res.data) 判断（data 实为对象），
    // 导致位置树始终为空、新增摄像头弹窗的「安装位置」下拉显示 No data。
    const res = await assetApi.getLocationTree({ status: 1 })
    locationTree.value = res?.data?.tree ?? []
  } catch (_) {
    locationTree.value = []
  }
}

async function loadDeviceOptions() {
  try {
    const res = await assetApi.getDevicePage({ pageNum: 1, pageSize: 999 })
    deviceOptions.value = res?.data?.records ?? res?.data?.list ?? []
  } catch (_) {
    deviceOptions.value = []
  }
}

onMounted(() => {
  loadLocationTree()
  loadDeviceOptions()
  fetchCameras()
  fetchAllCameras()
  fetchSnapshots()
})

watch(activeTab, () => {
  if (activeTab.value === 'playback') fetchAllCameras()
})
</script>

<style scoped>
.video-card { min-height: 160px; transition: transform 0.2s; }
.video-card:hover { transform: scale(1.02); }
.page-container { padding: 0; }
</style>
