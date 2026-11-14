<template>
  <div class="amap-location-view bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
    <!-- 顶栏 -->
    <div class="map-header">
      <h3 class="font-semibold text-gray-700 flex items-center gap-2">
        <el-icon color="#1677ff"><MapLocation /></el-icon> 高德GIS空间视图
      </h3>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400">{{ flatNodes.length }} 个位置</span>
        <el-switch v-model="showLabels" size="small" active-text="标签" />
        <el-select v-model="mapLayer" size="small" style="width:90px">
          <el-option label="标准" value="normal" />
          <el-option label="卫星" value="satellite" />
        </el-select>
        <el-button size="small" text :icon="Refresh" @click="fitAllMarkers">适应全部</el-button>
      </div>
    </div>

    <!-- 地图容器 -->
    <div ref="mapContainer" class="amap-container"></div>

    <!-- 选中节点详情条 -->
    <div v-if="selectedNode" class="detail-bar">
      <div class="detail-info">
        <span
          class="detail-badge"
          :style="{ background: levelColorMap[selectedNode.level] || '#999' }"
        ></span>
        <strong>{{ selectedNode.locationName }}</strong>
        <span class="text-xs text-gray-400">
          坐标: {{ selectedNode.gisLon?.toFixed(6) }}, {{ selectedNode.gisLat?.toFixed(6) }}
        </span>
      </div>
      <el-button text size="small" type="primary" :icon="Edit" @click="$emit('edit', selectedNode)">编辑</el-button>
    </div>

    <!-- 图例 -->
    <div class="map-legend">
      <span v-for="lvl in levels" :key="lvl.level" class="legend-item">
        <span class="legend-dot" :style="{ background: lvl.color }"></span>
        {{ lvl.label }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { MapLocation, Edit, Refresh } from '@element-plus/icons-vue'
import { loadAMap } from '@/utils/amap'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const emit = defineEmits(['edit', 'select'])

// UI 状态
const showLabels = ref(true)
const mapLayer = ref('normal')
const selectedNode = ref(null)

const mapContainer = ref(null)

let mapInstance = null
let markersMap = {}
let infoWindow = null

const levels = [
  { level: 1, label: '一级区域', color: '#1677ff' },
  { level: 2, label: '二级位置', color: '#fa8c16' },
  { level: 3, label: '三级点位', color: '#52c41a' }
]

const levelColorMap = { 1: '#1677ff', 2: '#fa8c16', 3: '#52c41a' }
const levelIconMap = { 1: '📍', 2: '📌', 3: '🔹' }

// 展平树为节点列表
const flattenTree = (tree, level = 1) => {
  const result = []
  if (!Array.isArray(tree)) return result
  tree.forEach(item => {
    const lng = item.gisLon
    const lat = item.gisLat
    result.push({
      id: item.id,
      locationName: item.locationName,
      level,
      gisLon: lng,
      gisLat: lat,
      hasCoord: lng != null && lat != null,
      raw: item
    })
    if (item.children && item.children.length) {
      result.push(...flattenTree(item.children, level + 1))
    }
  })
  return result
}

const flatNodes = computed(() => flattenTree(props.data))

// 有坐标的节点（用于打点）
const nodesWithCoords = computed(() => flatNodes.value.filter(n => n.hasCoord))

function getMarkerContent(node) {
  const icon = levelIconMap[node.level] || '📍'
  const color = levelColorMap[node.level] || '#999'
  if (!showLabels.value) {
    return `<div style="
      width:18px;height:18px;background:${color};
      border:2px solid #fff;border-radius:50%;
      box-shadow:0 1px 4px rgba(0,0,0,0.3);
      cursor:pointer;
    "></div>`
  }
  const size = node.level === 1 ? '24px' : node.level === 2 ? '18px' : '14px'
  return `<div style="
    cursor:pointer;
    text-align:center;
    line-height:1;
    filter:drop-shadow(0 1px 2px rgba(0,0,0,0.3));
  ">
    <div style="
      width:${size};height:${size};
      background:${color};
      border:2px solid #fff;
      border-radius:50%;
      display:flex;
      align-items:center;
      justify-content:center;
      font-size:${node.level === 1 ? '12px' : '10px'};
      color:#fff;
      font-weight:bold;
      margin:0 auto;
    ">${node.level}</div>
    <div style="
      font-size:10px;
      color:#333;
      background:rgba(255,255,255,0.85);
      padding:1px 4px;
      border-radius:2px;
      margin-top:2px;
      white-space:nowrap;
    ">${node.locationName}</div>
  </div>`
}

// 构建 AMap marker 内容
function getL1AreaContent(node, nodes) {
  // 对于 L1 区域，不画多边形，仅用大号 marker
  return getMarkerContent(node)
}

onMounted(async () => {
  try {
    const AMap = await loadAMap()
    await nextTick()
    if (!mapContainer.value) return

    mapInstance = new AMap.Map(mapContainer.value, {
      zoom: 13,
      center: [116.42, 39.94],
      viewMode: '2D',
      layers: [new AMap.TileLayer()]
    })

    infoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -30) })

    // 监听图层切换
    watch(mapLayer, (val) => {
      if (!mapInstance) return
      const layers = val === 'satellite'
        ? [new AMap.TileLayer.Satellite()]
        : [new AMap.TileLayer()]
      mapInstance.setLayers(layers)
    })

    // 监听数据变化以重建标记
    watch(nodesWithCoords, () => rebuildMarkers(), { deep: true })
    watch(showLabels, () => rebuildMarkers())

    rebuildMarkers()
  } catch (err) {
    console.error('AMap 加载失败:', err)
  }
})

onUnmounted(() => {
  if (infoWindow) infoWindow.close()
  infoWindow = null
  clearMarkers()
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
})

function clearMarkers() {
  Object.values(markersMap).forEach(m => {
    if (m.contentDom) {
      m.contentDom = null
    }
    if (m.setMap) m.setMap(null)
  })
  markersMap = {}
}

function rebuildMarkers() {
  if (!mapInstance) return
  clearMarkers()

  const AMap = window.AMap

  nodesWithCoords.value.forEach(node => {
    const content = getMarkerContent(node)
    const marker = new AMap.Marker({
      position: [node.gisLon, node.gisLat],
      content,
      offset: new AMap.Pixel(showLabels.value ? -40 : -9, showLabels.value ? -28 : -9),
      zIndex: (4 - node.level) * 100
    })

    marker.on('click', () => {
      selectNode(node)
    })

    marker.setMap(mapInstance)
    markersMap[node.id] = marker
  })

  fitAllMarkers()
}

function selectNode(node) {
  selectedNode.value = node

  if (infoWindow && node.hasCoord) {
    infoWindow.setContent(`
      <div style="padding:4px 8px;font-size:13px;">
        <strong>${node.locationName}</strong><br/>
        <span style="color:#999;font-size:11px;">
          L${node.level}<br/>
          ${node.gisLon?.toFixed(6)}, ${node.gisLat?.toFixed(6)}
        </span>
      </div>
    `)
    infoWindow.open(mapInstance, [node.gisLon, node.gisLat])
  }

  emit('select', node.raw || node)
}

function fitAllMarkers() {
  if (!mapInstance || nodesWithCoords.value.length === 0) return
  mapInstance.setFitView(null, false, [60, 60, 60, 60])
}
</script>

<style scoped>
.amap-location-view { position: relative; }
.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
  gap: 10px;
  flex-wrap: wrap;
}
.amap-container {
  width: 100%;
  height: 520px;
}

.detail-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: rgba(255,255,255,0.97);
  backdrop-filter: blur(8px);
  border-top: 1px solid #e5e7eb;
}
.detail-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-badge {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.map-legend {
  display: flex;
  gap: 14px;
  padding: 8px 14px;
  border-top: 1px solid #f0f0f0;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #6b7280;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

:deep(.amap-logo) { display: none !important; }
</style>
