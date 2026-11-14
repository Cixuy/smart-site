<template>
  <div class="amap-picker">
    <div class="picker-toolbar">
      <span class="coord-display">
        <el-tag size="small" type="warning">经度: {{ currentCoord.x.toFixed(6) }}</el-tag>
        <el-tag size="small" type="warning">纬度: {{ currentCoord.y.toFixed(6) }}</el-tag>
      </span>
      <div class="toolbar-actions">
        <el-input
          v-model="addressKeyword"
          size="small"
          placeholder="搜索地址..."
          style="width:160px"
          clearable
          @keyup.enter="searchAddress"
        >
          <template #suffix>
            <el-icon style="cursor:pointer" @click="searchAddress"><Search /></el-icon>
          </template>
        </el-input>
        <el-button size="small" text @click="reset">复位</el-button>
      </div>
    </div>

    <div ref="mapContainer" class="picker-map"></div>
    <div class="center-crosshair">
      <svg width="32" height="32" viewBox="0 0 32 32">
        <circle cx="16" cy="16" r="12" fill="none" stroke="#ff4d4f" stroke-width="2" />
        <circle cx="16" cy="16" r="3" fill="#ff4d4f" />
        <line x1="16" y1="0" x2="16" y2="8" stroke="#ff4d4f" stroke-width="1.5" />
        <line x1="16" y1="24" x2="16" y2="32" stroke="#ff4d4f" stroke-width="1.5" />
        <line x1="0" y1="16" x2="8" y2="16" stroke="#ff4d4f" stroke-width="1.5" />
        <line x1="24" y1="16" x2="32" y2="16" stroke="#ff4d4f" stroke-width="1.5" />
      </svg>
    </div>

    <!-- 手动输入 -->
    <div class="manual-inputs">
      <div class="input-group">
        <label>经度 (X)</label>
        <el-input-number
          v-model="localX"
          :min="-180"
          :max="180"
          :precision="6"
          :step="0.001"
          size="small"
          controls-position="right"
          @change="syncFromInput"
        />
      </div>
      <div class="input-group">
        <label>纬度 (Y)</label>
        <el-input-number
          v-model="localY"
          :min="-90"
          :max="90"
          :precision="6"
          :step="0.001"
          size="small"
          controls-position="right"
          @change="syncFromInput"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { loadAMap } from '@/utils/amap'

const props = defineProps({
  modelValue: { type: Object, default: () => ({ x: 116.397428, y: 39.90923 }) },
  zoom: { type: Number, default: 15 }
})

const emit = defineEmits(['update:modelValue'])

const mapContainer = ref(null)
const addressKeyword = ref('')

let mapInstance = null
let centerMarker = null
let geocoder = null

const currentCoord = reactive({ x: props.modelValue.x, y: props.modelValue.y })
const localX = ref(props.modelValue.x)
const localY = ref(props.modelValue.y)

watch(() => props.modelValue, (v) => {
  if (v) {
    currentCoord.x = v.x; currentCoord.y = v.y
    localX.value = v.x; localY.value = v.y
    moveMapTo(v.x, v.y)
  }
}, { deep: true })

onMounted(async () => {
  try {
    const AMap = await loadAMap(['AMap.Geocoder'])
    await nextTick()
    if (!mapContainer.value) return

    mapInstance = new AMap.Map(mapContainer.value, {
      zoom: props.zoom,
      center: [props.modelValue.x, props.modelValue.y],
      viewMode: '2D'
    })

    geocoder = new AMap.Geocoder({ map: mapInstance })

    // 中心十字标记
    centerMarker = new AMap.Marker({
      position: [props.modelValue.x, props.modelValue.y],
      offset: new AMap.Pixel(-16, -16),
      content: '<div style="pointer-events:none;opacity:0">cross</div>',
      zIndex: 999
    })
    mapInstance.add(centerMarker)

    // 拖动地图时更新坐标
    mapInstance.on('moveend', () => {
      const center = mapInstance.getCenter()
      if (center) {
        currentCoord.x = center.lng
        currentCoord.y = center.lat
        localX.value = center.lng
        localY.value = center.lat
        emitUpdate()
        // 更新隐藏 marker 位置
        if (centerMarker) {
          centerMarker.setPosition([center.lng, center.lat])
        }
      }
    })
  } catch (err) {
    console.error('AMap 加载失败:', err)
  }
})

onUnmounted(() => {
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
  centerMarker = null
  geocoder = null
})

function moveMapTo(lng, lat) {
  if (!mapInstance) return
  mapInstance.setCenter([lng, lat])
}

function emitUpdate() {
  emit('update:modelValue', {
    x: parseFloat(currentCoord.x.toFixed(6)),
    y: parseFloat(currentCoord.y.toFixed(6))
  })
}

function syncFromInput() {
  currentCoord.x = localX.value
  currentCoord.y = localY.value
  moveMapTo(localX.value, localY.value)
  emitUpdate()
}

function reset() {
  const def = { x: 116.397428, y: 39.90923 }
  currentCoord.x = def.x; currentCoord.y = def.y
  localX.value = def.x; localY.value = def.y
  moveMapTo(def.x, def.y)
  emit('update:modelValue', def)
}

function searchAddress() {
  if (!geocoder || !addressKeyword.value.trim()) return
  geocoder.getLocation(addressKeyword.value, (status, result) => {
    if (status === 'complete' && result.info === 'OK') {
      const loc = result.geocodes[0].location
      currentCoord.x = loc.lng
      currentCoord.y = loc.lat
      localX.value = loc.lng
      localY.value = loc.lat
      moveMapTo(loc.lng, loc.lat)
      emitUpdate()
    }
  })
}
</script>

<style scoped>
.amap-picker {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.coord-display {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.picker-map {
  width: 100%;
  height: 280px;
  position: relative;
}
.center-crosshair {
  position: absolute;
  /* 这需要父级有 position:relative; 在 picker-toolbar 之后的布局中不行……改为相对于 amap-picker */
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 10;
}
/* 覆盖：center-crosshair 应该相对于 picker-map */
.amap-picker {
  position: relative;
}
.center-crosshair {
  top: calc(42px + 140px); /* toolbar height + half map */
  left: 50%;
  transform: translate(-50%, -50%);
}
.manual-inputs {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  background: #f9fafb;
  border-top: 1px solid #e5e7eb;
}
.input-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}
.input-group label {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
  min-width: 52px;
}

/* 覆盖高德地图 logo 定位 */
:deep(.amap-logo) { display: none !important; }
:deep(.amap-copyright) { display: none !important; }
</style>
