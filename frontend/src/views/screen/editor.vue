<template>
  <!-- 单根包裹：保持与 layout 中页面容器（h-full）的高度链一致，避免绝对定位元素定位基准漂移 -->
  <div class="h-full">
  <!-- ==================== 预览模式（全屏数据大屏） ==================== -->
  <div
    v-if="previewMode"
    v-loading="previewLoading"
    element-loading-background="rgba(2,6,23,0.6)"
    class="screen-preview-root relative overflow-hidden"
  >
    <!-- 顶部标题栏 -->
    <div class="preview-header absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-6 py-3">
      <div class="flex items-center gap-3">
        <span class="text-xl font-bold tracking-widest text-slate-100">{{ screenName || '数据大屏' }}</span>
        <el-tag v-if="isDefault" type="warning" size="small" effect="dark">默认</el-tag>
      </div>
      <div class="flex items-center gap-3">
        <span class="flex items-center gap-1.5 text-xs text-red-400"><span class="live-dot"></span>LIVE</span>
        <span class="text-sm text-slate-400 tabular-nums">{{ serverTimeText }}</span>
        <el-button size="small" :icon="FullScreen" circle @click="toggleFullscreen" title="全屏" />
        <el-button size="small" :icon="Close" circle @click="exitPreview" title="退出预览" />
      </div>
    </div>

    <!-- 大屏主体（1920x1080 设计稿等比缩放） -->
    <div class="absolute inset-0 flex items-center justify-center" :style="{ paddingTop: '56px' }">
      <div :style="previewFrameStyle" class="shrink-0">
        <div
          class="relative"
          style="width: 1920px; height: 1080px"
          :style="{ transform: `scale(${previewScale})`, transformOrigin: 'top left' }"
        >
          <!-- 组件渲染 -->
          <div v-for="comp in layoutConfig.components" :key="comp.id" class="absolute" :style="compStyle(comp)">
            <!-- 设备状态 -->
            <div class="preview-panel h-full" v-if="comp.type === 'device_status'">
              <div class="panel-title">{{ compTitle(comp) }}</div>
              <div class="grid grid-cols-2 gap-2">
                <div class="stat-card" style="--c: #22c55e">
                  <div class="stat-num">{{ realtime.deviceStatus?.onlineCount ?? '-' }}</div>
                  <div class="stat-label">在线设备</div>
                </div>
                <div class="stat-card" style="--c: #94a3b8">
                  <div class="stat-num">{{ realtime.deviceStatus?.offlineCount ?? '-' }}</div>
                  <div class="stat-label">离线设备</div>
                </div>
              </div>
            </div>

            <!-- 环境监测（数据源：传感器实时推流，与视频监控环境监测页同源一致） -->
            <div class="preview-panel h-full" v-else-if="comp.type === 'environment'">
              <div class="panel-title">{{ compTitle(comp) }}</div>
              <div class="env-list">
                <div v-for="p in envList(comp)" :key="p.pointKey" class="env-point-card">
                  <div class="flex items-center gap-2">
                    <span class="equip-name">{{ p.pointName }}</span>
                    <span class="status-dot" :class="statusDotClass(p.status)"></span>
                    <span class="text-xs" :class="statusTextClass(p.status)">{{ p.statusText || '--' }}</span>
                  </div>
                  <div class="grid grid-cols-2 gap-x-3 mt-1">
                    <div v-for="s in p.sensors" :key="s.id" class="metric-row">
                      <span class="text-xs text-slate-400">{{ s.label }}</span>
                      <span class="text-sm font-semibold" :class="envMetricClass(s)">{{ fmtNum(s.value) }}<span class="text-[10px] text-slate-400 ml-0.5">{{ s.unit }}</span></span>
                    </div>
                  </div>
                </div>
                <el-empty v-if="envList(comp).length === 0" description="暂无环境数据" :image-size="48" />
              </div>
            </div>

            <!-- 告警滚动 -->
            <div class="preview-panel h-full" v-else-if="comp.type === 'alarm_scroll'">
              <div class="panel-title">{{ compTitle(comp) }}</div>
              <div class="alarm-scroll-area">
                <div class="alarm-scroll-track">
                  <!-- 只渲染真实告警数据（不复制），避免出现"两条相同信息"的观感；
                       超高时由 startAlarmScroll 的 JS 定时滚动向上循环 -->
                  <div v-for="(a, i) in alarmList(comp)" :key="i" class="alarm-item">
                    <span class="level-tag" :style="levelStyle(a.alarmLevel)">{{ levelLabel(a.alarmLevel) }}</span>
                    <span class="alarm-type">{{ a.alarmType }}</span>
                    <span class="alarm-device">{{ a.deviceName }}</span>
                    <span class="alarm-time">{{ formatTime(a.alarmTime) }}</span>
                  </div>
                </div>
                <el-empty v-if="alarmList(comp).length === 0" description="暂无告警" :image-size="48" />
              </div>
            </div>

            <!-- 塔吊状态（数据源：传感器实时推流，与视频监控塔吊监控页同源一致） -->
            <div class="preview-panel h-full" v-else-if="comp.type === 'tower_crane'">
              <div class="panel-title">{{ compTitle(comp) }}</div>
              <div class="equip-list">
                <div v-for="d in realtime.towerCraneStatus || []" :key="d.deviceId" class="equip-card">
                  <div class="flex items-center gap-2">
                    <span class="equip-name">{{ d.deviceName || d.deviceId }}</span>
                    <span class="status-dot" :class="statusDotClass(d.status)"></span>
                    <span class="text-xs" :class="statusTextClass(d.status)">{{ d.statusText || '--' }}</span>
                  </div>
                  <div class="grid grid-cols-2 gap-x-3 mt-1">
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">起重量</span>
                      <span class="text-sm font-semibold" :class="metricClass(d)">{{ fmtNum(d.loadWeight) }}<span class="text-[10px] text-slate-400 ml-0.5">t</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">力矩</span>
                      <span class="text-sm font-semibold" :class="metricClass(d)">{{ fmtNum(d.moment) }}<span class="text-[10px] text-slate-400 ml-0.5">kN·m</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">幅度</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.amplitude) }}<span class="text-[10px] text-slate-400 ml-0.5">m</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">风速</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.windSpeed) }}<span class="text-[10px] text-slate-400 ml-0.5">m/s</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">高度</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.height) }}<span class="text-[10px] text-slate-400 ml-0.5">m</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">回转角</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.slewAngle) }}<span class="text-[10px] text-slate-400 ml-0.5">°</span></span>
                    </div>
                  </div>
                </div>
                <el-empty v-if="(realtime.towerCraneStatus || []).length === 0" description="暂无塔吊数据" :image-size="48" />
              </div>
            </div>

            <!-- 升降机状态（数据源：传感器实时推流，与视频监控升降机监控页同源一致） -->
            <div class="preview-panel h-full" v-else-if="comp.type === 'hoist'">
              <div class="panel-title">{{ compTitle(comp) }}</div>
              <div class="equip-list">
                <div v-for="d in realtime.hoistStatus || []" :key="d.deviceId" class="equip-card">
                  <div class="flex items-center gap-2">
                    <span class="equip-name">{{ d.deviceName || d.deviceId }}</span>
                    <span class="status-dot" :class="statusDotClass(d.status)"></span>
                    <span class="text-xs" :class="statusTextClass(d.status)">{{ d.statusText || '--' }}</span>
                  </div>
                  <div class="grid grid-cols-2 gap-x-3 mt-1">
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">载重</span>
                      <span class="text-sm font-semibold" :class="metricClass(d)">{{ fmtNum(d.loadWeight) }}<span class="text-[10px] text-slate-400 ml-0.5">kg</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">载重比</span>
                      <span class="text-sm font-semibold" :class="metricClass(d)">{{ fmtNum(d.loadPercent) }}<span class="text-[10px] text-slate-400 ml-0.5">%</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">高度</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.height) }}<span class="text-[10px] text-slate-400 ml-0.5">m</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">倾斜角</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.tiltAngle) }}<span class="text-[10px] text-slate-400 ml-0.5">°</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">人数</span>
                      <span class="text-sm font-semibold text-sky-300">{{ d.passengerCount ?? '-' }}<span class="text-[10px] text-slate-400 ml-0.5">人</span></span>
                    </div>
                    <div class="metric-row">
                      <span class="text-xs text-slate-400">风速</span>
                      <span class="text-sm font-semibold text-sky-300">{{ fmtNum(d.windSpeed) }}<span class="text-[10px] text-slate-400 ml-0.5">m/s</span></span>
                    </div>
                  </div>
                </div>
                <el-empty v-if="(realtime.hoistStatus || []).length === 0" description="暂无升降机数据" :image-size="48" />
              </div>
            </div>

            <!-- 告警趋势 -->
            <div class="preview-panel h-full" v-else-if="comp.type === 'alarm_trend'">
              <div class="panel-title">{{ compTitle(comp) }}</div>
              <div :ref="el => setChartRef(comp.id, el)" class="flex-1"></div>
            </div>

            <!-- 视频轮播 -->
            <div class="preview-panel h-full video-panel" v-else-if="comp.type === 'video'">
              <div class="relative w-full h-full bg-black">
                <!-- 复用实时监控页 CameraPlayer：经 /api/video/cameras/{id}/stream-url 取流 + RTSP 网关转码，
                     与"实时监控-视频监控"播放能力完全一致；加载中/离线/无流等状态由组件自行展示 -->
                <CameraPlayer
                  v-if="videoState[comp.id]?.item"
                  :key="videoState[comp.id]?.item?.cameraId"
                  :camera="videoCameraOf(comp)"
                  autoplay
                  muted
                  fit="cover"
                  :show-live-badge="false"
                  :reconnect-interval="10"
                />
                <div class="absolute top-2 left-2 text-xs text-slate-300 bg-black/50 rounded px-2 py-0.5">
                  {{ videoState[comp.id]?.item?.cameraName || compTitle(comp) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ==================== 编辑模式 ==================== -->
  <div v-else class="h-full flex flex-col bg-gray-100">
    <!-- 工具栏 -->
    <header class="h-14 flex-shrink-0 bg-white border-b border-gray-100 flex items-center gap-3 px-4">
      <el-button :icon="Back" circle @click="goBack" title="返回" />
      <div class="font-semibold text-gray-700">大屏编辑器</div>
      <el-divider direction="vertical" />
      <el-input v-model="screenName" placeholder="输入大屏名称" class="w-64" maxlength="50" clearable />
      <el-tag v-if="screenId" type="info" effect="plain">ID: {{ screenId }}</el-tag>
      <el-tag v-else type="warning" effect="plain">新建</el-tag>
      <div class="flex-1"></div>
      <el-button :icon="Timer" @click="openRotationDialog">视频轮播</el-button>
      <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存</el-button>
      <el-button type="success" :icon="View" @click="enterPreview()">预览</el-button>
    </header>

    <div class="flex-1 flex overflow-hidden">
      <!-- 左侧：组件库 -->
      <aside class="w-56 flex-shrink-0 bg-white border-r border-gray-100 p-4 overflow-y-auto">
        <h3 class="text-sm font-semibold text-gray-700 mb-1">组件库</h3>
        <p class="text-xs text-gray-400 mb-3">点击添加组件到画布，拖动调整位置</p>
        <div class="space-y-2">
          <div v-for="t in COMPONENT_TYPES" :key="t.type" class="palette-item" @click="addComp(t.type)">
            <el-icon :size="18" color="#3b82f6"><component :is="t.icon" /></el-icon>
            <span class="text-sm text-gray-600">{{ t.label }}</span>
            <span class="ml-auto text-[10px] text-gray-400">{{ t.w }}×{{ t.h }}</span>
          </div>
        </div>
      </aside>

      <!-- 中间：画布 -->
      <main
        ref="canvasWrapRef"
        v-loading="loading"
        class="flex-1 relative overflow-auto bg-gray-200/70 p-6"
        @click="selectedId = null"
      >
        <div class="mx-auto" :style="canvasWrapStyle">
          <div
            class="relative bg-slate-50 rounded-lg shadow-inner border border-gray-200"
            style="width: 1920px; height: 1080px"
            :style="{ transform: `scale(${canvasScale})`, transformOrigin: 'top left' }"
          >
            <!-- 网格背景 -->
            <div class="absolute inset-0 rounded-lg" :style="gridBgStyle"></div>

            <!-- 组件 -->
            <div
              v-for="comp in layoutConfig.components"
              :key="comp.id"
              class="canvas-comp absolute rounded-md border-2"
              :class="comp.id === selectedId ? 'border-blue-500 shadow-lg' : 'border-transparent hover:border-blue-300'"
              :style="compStyle(comp)"
              @mousedown.stop="startDrag($event, comp)"
              @click.stop="selectComp(comp)"
            >
              <div class="comp-header">
                <span class="truncate">{{ compTitle(comp) }}</span>
                <span class="comp-type text-[10px] text-gray-400 flex-shrink-0">{{ compTypeLabel(comp.type) }}</span>
                <span class="comp-actions flex-shrink-0">
                  <el-icon @click.stop="duplicateComp(comp)"><CopyDocument /></el-icon>
                  <el-icon class="text-red-500" @click.stop="removeComp(comp)"><Delete /></el-icon>
                </span>
              </div>
              <div class="comp-body">
                <el-icon :size="28" color="#93c5fd"><component :is="compIcon(comp.type)" /></el-icon>
                <span class="text-xs text-gray-400">{{ compTypeLabel(comp.type) }}</span>
              </div>
              <div class="resize-handle" @mousedown.stop="startResize($event, comp)" @click.stop></div>
            </div>

            <el-empty
              v-if="layoutConfig.components.length === 0"
              description="从左侧组件库点击添加组件"
              :image-size="72"
              class="absolute inset-0"
            />
          </div>
        </div>
      </main>

      <!-- 右侧：属性面板 -->
      <aside class="w-72 flex-shrink-0 bg-white border-l border-gray-100 p-4 overflow-y-auto">
        <template v-if="selectedComp">
          <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1">
            <el-icon><Setting /></el-icon> 组件属性
          </h3>
          <el-form label-position="top" size="small">
            <el-form-item label="组件类型">
              <el-input :model-value="compTypeLabel(selectedComp.type)" disabled />
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="selectedComp.props.title" maxlength="30" />
            </el-form-item>
            <el-form-item label="副标题">
              <el-input v-model="selectedComp.props.subtitle" maxlength="30" />
            </el-form-item>
            <el-form-item v-if="selectedComp.type === 'alarm_scroll'" label="显示条数">
              <el-input-number v-model="selectedComp.props.maxItems" :min="3" :max="20" />
            </el-form-item>
            <el-form-item label="位置">
              <div class="flex gap-2 w-full">
                <el-input :model-value="`x: ${selectedComp.x}`" disabled />
                <el-input :model-value="`y: ${selectedComp.y}`" disabled />
              </div>
            </el-form-item>
            <el-form-item label="尺寸">
              <div class="flex gap-2 w-full">
                <el-input :model-value="`宽: ${selectedComp.w}`" disabled />
                <el-input :model-value="`高: ${selectedComp.h}`" disabled />
              </div>
            </el-form-item>
            <div class="flex gap-2">
              <el-button size="small" type="primary" plain :icon="CopyDocument" class="flex-1" @click="duplicateComp(selectedComp)">复制</el-button>
              <el-button size="small" type="danger" plain :icon="Delete" class="flex-1" @click="removeComp(selectedComp)">删除</el-button>
            </div>
          </el-form>
        </template>
        <template v-else>
          <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1">
            <el-icon><Setting /></el-icon> 画布设置
          </h3>
          <el-form label-position="top" size="small">
            <el-form-item label="网格列数">
              <el-input-number v-model="layoutConfig.gridCols" :min="6" :max="24" />
            </el-form-item>
            <el-form-item label="行高 (px)">
              <el-input-number v-model="layoutConfig.rowHeight" :min="40" :max="160" :step="10" />
            </el-form-item>
            <el-form-item label="刷新间隔 (秒)">
              <el-input-number v-model="refreshInterval" :min="3" :max="3600" />
            </el-form-item>
            <el-form-item label="设为默认大屏">
              <el-switch v-model="isDefault" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-alert
              type="info"
              :closable="false"
              title="提示：点击画布中的组件可编辑属性；拖拽右下角手柄可调整大小。"
              class="mt-2"
            />
          </el-form>
        </template>
      </aside>
    </div>
  </div>

  <!-- ==================== 视频轮播配置对话框 ==================== -->
  <el-dialog v-model="rotationVisible" title="视频轮播配置" width="760px" destroy-on-close>
    <div class="mb-3 flex items-end gap-2 flex-wrap">
      <el-select v-model="rotationForm.cameraId" placeholder="选择摄像头" filterable clearable style="width: 220px">
        <el-option v-for="c in cameraOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <div>
        <div class="text-xs text-gray-400 mb-1">播放顺序</div>
        <el-input-number v-model="rotationForm.displayOrder" :min="1" :max="99" size="small" />
      </div>
      <div>
        <div class="text-xs text-gray-400 mb-1">时长(秒)</div>
        <el-input-number v-model="rotationForm.duration" :min="3" :max="300" size="small" />
      </div>
      <el-button type="primary" :icon="Plus" @click="addRotation">添加</el-button>
    </div>
    <el-table :data="rotationList" v-loading="rotationLoading" size="small" border max-height="360">
      <el-table-column type="index" label="#" width="48" align="center" />
      <el-table-column prop="cameraName" label="摄像头" min-width="130" show-overflow-tooltip />
      <el-table-column prop="streamUrl" label="流地址" min-width="200" show-overflow-tooltip />
      <el-table-column label="顺序" width="120" align="center">
        <template #default="{ row }">
          <el-input-number v-model="row.displayOrder" :min="1" :max="99" size="small" controls-position="right" />
        </template>
      </el-table-column>
      <el-table-column label="时长(秒)" width="130" align="center">
        <template #default="{ row }">
          <el-input-number v-model="row.duration" :min="3" :max="300" size="small" controls-position="right" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center">
        <template #default="{ row }">
          <el-button type="danger" size="small" :icon="Delete" circle @click="removeRotation(row)" />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无轮播配置，请选择摄像头后点击「添加」" :image-size="60" />
      </template>
    </el-table>
    <template #footer>
      <el-button @click="rotationVisible = false">取消</el-button>
      <el-button type="primary" :loading="rotationSaving" @click="saveRotations">保存配置</el-button>
    </template>
  </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import {
  Back, Bell, Box, Check, Close, CopyDocument, Cpu, Delete, FullScreen, Odometer,
  Plus, Setting, Sunny, Timer, TrendCharts, VideoCamera, View
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import CameraPlayer from '@/views/monitor/video/components/CameraPlayer.vue'
import screenApi from '@/api/screen'
import alarmApi from '@/api/alarm'
import assetApi from '@/api/asset'
import sensorApi from '@/api/sensor'
import sensorWs from '@/utils/sensorWs'
import { getToken } from '@/utils/auth'
import { environmentApi, videoApi } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useStore()

/* ---------------- 组件类型注册表 ---------------- */
const COMPONENT_TYPES = [
  { type: 'device_status', label: '设备状态', icon: Cpu, w: 4, h: 3, title: '设备状态总览' },
  { type: 'environment', label: '环境监测', icon: Sunny, w: 4, h: 3, title: '环境实时指标' },
  { type: 'alarm_scroll', label: '告警滚动', icon: Bell, w: 4, h: 5, title: '最新未处置告警' },
  { type: 'tower_crane', label: '塔吊状态', icon: Odometer, w: 6, h: 4, title: '塔吊运行状态' },
  { type: 'hoist', label: '升降机状态', icon: Box, w: 6, h: 4, title: '升降机运行状态' },
  { type: 'alarm_trend', label: '告警趋势', icon: TrendCharts, w: 6, h: 4, title: '近7天告警趋势' },
  { type: 'video', label: '视频轮播', icon: VideoCamera, w: 6, h: 4, title: '视频监控' }
]
const TYPE_MAP = Object.fromEntries(COMPONENT_TYPES.map(t => [t.type, t]))

/* ---------------- 基础状态 ---------------- */
const screenId = ref(route.query.id ? Number(route.query.id) : null)
const screenName = ref('')
const refreshInterval = ref(5)
const isDefault = ref(false)
const status = ref(1)
const previewMode = ref(route.query.preview === '1' || route.query.preview === 1)
const saving = ref(false)
const loading = ref(false)
const layoutConfig = reactive({ gridCols: 12, rowHeight: 80, components: [] })
const selectedId = ref(null)

const selectedComp = computed(() => layoutConfig.components.find(c => c.id === selectedId.value) || null)

/* ---------------- 组件工具 ---------------- */
function compTitle(comp) { return comp.props?.title || TYPE_MAP[comp.type]?.title || comp.type }
function compTypeLabel(type) { return TYPE_MAP[type]?.label || type }
function compIcon(type) { return TYPE_MAP[type]?.icon || Odometer }

function compStyle(comp) {
  const colW = 1920 / layoutConfig.gridCols
  const rowH = layoutConfig.rowHeight
  return {
    left: `${comp.x * colW}px`,
    top: `${comp.y * rowH}px`,
    width: `${comp.w * colW}px`,
    height: `${comp.h * rowH}px`
  }
}

/* ---------------- 画布缩放 ---------------- */
const canvasWrapRef = ref(null)
const canvasScale = ref(1)
const canvasWrapStyle = computed(() => ({
  width: `${1920 * canvasScale.value}px`,
  height: `${1080 * canvasScale.value}px`
}))
const gridBgStyle = computed(() => {
  const colW = 1920 / layoutConfig.gridCols
  const rowH = layoutConfig.rowHeight
  return {
    backgroundImage:
      'linear-gradient(to right, rgba(59,130,246,0.12) 1px, transparent 1px), linear-gradient(to bottom, rgba(59,130,246,0.12) 1px, transparent 1px)',
    backgroundSize: `${colW}px ${rowH}px`
  }
})

function calcCanvasScale() {
  const wrap = canvasWrapRef.value
  if (!wrap) return
  const cw = wrap.clientWidth - 48
  const ch = wrap.clientHeight - 48
  canvasScale.value = Math.min(cw / 1920, ch / 1080, 1)
}

/* ---------------- 组件增删改 ---------------- */
function collides(comp, x, y, w, h) {
  return layoutConfig.components.some(c =>
    c.id !== comp.id && x < c.x + c.w && x + w > c.x && y < c.y + c.h && y + h > c.y
  )
}

function findFreeSpot(w, h) {
  for (let y = 0; y < 60; y++) {
    for (let x = 0; x <= layoutConfig.gridCols - w; x++) {
      const hit = layoutConfig.components.some(c =>
        x < c.x + c.w && x + w > c.x && y < c.y + c.h && y + h > c.y
      )
      if (!hit) return { x, y }
    }
  }
  return { x: 0, y: 0 }
}

function addComp(type) {
  const meta = TYPE_MAP[type]
  const pos = findFreeSpot(meta.w, meta.h)
  const comp = reactive({
    id: `${type}_${Date.now()}`,
    type,
    x: pos.x,
    y: pos.y,
    w: meta.w,
    h: meta.h,
    props: { title: meta.title, subtitle: '' }
  })
  layoutConfig.components.push(comp)
  selectComp(comp)
}

function selectComp(comp) { selectedId.value = comp.id }

function duplicateComp(comp) {
  const idx = layoutConfig.components.findIndex(c => c.id === comp.id)
  if (idx === -1) return
  const copy = reactive({
    ...comp,
    id: `${comp.type}_${Date.now()}`,
    props: { ...comp.props },
    x: Math.min(comp.x + 1, layoutConfig.gridCols - comp.w),
    y: comp.y + 1
  })
  layoutConfig.components.splice(idx + 1, 0, copy)
  selectComp(copy)
}

function removeComp(comp) {
  const idx = layoutConfig.components.findIndex(c => c.id === comp.id)
  if (idx > -1) layoutConfig.components.splice(idx, 1)
  if (selectedId.value === comp.id) selectedId.value = null
}

/* ---------------- 拖拽移动 ---------------- */
let dragState = null
function startDrag(e, comp) {
  selectComp(comp)
  e.preventDefault()
  dragState = { comp, startX: comp.x, startY: comp.y, mouseX: e.clientX, mouseY: e.clientY }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}
function onDragMove(e) {
  if (!dragState) return
  const { comp, startX, startY, mouseX, mouseY } = dragState
  const colW = 1920 / layoutConfig.gridCols
  const rowH = layoutConfig.rowHeight
  const dx = (e.clientX - mouseX) / canvasScale.value / colW
  const dy = (e.clientY - mouseY) / canvasScale.value / rowH
  const nx = Math.max(0, Math.min(Math.round(startX + dx), layoutConfig.gridCols - comp.w))
  const ny = Math.max(0, Math.round(startY + dy))
  if ((nx !== comp.x || ny !== comp.y) && !collides(comp, nx, ny, comp.w, comp.h)) {
    comp.x = nx
    comp.y = ny
  }
}
function onDragEnd() {
  dragState = null
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
}

/* ---------------- 拖拽缩放 ---------------- */
let resizeState = null
function startResize(e, comp) {
  selectComp(comp)
  e.preventDefault()
  e.stopPropagation()
  resizeState = { comp, startW: comp.w, startH: comp.h, mouseX: e.clientX, mouseY: e.clientY }
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
}
function onResizeMove(e) {
  if (!resizeState) return
  const { comp, startW, startH, mouseX, mouseY } = resizeState
  const colW = 1920 / layoutConfig.gridCols
  const rowH = layoutConfig.rowHeight
  const nw = Math.max(1, Math.min(Math.round(startW + (e.clientX - mouseX) / canvasScale.value / colW), layoutConfig.gridCols - comp.x))
  const nh = Math.max(1, Math.min(Math.round(startH + (e.clientY - mouseY) / canvasScale.value / rowH), 60))
  comp.w = nw
  comp.h = nh
}
function onResizeEnd() {
  resizeState = null
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
}

watch(() => layoutConfig.gridCols, v => {
  layoutConfig.components.forEach(c => {
    if (c.x + c.w > v) c.x = Math.max(0, v - c.w)
  })
})

/* ---------------- 详情加载与保存 ---------------- */
async function loadDetail() {
  loading.value = true
  try {
    const res = await screenApi.getConfigDetail(screenId.value)
    const data = res?.data
    if (data) {
      screenName.value = data.screenName || ''
      refreshInterval.value = data.refreshInterval ?? 5
      isDefault.value = !!data.isDefault
      status.value = data.status ?? 1
      if (data.layoutConfig) {
        layoutConfig.gridCols = data.layoutConfig.gridCols || 12
        layoutConfig.rowHeight = data.layoutConfig.rowHeight || 80
        layoutConfig.components.splice(0, layoutConfig.components.length)
        ;(data.layoutConfig.components || []).forEach(c => {
          layoutConfig.components.push(reactive({
            id: c.id || `${c.type || 'comp'}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
            type: c.type,
            x: c.x ?? 0,
            y: c.y ?? 0,
            w: c.w ?? 4,
            h: c.h ?? 3,
            props: reactive(c.props && typeof c.props === 'object' ? { ...c.props } : {})
          }))
        })
      }
    }
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!screenName.value.trim()) { ElMessage.warning('请输入大屏名称'); return }
  saving.value = true
  try {
    const payload = {
      screenName: screenName.value.trim(),
      layoutConfig: {
        gridCols: layoutConfig.gridCols,
        rowHeight: layoutConfig.rowHeight,
        components: layoutConfig.components.map(c => ({
          id: c.id, type: c.type, x: c.x, y: c.y, w: c.w, h: c.h, props: { ...c.props }
        }))
      },
      refreshInterval: refreshInterval.value,
      isDefault: isDefault.value,
      status: status.value
    }
    if (screenId.value) {
      await screenApi.updateConfig(screenId.value, payload)
    } else {
      const res = await screenApi.createConfig(payload)
      screenId.value = res?.data?.id
      router.replace({ query: { id: screenId.value } })
    }
    ElMessage.success('保存成功')
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    saving.value = false
  }
}

/* ---------------- 实时数据 ---------------- */
const previewLoading = ref(false)
const realtime = reactive({
  deviceStatus: null,
  alarmScroll: [],
  towerCraneStatus: [],
  hoistStatus: [],
  alarmTrend: [],
  videoRotations: [],
  videoCameras: [], // 与实时监控页同源的摄像头列表（getCameras 分页拉全），用于轮播配置为空时的兜底显示与流信息校准
  serverTime: ''
})

const nowTick = ref(Date.now())
let serverOffsetMs = 0

const serverTimeText = computed(() => {
  void nowTick.value
  const d = new Date(Date.now() + serverOffsetMs)
  if (isNaN(d.getTime())) return ''
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
})

async function fetchRealtime() {
  if (!screenId.value) return
  // 1. 大屏通用数据：设备状态/视频轮播/服务端时间（塔吊/升降机由 WS 推流驱动）
  //    失败仅影响自身模块，不阻塞告警数据刷新
  try {
    const res = await screenApi.getRealtimeData(screenId.value)
    const data = res?.data
    if (data) {
      realtime.deviceStatus = data.deviceStatus ?? null
      // 环境实时指标由 initEnvRealtime() 的传感器 WS 推流驱动（与视频监控环境监测页同源），
      // 不再读取数据库表 environmentMetrics
      realtime.videoRotations = data.videoRotations ?? []
      realtime.serverTime = data.serverTime ?? ''
      if (data.serverTime) {
        const st = new Date(data.serverTime).getTime()
        if (!isNaN(st)) serverOffsetMs = st - Date.now()
      }
    }
  } catch (e) {
    /* 大屏通用接口失败不影响其他模块 */
  }
  // 2. 告警滚动/告警趋势与告警管理模块同源（biz_alarm_record），独立刷新，
  //    保证即使通用接口失败，告警数据与告警管理模块仍保持一致
  await fetchAlarmRealtime()
  nextTick(() => {
    renderCharts()
    layoutConfig.components.filter(c => c.type === 'video').forEach(startVideoRotation)
  })
}

/* ============ 塔吊/升降机实时数据（与视频监控同源：传感器实时推流） ============ */
// 设备台账类型：塔吊 typeId=2、升降机 typeId=8（与视频监控塔吊/升降机页一致）
const EQUIP_KINDS = [
  { key: 'tower', typeId: 2, listField: 'towerCraneStatus' },
  { key: 'hoist', typeId: 8, listField: 'hoistStatus' }
]
// 传感器类型(小写) → 设备记录字段（与监控页 SensorRealtime 的类型口径一致）
const TOWER_FIELD_MAP = {
  load_weight: 'loadWeight', moment: 'moment', amplitude: 'amplitude',
  wind_speed: 'windSpeed', height: 'height', slew_angle: 'slewAngle'
}
const HOIST_FIELD_MAP = {
  load_weight: 'loadWeight', load_percent: 'loadPercent', height: 'height',
  passenger_count: 'passengerCount', wind_speed: 'windSpeed', tilt_angle: 'tiltAngle'
}
const sensorIndex = new Map()       // sensorId → { deviceKey, listField, field }
const deviceSensorMeta = new Map()  // deviceKey → 该设备传感器元数据列表（用于状态判定）
const wsSensorData = reactive({})   // sensorId → WS 推送的最新数据
let wsConnected = false
let subscribedSensorIds = []

// 传感器元数据字段下划线转驼峰（兼容后端返回）
function normalizeMeta(m) {
  if (!m) return m
  const r = {}
  for (const k of Object.keys(m)) {
    r[k.replace(/_([a-z])/g, (_, c) => c.toUpperCase())] = m[k]
  }
  return r
}

function toNum(v) {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}

// 单传感器状态判定（与视频监控 SensorRealtime.sensorState 完全一致）：
// 故障 > 停止 > 离线 > 等待数据 > 报警 > 预警 > 正常
function sensorLevel(meta, data) {
  if (meta.status === 2) return 'alarm'
  if (meta.status === 0) return 'waiting'
  if (!wsConnected) return 'offline'
  if (!data || data.value == null || data.value === '') return 'waiting'
  const v = Number(data.value)
  if (Number.isNaN(v)) return 'waiting'
  const au = toNum(meta.alarmUpper), al = toNum(meta.alarmLower)
  const wu = toNum(meta.warningUpper), wl = toNum(meta.warningLower)
  if ((au != null && v >= au) || (al != null && v <= al)) return 'alarm'
  if ((wu != null && v >= wu) || (wl != null && v <= wl)) return 'warning'
  return 'normal'
}

// 设备级状态汇总：报警 > 预警 > 正常 > 等待数据 > 离线
function computeDeviceState(deviceKey) {
  const metas = deviceSensorMeta.get(deviceKey) || []
  let alarm = 0, warning = 0, normal = 0
  for (const m of metas) {
    const lv = sensorLevel(m, wsSensorData[Number(m.id)])
    if (lv === 'alarm') alarm++
    else if (lv === 'warning') warning++
    else if (lv === 'normal') normal++
  }
  if (alarm > 0) return { level: 'alarm', text: '报警' }
  if (warning > 0) return { level: 'warning', text: '预警' }
  if (normal > 0) return { level: 'normal', text: '正常' }
  if (metas.length) return { level: 'waiting', text: '等待数据' }
  return { level: 'offline', text: '离线' }
}

/* ============ 环境监测实时数据（与视频监控环境监测页同源：传感器实时推流） ============ */
// 环境传感器类型 → 展示名称（与监控页 SensorRealtime.TYPE_META 环境部分口径一致）
const ENV_TYPE_LABEL = {
  TEMP: '温度', TEMPERATURE: '温度', HUMIDITY: '湿度', PM25: 'PM2.5', PM10: 'PM10',
  NOISE: '噪声', WIND: '风速', WIND_SPEED: '风速'
}
const envPointIndex = new Map() // sensorId → { pointKey, meta }（环境传感器归属）
// 监测点列表：[{ pointKey, pointId, pointName, status, statusText, sensors: [{ id, label, unit, value, level }] }]
const envPoints = ref([])

// 监测点级状态汇总：报警 > 预警 > 正常 > 等待数据 > 离线
function computeEnvPointState(point) {
  let alarm = 0, warning = 0, normal = 0
  for (const s of point.sensors) {
    if (s.level === 'alarm') alarm++
    else if (s.level === 'warning') warning++
    else if (s.level === 'normal') normal++
  }
  if (alarm > 0) return { level: 'alarm', text: '报警' }
  if (warning > 0) return { level: 'warning', text: '预警' }
  if (normal > 0) return { level: 'normal', text: '正常' }
  if (point.sensors.length) return { level: 'waiting', text: '等待数据' }
  return { level: 'offline', text: '离线' }
}

// 初始化：拉环境监测点 + 每监测点传感器元数据 + 合并订阅 WS 推流
async function initEnvRealtime() {
  try {
    // 1. 环境监测点（与监控页环境监测一致：GET /api/env-monitor/points/page）
    const res = await environmentApi.getMonitorPoints({ pageNum: 1, pageSize: 100 })
    const list = Array.isArray(res?.data?.list)
      ? res.data.list
      : (Array.isArray(res?.data?.records) ? res.data.records : [])
    const map = new Map()
    list.forEach(p => {
      const id = p.monitorPointId ?? p.id
      if (id == null || map.has(id)) return
      map.set(id, {
        pointKey: `env_${id}`,
        pointId: id,
        pointName: p.pointName || p.name || `监测点${id}`
      })
    })
    const points = [...map.values()]
    envPoints.value = points.map(p => ({
      ...p,
      status: 'waiting', statusText: '等待数据', sensors: []
    }))

    // 2. 逐监测点拉传感器元数据（parentType=2：监测点，与监控页环境监测一致）
    const allIds = []
    for (const p of points) {
      try {
        const sres = await sensorApi.getSensorList({ parentType: 2, parentId: p.pointId, parent_type: 2, parent_id: p.pointId })
        const metas = (Array.isArray(sres?.data) ? sres.data : []).map(normalizeMeta)
        const point = envPoints.value.find(x => x.pointKey === p.pointKey)
        if (!point) continue
        point.sensors = metas.map(m => ({
          id: Number(m.id),
          label: ENV_TYPE_LABEL[String(m.monitorType || '').toUpperCase()] || m.sensorName || String(m.monitorType || ''),
          unit: m.unit || '',
          value: null,
          level: 'waiting'
        }))
        for (const m of metas) {
          if (m.id == null) continue
          const sid = Number(m.id)
          envPointIndex.set(sid, { pointKey: p.pointKey, meta: m })
          allIds.push(sid)
        }
        // 初始化状态：有传感器未收到数据 → 等待数据；无传感器 → 离线
        const st = computeEnvPointState(point)
        point.status = st.level
        point.statusText = st.text
      } catch (_) { /* 单个监测点传感器加载失败不影响其余监测点 */ }
    }

    // 3. 合并订阅：sensorWs.subscribe 会整体覆盖内部 sensorIds（断线重连时按它补订），
    //    必须把环境传感器与设备传感器合并后一次性提交，避免重连后丢失设备订阅
    subscribedSensorIds = [...new Set([...subscribedSensorIds, ...allIds])]
    // 后端单次订阅上限 200（MAX_SUBSCRIBE_IDS），超限整批失败；设备在前、环境在后，超限时优先保设备
    if (allIds.length) sensorWs.subscribe(subscribedSensorIds.slice(0, 200))
  } catch (e) {
    /* 初始化失败不影响其他模块展示 */
  }
}

// WS 推流回调：按传感器 id 归到对应设备/监测点并更新指标/状态
function handleSensorData(data) {
  if (!data || data.id == null) return
  const sid = Number(data.id)
  wsSensorData[sid] = data
  // —— 塔吊/升降机设备传感器 ——
  const idx = sensorIndex.get(sid)
  if (idx) {
    const list = realtime[idx.listField] || []
    const dev = list.find(x => x.deviceKey === idx.deviceKey)
    if (dev) {
      if (idx.field) dev[idx.field] = data.value
      dev.updatedAt = Date.now()
      const st = computeDeviceState(idx.deviceKey)
      dev.status = st.level
      dev.statusText = st.text
    }
    return
  }
  // —— 环境监测点传感器 ——
  const eidx = envPointIndex.get(sid)
  if (!eidx) return
  const point = envPoints.value.find(p => p.pointKey === eidx.pointKey)
  if (!point) return
  const s = point.sensors.find(x => x.id === sid)
  if (s) {
    s.value = data.value
    s.unit = data.unit || s.unit
    s.level = sensorLevel(eidx.meta, data)
  }
  const st = computeEnvPointState(point)
  point.status = st.level
  point.statusText = st.text
}

// 初始化：拉设备台账 + 每台设备传感器元数据 + 订阅 WS 推流（与视频监控同一条推流通道）
async function initSensorRealtime() {
  try {
    const [towerRes, hoistRes] = await Promise.all([
      assetApi.getDevicePage({ typeId: 2, pageSize: 200 }),
      assetApi.getDevicePage({ typeId: 8, pageSize: 200 })
    ])
    const towerDevices = towerRes?.data?.records || []
    const hoistDevices = hoistRes?.data?.records || []

    realtime.towerCraneStatus = towerDevices.map(d => ({
      deviceKey: `tower_${d.id}`, deviceId: d.id,
      deviceName: d.deviceName || d.deviceCode || d.id,
      deviceCode: d.deviceCode || '',
      loadWeight: null, moment: null, amplitude: null,
      windSpeed: null, height: null, slewAngle: null,
      status: 'waiting', statusText: '等待数据', updatedAt: 0
    }))
    realtime.hoistStatus = hoistDevices.map(d => ({
      deviceKey: `hoist_${d.id}`, deviceId: d.id,
      deviceName: d.deviceName || d.deviceCode || d.id,
      deviceCode: d.deviceCode || '',
      loadWeight: null, loadPercent: null, height: null,
      passengerCount: null, windSpeed: null, tiltAngle: null,
      status: 'waiting', statusText: '等待数据', updatedAt: 0
    }))

    const fieldMapByKey = { tower: TOWER_FIELD_MAP, hoist: HOIST_FIELD_MAP }
    const allIds = []
    for (const kind of EQUIP_KINDS) {
      const devices = kind.key === 'tower' ? towerDevices : hoistDevices
      for (const d of devices) {
        try {
          const res = await sensorApi.getSensorList({ parentType: 1, parentId: d.id, parent_type: 1, parent_id: d.id })
          const metas = (Array.isArray(res?.data) ? res.data : []).map(normalizeMeta)
          const deviceKey = `${kind.key}_${d.id}`
          deviceSensorMeta.set(deviceKey, metas)
          for (const m of metas) {
            if (m.id == null) continue
            const sid = Number(m.id)
            const mt = String(m.monitorType || '').toLowerCase()
            sensorIndex.set(sid, {
              deviceKey,
              listField: kind.listField,
              field: fieldMapByKey[kind.key][mt]
            })
            allIds.push(sid)
          }
        } catch (_) { /* 单台设备传感器加载失败不影响其余设备 */ }
      }
    }
    // 合并而非覆盖：initEnvRealtime 可能已把环境传感器并入订阅列表。
    // 若此处用 allIds 直接覆盖，会把环境传感器剔除（WS 订阅是整体覆盖语义，
    // 后到订阅覆盖先到，见后端 SensorRealtimeWebSocketHandler.handleSubscribe），
    // 导致环境面板一直"等待数据"，而塔吊/升降机正常
    subscribedSensorIds = [...new Set([...subscribedSensorIds, ...allIds])]

    const token = getToken()
    if (!token) return
    sensorWs.onStatus = (s) => { wsConnected = s === 'open' }
    sensorWs.onData = handleSensorData
    sensorWs.onError = (info) => {
      if (info?.type === 'server_error' && info?.message) ElMessage.warning(info.message)
    }
    sensorWs.connect(token)
    // 后端单次订阅上限 200（MAX_SUBSCRIBE_IDS），超限会整批失败返回 error 4002；
    // 截断保护：设备传感器在前、环境在后，超限时优先保住设备/塔吊/升降机订阅
    if (subscribedSensorIds.length) sensorWs.subscribe(subscribedSensorIds.slice(0, 200))
  } catch (e) {
    /* 初始化失败不影响其他模块展示 */
  }
}

// 退出预览/卸载时清理 WS 回调与订阅
function destroySensorRealtime() {
  if (subscribedSensorIds.length) sensorWs.unsubscribe(subscribedSensorIds)
  subscribedSensorIds = []
  sensorIndex.clear()
  deviceSensorMeta.clear()
  envPointIndex.clear()
  envPoints.value = []
  Object.keys(wsSensorData).forEach(k => delete wsSensorData[k])
  wsConnected = false
  sensorWs.clearCallbacks()
  sensorWs.disconnect()
}

/* ---------------- 塔吊/升降机模块状态样式 ---------------- */
function statusDotClass(level) {
  const map = { alarm: 'bg-red-500', warning: 'bg-amber-400', normal: 'bg-emerald-400', waiting: 'bg-slate-400', offline: 'bg-slate-600' }
  return map[level] || 'bg-slate-400'
}
function statusTextClass(level) {
  const map = { alarm: 'text-red-400', warning: 'text-amber-400', normal: 'text-emerald-400', waiting: 'text-slate-400', offline: 'text-slate-500' }
  return map[level] || 'text-slate-400'
}
function metricClass(d) {
  if (d.status === 'alarm') return 'text-red-400'
  if (d.status === 'warning') return 'text-amber-400'
  return 'text-sky-300'
}
// 环境传感器指标颜色：报警红 / 预警琥珀 / 正常天空蓝
function envMetricClass(s) {
  if (s.level === 'alarm') return 'text-red-400'
  if (s.level === 'warning') return 'text-amber-400'
  return 'text-sky-300'
}

/* ---------------- 告警实时数据（与告警管理模块同源：biz_alarm_record） ---------------- */
// 最新未处置告警 + 近7天告警趋势，均来自告警管理模块接口（/api/alarms），与大屏旧环境表数据无关
async function fetchAlarmRealtime() {
  try {
    // 1. 最新未处置告警：GET /api/alarms?status=0（与告警管理"未处置"口径一致）
    const page = await alarmApi.getPageList({ pageNum: 1, pageSize: 20, status: 0 })
    const list = page?.data?.records || page?.data?.list || []
    realtime.alarmScroll = list.map(a => ({
      alarmId: a.id,
      alarmLevel: a.alarmLevel === 3 ? 'control' : (a.alarmLevel === 2 ? 'alarm' : 'warning'),
      alarmType: a.alarmTypeText || a.alarmContent || a.alarmTypeCode || '-',
      deviceName: a.deviceName || a.monitorPointName || a.cameraName || '-',
      alarmTime: a.alarmTime,
      status: a.status
    }))
  } catch (_) { /* 失败保留上次数据，不阻塞其他模块 */ }
  try {
    // 2. 近7天告警趋势：GET /api/alarms/statistics/trend（与告警管理统计页同源）
    const end = new Date()
    const start = new Date()
    start.setDate(end.getDate() - 6)
    const fmt = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const trend = await alarmApi.getTrend({
      projectId: store.getters.projectId,
      startDate: fmt(start),
      endDate: fmt(end),
      granularity: 'DAY'
    })
    realtime.alarmTrend = trend?.data?.points || []
  } catch (_) { /* 失败保留上次数据，不阻塞其他模块 */ }
}

/* ---------------- ECharts（告警趋势） ---------------- */
const chartInstances = new Map()
const chartEls = new Map()
function setChartRef(compId, el) {
  if (el) chartEls.set(compId, el)
  else chartEls.delete(compId)
}
function renderCharts() {
  for (const comp of layoutConfig.components) {
    if (comp.type !== 'alarm_trend') continue
    const el = chartEls.get(comp.id)
    if (!el) continue
    let chart = chartInstances.get(comp.id)
    if (!chart) {
      chart = echarts.init(el)
      chartInstances.set(comp.id, chart)
    }
    const points = realtime.alarmTrend || []
    chart.setOption({
      color: ['#38bdf8', '#f59e0b', '#ef4444', '#8b5cf6', '#22c55e'],
      grid: { left: 40, right: 16, top: 24, bottom: 28 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15,23,42,0.9)',
        borderColor: 'rgba(56,189,248,0.4)',
        textStyle: { color: '#e2e8f0', fontSize: 12 }
      },
      legend: { show: false },
      xAxis: {
        type: 'category',
        data: points.map(p => String(p.periodStart || '').slice(0, 10)),
        axisLine: { lineStyle: { color: 'rgba(148,163,184,0.4)' } },
        axisLabel: { color: '#94a3b8', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: 'rgba(148,163,184,0.15)' } },
        axisLabel: { color: '#94a3b8', fontSize: 11 }
      },
      series: [
        { name: '总数', type: 'line', smooth: true, symbolSize: 4, data: points.map(p => p.totalCount || 0), itemStyle: { color: '#38bdf8' }, areaStyle: { color: 'rgba(56,189,248,0.10)' } },
        { name: '预警', type: 'line', smooth: true, symbolSize: 4, data: points.map(p => p.warningCount || 0), itemStyle: { color: '#f59e0b' } },
        { name: '警报', type: 'line', smooth: true, symbolSize: 4, data: points.map(p => p.alarmCount || 0), itemStyle: { color: '#ef4444' } },
        { name: '控制', type: 'line', smooth: true, symbolSize: 4, data: points.map(p => p.controlCount || 0), itemStyle: { color: '#8b5cf6' } },
        { name: '已处置', type: 'line', smooth: true, symbolSize: 4, data: points.map(p => p.resolvedCount || 0), itemStyle: { color: '#22c55e' } }
      ]
    }, true)
  }
}
function disposeCharts() {
  chartInstances.forEach(c => { try { c.dispose() } catch (_) {} })
  chartInstances.clear()
  chartEls.clear()
}

/* ---------------- 视频轮播 ---------------- */
const videoState = reactive({})
const videoTimers = new Map()

/** 轮播配置项映射为 CameraPlayer 需要的 camera（含 streamUrl：与实时监控页传完整 cam 一致，
 *  CameraPlayer 直接使用该地址播放，不再依赖 /stream-url 接口，避免接口异常导致无画面） */
function videoCameraOf(comp) {
  const item = videoState[comp.id]?.item
  if (!item) return null
  // 轮播配置存的字段是 cameraId（非 id）；streamUrl 来自 selectRotationVOs 的 JOIN biz_camera（c.stream_url）
  return {
    id: item.cameraId ?? item.id,
    cameraName: item.cameraName || '',
    streamUrl: item.streamUrl || '',
    protocolType: item.protocolType || '',
    onlineStatus: item.onlineStatus ?? 1,
  }
}

function startVideoRotation(comp) {
  // 每次 fetchRealtime 都会调用本函数：已固定播放中则跳过，避免反复重置画面
  if (videoState[comp.id]) return
  stopVideoRotation(comp.id)
  const cameras = realtime.videoCameras || []
  const conf = realtime.videoRotations || []
  // 1. 有轮播配置：按配置取第一个摄像头（同一摄像头的流地址/协议/在线状态一律以实时监控页同源的
  //    videoCameras 为准，保证画面与实时监控页完全一致），配置中失效的 cameraId 自动丢弃
  // 2. 无轮播配置：回退使用与实时监控页一致的摄像头列表，显示第一个（固定不轮播），
  //    避免"视频监控"面板空白无画面
  let items = []
  if (conf.length) {
    items = conf
      .map(c => {
        const cam = cameras.find(x => x.id === c.cameraId)
        return cam
          ? { ...c, cameraId: cam.id, cameraName: cam.cameraName, streamUrl: cam.streamUrl, protocolType: cam.protocolType, onlineStatus: cam.onlineStatus }
          : null
      })
      .filter(Boolean)
  } else {
    items = cameras.map(c => ({
      cameraId: c.id,
      cameraName: c.cameraName,
      streamUrl: c.streamUrl,
      protocolType: c.protocolType,
      onlineStatus: c.onlineStatus,
      duration: 0
    }))
  }
  if (!items.length) {
    videoState[comp.id] = null
    return
  }
  // 仅去掉轮播播放行为：固定播放第一个摄像头，不再启动定时切换；保留「视频轮播」配置对话框
  // 供编辑排序/调整，但预览/大屏不再自动切播后续摄像头
  const item = items[0]
  videoState[comp.id] = { item, idx: 0 }
}

/** 拉取与实时监控页同源的摄像头列表（getCameras 分页拉全），供视频面板兜底显示与流信息校准 */
async function loadVideoCameras() {
  try {
    realtime.videoCameras = await loadAllCameras()
  } catch (_) {
    realtime.videoCameras = []
  }
}
function stopVideoRotation(compId) {
  const t = videoTimers.get(compId)
  if (t) { clearTimeout(t); videoTimers.delete(compId) }
  delete videoState[compId]
}
function stopAllVideoRotation() {
  videoTimers.forEach(t => clearTimeout(t))
  videoTimers.clear()
  Object.keys(videoState).forEach(k => delete videoState[k])
}

/* ---------------- 预览数据工具 ---------------- */
function alarmList(comp) {
  const max = comp.props?.maxItems ? Number(comp.props.maxItems) : 8
  return (realtime.alarmScroll || []).slice(0, max)
}

/* ---------------- 告警滚动：真实列表 + JS 定时滚动（不复制数据，杜绝"重复信息"观感） ---------------- */
let alarmScrollTimer = null
function startAlarmScroll() {
  stopAlarmScroll()
  alarmScrollTimer = setInterval(() => {
    document.querySelectorAll('.alarm-scroll-area').forEach((area) => {
      const track = area.querySelector('.alarm-scroll-track')
      if (!track) return
      const maxScroll = track.scrollHeight - area.clientHeight
      // 内容不超高：无需滚动（静态完整展示，绝不出现重复）
      if (maxScroll <= 0) { area.scrollTop = 0; return }
      // 每 60ms 推进 maxScroll/50（约 3s 滚完一轮），到底后回顶继续循环
      const step = Math.max(1, Math.round(maxScroll / 50))
      const next = area.scrollTop + step
      area.scrollTop = next >= maxScroll ? 0 : next
    })
  }, 60)
}
function stopAlarmScroll() {
  if (alarmScrollTimer) { clearInterval(alarmScrollTimer); alarmScrollTimer = null }
}
function envList(comp) {
  const max = comp.props?.maxItems ? Number(comp.props.maxItems) : 3
  return (envPoints.value || []).slice(0, max)
}

const ALARM_LEVEL = {
  warning: { label: '预警', color: '#f59e0b' },
  alarm: { label: '警报', color: '#ef4444' },
  control: { label: '控制', color: '#8b5cf6' }
}
function levelLabel(level) { return ALARM_LEVEL[level]?.label || level || '未知' }
function levelStyle(level) {
  const m = ALARM_LEVEL[level] || { color: '#64748b' }
  return { color: m.color, borderColor: m.color, background: `${m.color}22` }
}

/* ---------------- 预览模式 ---------------- */
const previewScale = ref(1)
const previewFrameStyle = computed(() => ({
  width: `${1920 * previewScale.value}px`,
  height: `${1080 * previewScale.value}px`
}))
function calcPreviewScale() {
  previewScale.value = Math.min(window.innerWidth / 1920, (window.innerHeight - 56) / 1080)
}

async function enterPreview() {
  if (!screenId.value) {
    ElMessage.warning('请先保存大屏配置，再进行预览')
    return
  }
  await save()
  if (!screenId.value) return
  previewMode.value = true
  router.replace({ query: { id: screenId.value, preview: 1 } })
  previewLoading.value = true
  try {
    // 先加载与实时监控页同源的摄像头列表，供视频面板兜底显示与流信息校准
    await loadVideoCameras()
    await fetchRealtime()
    // 塔吊/升降机实时数据：与视频监控同源的传感器 WS 推流
    await initSensorRealtime()
    // 环境监测实时数据：与视频监控环境监测页同源的传感器 WS 推流
    await initEnvRealtime()
    startClock()
    startAlarmScroll()
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    previewLoading.value = false
    nextTick(calcPreviewScale)
  }
}

function exitPreview() {
  stopClock()
  stopAlarmScroll()
  stopAllVideoRotation()
  disposeCharts()
  destroySensorRealtime()
  previewMode.value = false
  router.replace({ query: { id: screenId.value } })
  nextTick(calcCanvasScale)
}

let clockTimer = null
function startClock() {
  stopClock()
  clockTimer = setInterval(() => { nowTick.value = Date.now() }, 1000)
}
function stopClock() {
  if (clockTimer) { clearInterval(clockTimer); clockTimer = null }
}

async function toggleFullscreen() {
  try {
    if (!document.fullscreenElement) await document.documentElement.requestFullscreen?.()
    else await document.exitFullscreen?.()
  } catch (_) {}
  nextTick(calcPreviewScale)
}

/* ---------------- 视频轮播配置对话框 ---------------- */
const rotationVisible = ref(false)
const rotationLoading = ref(false)
const rotationSaving = ref(false)
const rotationList = ref([])
const cameraOptions = ref([])
const rotationForm = reactive({ cameraId: null, displayOrder: 1, duration: 10 })

/** 分页循环拉取全部摄像头（pageSize=100，服务端上限 100；原 pageSize=999 会触发 @Max 校验 400 导致下拉为空） */
async function loadAllCameras() {
  const list = []
  let pageNum = 1
  let total = Infinity
  while (list.length < total) {
    const res = await videoApi.getCameras({ pageNum, pageSize: 100 })
    const records = res?.data?.records ?? res?.data?.list ?? []
    total = res?.data?.total ?? records.length
    list.push(...records)
    if (records.length === 0) break // 防止 total 不准确时死循环
    pageNum += 1
  }
  return list
}

async function openRotationDialog() {
  if (!screenId.value) {
    ElMessage.warning('请先保存大屏配置，再配置视频轮播')
    return
  }
  rotationVisible.value = true
  rotationLoading.value = true
  // 轮播配置与摄像头列表分别加载：任一失败不影响另一方（摄像头列表可正常选择）
  try {
    const res = await screenApi.getVideoRotationConfig(screenId.value)
    rotationList.value = (res?.data?.records || []).map(r => ({ ...r }))
  } catch (e) {
    /* 错误由拦截器统一提示 */
  }
  try {
    const cameras = await loadAllCameras()
    cameraOptions.value = cameras.map(c => ({ id: c.id, name: c.cameraName, streamUrl: c.streamUrl }))
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    rotationLoading.value = false
  }
}

function addRotation() {
  if (!rotationForm.cameraId) { ElMessage.warning('请选择摄像头'); return }
  if (rotationList.value.some(r => r.cameraId === rotationForm.cameraId)) {
    ElMessage.warning('该摄像头已在轮播列表中')
    return
  }
  const cam = cameraOptions.value.find(c => c.id === rotationForm.cameraId)
  rotationList.value.push({
    cameraId: rotationForm.cameraId,
    cameraName: cam?.name || '',
    streamUrl: cam?.streamUrl || '',
    displayOrder: rotationForm.displayOrder || rotationList.value.length + 1,
    duration: rotationForm.duration || 10
  })
  rotationForm.cameraId = null
  rotationForm.displayOrder = rotationList.value.length + 1
}

function removeRotation(row) {
  const idx = rotationList.value.findIndex(r => r.cameraId === row.cameraId)
  if (idx > -1) rotationList.value.splice(idx, 1)
}

async function saveRotations() {
  rotationSaving.value = true
  try {
    await screenApi.saveVideoRotationConfig(screenId.value, rotationList.value.map((r, i) => ({
      cameraId: r.cameraId,
      displayOrder: r.displayOrder ?? i + 1,
      duration: r.duration ?? 10
    })))
    ElMessage.success('视频轮播配置保存成功')
    rotationVisible.value = false
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    rotationSaving.value = false
  }
}

/* ---------------- 其他 ---------------- */
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

// 数值格式化：null/undefined/NaN/空字符串 → '-'，整数原样显示，小数保留 1 位
function fmtNum(value) {
  if (value === null || value === undefined || value === '') return '-'
  const n = Number(value)
  if (Number.isNaN(n)) return '-'
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function handleResize() {
  calcCanvasScale()
  calcPreviewScale()
  if (previewMode.value) nextTick(renderCharts)
}

function goBack() {
  if (previewMode.value) { exitPreview(); return }
  router.push('/screen/list')
}

onMounted(async () => {
  if (screenId.value) await loadDetail()
  window.addEventListener('resize', handleResize)
  if (previewMode.value) {
    nextTick(() => {
      calcPreviewScale()
      previewLoading.value = true
      // 先加载与实时监控页同源的摄像头列表，供视频面板兜底显示与流信息校准
      loadVideoCameras().finally(() => {
        fetchRealtime().finally(() => {
          previewLoading.value = false
          startClock()
          startAlarmScroll()
        })
      })
      // 塔吊/升降机实时数据：与视频监控同源的传感器 WS 推流。
      // 必须串行：WS 订阅是"整体覆盖"语义（后到订阅覆盖先到），并行执行时
      // initSensorRealtime 的覆盖会剔除环境传感器，导致环境面板一直"等待数据"
      initSensorRealtime().then(() => initEnvRealtime())
    })
  } else {
    nextTick(calcCanvasScale)
  }
})

onBeforeUnmount(() => {
  console.log('[editor] onBeforeUnmount 开始', Date.now())
  stopClock()
  stopAllVideoRotation()
  disposeCharts()
  destroySensorRealtime()
  stopAlarmScroll()
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  console.log('[editor] onBeforeUnmount 完成', Date.now())
})
</script>

<style scoped>
/* ==================== 编辑模式 ==================== */
.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}
.palette-item:hover {
  border-color: #3b82f6;
  background: #eff6ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.15);
}

.canvas-comp {
  box-sizing: border-box;
  cursor: move;
  user-select: none;
  background: rgba(255, 255, 255, 0.92);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.15s;
}
.canvas-comp.selected {
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.25);
}
.comp-header {
  height: 26px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}
.comp-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
  color: #64748b;
}
.comp-actions .el-icon {
  cursor: pointer;
}
.comp-actions .el-icon:hover {
  color: #3b82f6;
}
.comp-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  pointer-events: none;
}
.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 14px;
  height: 14px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, #3b82f6 50%);
  border-radius: 0 0 4px 0;
}

/* ==================== 预览模式（深色大屏） ==================== */
.screen-preview-root {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: radial-gradient(ellipse at 50% 0%, #12315e 0%, #0b1f3a 45%, #060f22 100%);
}
.preview-header {
  background: linear-gradient(180deg, rgba(2, 6, 23, 0.85), transparent);
}
.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: livePulse 1.2s ease-in-out infinite;
}
@keyframes livePulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.6); }
  50% { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
}

.preview-panel {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  gap: 8px;
  border-radius: 6px;
  border: 1px solid rgba(56, 189, 248, 0.25);
  background: linear-gradient(160deg, rgba(30, 58, 92, 0.55), rgba(15, 23, 42, 0.55));
  box-shadow: inset 0 0 24px rgba(56, 189, 248, 0.06), 0 4px 16px rgba(2, 6, 23, 0.4);
  overflow: hidden;
}
.preview-panel.video-panel {
  padding: 0;
}
.panel-title {
  position: relative;
  font-size: 15px;
  font-weight: 700;
  color: #e2e8f0;
  letter-spacing: 1px;
  padding-left: 10px;
  flex-shrink: 0;
}
.panel-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, #38bdf8, #0ea5e9);
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px 4px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.15);
}
.stat-num {
  font-size: 30px;
  font-weight: 800;
  color: var(--c, #38bdf8);
  font-family: 'DIN Alternate', 'Bahnschrift', sans-serif;
  line-height: 1.1;
  text-shadow: 0 0 12px var(--c, #38bdf8);
}
.stat-label {
  font-size: 12px;
  color: #94a3b8;
}

.env-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.env-point-card {
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.15);
  flex-shrink: 0;
}

.alarm-scroll-area {
  position: relative;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}
.alarm-scroll-track {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.alarm-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 4px;
  background: rgba(15, 23, 42, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.12);
  white-space: nowrap;
}
.level-tag {
  flex-shrink: 0;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid;
}
.alarm-type { font-size: 12px; color: #e2e8f0; flex-shrink: 0; }
.alarm-device { font-size: 12px; color: #94a3b8; flex-shrink: 0; }
.alarm-time { font-size: 11px; color: #64748b; margin-left: auto; }

.equip-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.equip-card {
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.15);
  flex-shrink: 0;
}
.equip-name { font-size: 13px; font-weight: 600; color: #e2e8f0; }
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 滚动条（预览深色风格） */
.equip-list::-webkit-scrollbar,
.env-list::-webkit-scrollbar,
.alarm-scroll-area::-webkit-scrollbar {
  width: 4px;
}
.equip-list::-webkit-scrollbar-thumb,
.env-list::-webkit-scrollbar-thumb,
.alarm-scroll-area::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.35);
  border-radius: 2px;
}
</style>
