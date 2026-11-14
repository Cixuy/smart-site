<template>
  <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-base font-semibold text-gray-700 flex items-center gap-2">
        <el-icon color="#1677ff"><Connection /></el-icon> 联动规则可视化
      </h3>
      <el-tag v-if="linkedCount > 0" type="success" size="small">
        {{ linkedCount }} 项已联动
      </el-tag>
      <el-tag v-else type="info" size="small">未配置联动</el-tag>
    </div>

    <!-- 规则流程图 -->
    <div class="flow-container">
      <div
        v-for="item in items"
        :key="item.id"
        class="flow-row"
        :class="{ 'opacity-50': !item.sprayLinked }"
      >
        <!-- 传感器节点 -->
        <div class="node sensor-node" :class="{ active: item.sprayLinked }">
          <div class="node-icon">{{ getSensorIcon(item.indicatorName) }}</div>
          <div class="node-label">{{ item.indicatorName }}</div>
        </div>

        <!-- 连接线+条件 -->
        <div class="flow-connector" :class="{ active: item.sprayLinked }">
          <div class="connector-line"></div>
          <div class="condition-badge" :class="{ active: item.sprayLinked }">
            当前值 ≥ {{ item.linkThreshold }}{{ item.unit }}
          </div>
        </div>

        <!-- 阈值可视化刻度 -->
        <div class="threshold-gauge">
          <div class="gauge-track">
            <div class="gauge-range warn" :style="{ width: warnPercent(item) + '%', left: '0%' }"></div>
            <div
              class="gauge-marker"
              :style="{ left: warnPercent(item) + '%' }"
              :class="{ active: item.sprayLinked }"
            >
              <span class="marker-label">{{ item.warnThreshold }}</span>
            </div>
            <div
              class="gauge-marker alarm"
              :style="{ left: alarmPercent(item) + '%' }"
              :class="{ active: item.sprayLinked }"
            >
              <span class="marker-label">{{ item.alarmThreshold }}</span>
            </div>
            <div
              class="gauge-marker link"
              :style="{ left: linkPercent(item) + '%' }"
              :class="{ active: item.sprayLinked }"
            >
              <span class="marker-label-tag">联动</span>
            </div>
          </div>
          <div class="gauge-legends">
            <span class="legend warn-dot">预</span>预警
            <span class="legend alarm-dot">告</span>告警
            <span class="legend link-dot">联</span>联动
          </div>
        </div>

        <!-- 连接线 -->
        <div class="flow-connector" :class="{ active: item.sprayLinked }">
          <div class="connector-line"></div>
        </div>

        <!-- 执行器节点 -->
        <div class="node action-node" :class="{ active: item.sprayLinked }">
          <div class="node-icon spray-icon">💧</div>
          <div class="node-label">喷淋系统</div>
          <el-switch
            v-model="item.sprayLinked"
            size="small"
            @change="() => $emit('toggle', item)"
          />
        </div>
      </div>
    </div>

    <!-- 汇总信息 -->
    <div class="mt-4 pt-4 border-t border-gray-100 flex flex-wrap gap-3">
      <div v-for="stat in stats" :key="stat.key" class="stat-chip">
        <span class="stat-dot" :style="{ background: stat.color }"></span>
        <span class="stat-label">{{ stat.label }}</span>
        <span class="stat-value" :style="{ color: stat.color }">{{ stat.count }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Connection } from '@element-plus/icons-vue'

const props = defineProps({
  /** 指标数据列表 */
  items: { type: Array, required: true },
  /** 最大阈值范围（用于计算比例） */
  maxValue: { type: Number, default: 300 }
})

defineEmits(['toggle'])

const linkedCount = computed(() => props.items.filter(i => i.sprayLinked).length)

const stats = computed(() => [
  { key: 'total', label: '指标总数', count: props.items.length, color: '#1677ff' },
  { key: 'linked', label: '已联动', count: linkedCount.value, color: '#52c41a' },
  { key: 'unlinked', label: '未联动', count: props.items.length - linkedCount.value, color: '#999' }
])

const icons = { 'PM2.5': '🌫', 'PM10': '💨', '温度': '🌡', '湿度': '💧', '噪声': '🔊', '风速': '🍃' }
function getSensorIcon(name) {
  return icons[name] || '📊'
}

function warnPercent(item) {
  return clamp((+item.warnThreshold / props.maxValue) * 100)
}
function alarmPercent(item) {
  return clamp((+item.alarmThreshold / props.maxValue) * 100)
}
function linkPercent(item) {
  return clamp((+item.linkThreshold / props.maxValue) * 100)
}
function clamp(v) {
  return Math.min(100, Math.max(0, v))
}
</script>

<style scoped>
.flow-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.flow-row {
  display: flex;
  align-items: center;
  gap: 0;
  transition: opacity 0.3s;
}

/* 节点 */
.node {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 12px;
  border: 2px dashed #d9d9d9;
  background: #fafafa;
  flex-shrink: 0;
  transition: all 0.3s;
}
.node.active {
  border-style: solid;
  border-color: #1677ff;
  background: #e6f4ff;
  box-shadow: 0 0 8px rgba(22,119,255,0.2);
}
.node-icon {
  font-size: 22px;
  line-height: 1;
  margin-bottom: 2px;
}
.spray-icon {
  font-size: 24px;
}
.node-label {
  font-size: 11px;
  color: #666;
  text-align: center;
  line-height: 1.2;
}

/* 连接线 */
.flow-connector {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 40px;
}
.connector-line {
  flex: 1;
  height: 2px;
  background: #e8e8e8;
  position: relative;
}
.connector-line::after {
  content: '▶';
  position: absolute;
  right: -4px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 8px;
  color: #ccc;
}
.flow-connector.active .connector-line {
  background: #1677ff;
}
.flow-connector.active .connector-line::after {
  color: #1677ff;
}

.condition-badge {
  position: absolute;
  font-size: 10px;
  white-space: nowrap;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 10px;
  color: #999;
  border: 1px solid #e8e8e8;
  z-index: 1;
  left: 0;
  min-width: max-content;
}

/* 隐藏 condition badge 相对定位的绝对处理 - 改为内联在 flow-connector 中 */
.flow-connector {
  position: relative;
}
.condition-badge {
  position: absolute;
  top: -24px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  white-space: nowrap;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 10px;
  color: #999;
  border: 1px solid #e8e8e8;
  z-index: 1;
}
.condition-badge.active {
  background: #e6f4ff;
  color: #1677ff;
  border-color: #91caff;
}

/* 阈值刻度 */
.threshold-gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 200px;
  flex-shrink: 0;
}
.gauge-track {
  position: relative;
  width: 100%;
  height: 6px;
  background: linear-gradient(to right, #52c41a, #faad14, #ff4d4f);
  border-radius: 3px;
}
.gauge-marker {
  position: absolute;
  top: -6px;
  transform: translateX(-50%);
  width: 2px;
  height: 18px;
  background: #d9d9d9;
  transition: background 0.3s;
}
.gauge-marker.active {
  background: #1677ff;
}
.gauge-marker.alarm {
  background: #ff4d4f;
}
.marker-label {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  color: #999;
  white-space: nowrap;
}
.marker-label-tag {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  color: #1677ff;
  font-weight: 600;
  white-space: nowrap;
  background: #e6f4ff;
  padding: 0 4px;
  border-radius: 3px;
}
.gauge-legends {
  display: flex;
  gap: 10px;
  font-size: 10px;
  color: #999;
  align-items: center;
}
.legend {
  display: flex;
  align-items: center;
  gap: 2px;
}
.warn-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  background: #faad14;
  color: #fff;
  font-size: 8px;
  border-radius: 3px;
  font-weight: 600;
}
.alarm-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  background: #ff4d4f;
  color: #fff;
  font-size: 8px;
  border-radius: 3px;
  font-weight: 600;
}
.link-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  background: #1677ff;
  color: #fff;
  font-size: 8px;
  border-radius: 3px;
  font-weight: 600;
}

/* 统计标签 */
.stat-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: #fafafa;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
}
.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.stat-label {
  font-size: 12px;
  color: #666;
}
.stat-value {
  font-size: 13px;
  font-weight: 600;
}

/* 联动开关在 action-node 中的定位 */
.action-node .el-switch {
  margin-top: 4px;
}
.action-node {
  gap: 2px;
}
</style>
