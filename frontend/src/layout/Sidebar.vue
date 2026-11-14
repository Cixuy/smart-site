<template>
  <aside
    class="sidebar flex flex-col bg-[#001529] text-white transition-all duration-300 select-none z-50"
    :class="collapsed ? 'w-[64px]' : 'w-[220px]'"
  >
    <!-- Logo -->
    <div class="logo-area flex items-center h-16 px-4 overflow-hidden border-b border-white/10">
      <transition name="fade">
        <span v-show="!collapsed" class="ml-3 text-base font-semibold whitespace-nowrap tracking-wide">
          建筑安全智能工地平台
        </span>
      </transition>
    </div>

    <!-- 菜单区域 -->
    <el-scrollbar class="flex-1" wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="#ffffffb3"
        active-text-color="#ffffff"
        unique-opened
        router
        class="sidebar-menu"
      >
        <template v-for="menu in menuList" :key="menu.id || menu.path">
          <!-- 单级菜单 -->
          <template v-if="!menu.children || menu.children.length === 0">
            <el-menu-item :index="resolvePath(menu.path)">
              <el-icon v-if="menu.icon || menu.meta?.icon">
                <component :is="resolveIcon(menu.icon || menu.meta?.icon)" />
              </el-icon>
              <template #title>
                <span class="text-sm">{{ menu.meta?.title || menu.name || menu.title }}</span>
              </template>
            </el-menu-item>
          </template>

          <!-- 多级菜单 -->
          <template v-else>
            <el-sub-menu :index="menu.path || menu.name || String(menu.id)">
              <template #title>
                <el-icon v-if="menu.icon || menu.meta?.icon">
                  <component :is="resolveIcon(menu.icon || menu.meta?.icon)" />
                </el-icon>
                <span class="text-sm">{{ menu.meta?.title || menu.name || menu.title }}</span>
              </template>
              <template v-for="child in menu.children" :key="child.id || child.path">
                <el-menu-item :index="resolvePath(child.path)">
                  <el-icon v-if="child.icon || child.meta?.icon">
                    <component :is="resolveIcon(child.icon || child.meta?.icon)" />
                  </el-icon>
                  <span class="text-sm">{{ child.meta?.title || child.name || child.title }}</span>
                </el-menu-item>
              </template>
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>

    <!-- 折叠按钮 -->
    <div class="collapse-btn flex items-center justify-center h-12 cursor-pointer border-t border-white/10
      hover:bg-white/5 transition-colors" @click="$emit('toggle')">
      <el-icon :size="18">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
    </div>
  </aside>
</template>

<script setup>
import { computed, watch, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from 'vuex'

const props = defineProps({
  collapsed: { type: Boolean, default: false }
})

defineEmits(['toggle'])

const route = useRoute()
const store = useStore()
const menuList = computed(() => store.getters.menuList)

const activeMenu = computed(() => route.path)

function resolvePath(path) {
  return path.startsWith('/') ? path : `/${path}`
}

/** 图标映射 - 将菜单的 icon 字符串映射到 Element Plus 图标组件 */
const iconMap = {
  'home-filled': 'HomeFilled',
  'monitor': 'Monitor',
  'video-camera': 'VideoCamera',
  'camera': 'Camera',
  'cpu': 'Cpu',
  'setting': 'Setting',
  'tools': 'Tools',
  'user': 'User',
  'user-filled': 'UserFilled',
  'list': 'List',
  'document': 'Document',
  'data-analysis': 'DataAnalysis',
  'trend-charts': 'TrendCharts',
  'bell': 'Bell',
  'alarm-clock': 'AlarmClock',
  'warning': 'Warning',
  'wind-power': 'WindPower',
  'management': 'Management',
  'menu': 'Menu',
  'tickets': 'Tickets',
  'grid': 'Grid',
  'picture': 'Picture',
  'chat-dot-round': 'ChatDotRound',
  'chat-line-square': 'ChatLineSquare',
  'operation': 'Operation',
  'avatar': 'Avatar',
  'key': 'Key',
  'lock': 'Lock',
  'odometer': 'Odometer',
  'histogram': 'Histogram',
  'pie-chart': 'PieChart',
  'coordinate': 'Coordinate',
  'map-location': 'MapLocation',
  'position': 'Position',
  'files': 'Files',
  'folder': 'Folder',
  'delete': 'Delete',
  'edit': 'Edit',
  'plus': 'Plus',
  'search': 'Search',
  'refresh': 'Refresh',
  'download': 'Download',
  'upload': 'Upload'
}

function resolveIcon(iconName) {
  if (!iconName) return 'Menu'
  // 直接尝试 kebab-case 或 PascalCase
  const kebab = iconName
  const pascal = iconName.replace(/-([a-z])/g, (_, c) => c.toUpperCase())
    .replace(/^[a-z]/, c => c.toUpperCase())
  const mapped = iconMap[kebab] || iconMap[pascal] || pascal
  return mapped
}
</script>

<style scoped>
.sidebar {
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
}

.sidebar-menu {
  border-right: none !important;
}

.sidebar-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
  transition: all 0.2s;
}

.sidebar-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.sidebar-menu .el-menu-item.is-active {
  background: linear-gradient(90deg, #1890ff, #096dd9) !important;
  border-radius: 0 4px 4px 0;
  margin-right: 8px;
}

.sidebar-menu .el-sub-menu .el-menu {
  background-color: #000c17 !important;
}

/* 滚动条 */
:deep(.scrollbar-wrapper) {
  overflow-x: hidden !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
