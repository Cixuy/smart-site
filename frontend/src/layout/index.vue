<template>
  <div class="layout-container h-screen flex overflow-hidden" :class="{ 'sidebar-collapsed': isCollapsed }">
    <!-- 侧边栏 -->
    <Sidebar :collapsed="isCollapsed" @toggle="toggleSidebar" />

    <!-- 右侧主区域 -->
    <div class="flex-1 flex flex-col overflow-hidden min-w-0">
      <!-- 顶栏 -->
      <Navbar :collapsed="isCollapsed" @toggle="toggleSidebar" />

      <!-- 主内容区 -->
      <main ref="mainRef" class="flex-1 overflow-auto bg-[#f0f2f5] p-4">
        <!--
          页面容器：不再使用 <transition mode="out-in">。
          根因：Vue 3.5 的 out-in 状态机（isLeaving → 渲染空占位符 → afterLeave 里同步
          instance.update() 重入 patch）在本项目路由切换时不可靠，导致 leave 完成后
          新组件永不挂载（main 子元素数为 0，白屏，刷新才恢复）。
          改为按 route.path 加 key 的包裹层：路由切换时整棵子树同步卸载/挂载，
          新页面立即渲染，配合 CSS 动画保留进入动效。
        -->
        <router-view v-slot="{ Component, route }">
          <div :key="route.path" class="page-enter h-full">
            <component :is="Component" />
          </div>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from 'vuex'
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
// 2026-08-15 清理：移除全局告警 wsManager（utils/websocket.js）。
// 该连接无任何业务订阅（仅建立/断开连接），后端 /ws/realtime 未使用时会无限重连刷日志，
// 实时数据已由 towerCraneWs.js / hoistWs.js 独立承担，此处全局连接一并删除。

const store = useStore()
const route = useRoute()
const isCollapsed = computed(() => store.state.app.sidebarCollapsed)

// ==================== 临时诊断：路由切换卡死检测（确认修复后删除） ====================
// 已移除 transition 状态机，改为直接检查 main 内容是否渲染。
const mainRef = ref(null)
let pendingTimer = null
watch(
  () => route.path,
  (newPath, oldPath) => {
    console.log('[route] 变化:', oldPath, '→', newPath, 'now:', Date.now())
    if (pendingTimer) { clearTimeout(pendingTimer); pendingTimer = null }
    pendingTimer = setTimeout(() => {
      const mainEl = mainRef.value
      const childCount = mainEl ? mainEl.children.length : -1
      if (childCount <= 0) {
        const msg = `[卡死检测] 路由 ${newPath} 切换后 3s 内未完成渲染！\n当前 main 子元素数: ${mainEl ? mainEl.children.length : 'main 为 null'}`
        console.error(msg)
        try {
          const div = document.createElement('div')
          div.style.cssText =
            'position:fixed;top:60px;right:0;z-index:99999;max-width:80%;background:rgba(217,119,6,0.95);' +
            'color:#fff;padding:10px 14px;font-size:12px;font-family:monospace;white-space:pre-wrap;' +
            'border-bottom-left-radius:8px;cursor:pointer'
          div.textContent = msg
          div.onclick = () => div.remove()
          document.body.appendChild(div)
          setTimeout(() => div.remove(), 20000)
        } catch (_) {}
      }
    }, 3000)
  }
)
// ==================== 临时诊断结束 ====================

function toggleSidebar() {
  store.dispatch('app/toggleSidebar')
}
// 2026-08-15 清理：原全局告警 WebSocket 生命周期（initWebSocket/destroyWebSocket/
// token watch/visibilitychange 重连）随 utils/websocket.js 一并删除，原因见顶部 import 注释。
</script>

<style scoped>
.layout-container {
  background: #f0f2f5;
}

main {
  scroll-behavior: smooth;
}

/* 页面进入动画：依赖路由切换时包裹层 key 变化触发重挂载，纯 opacity 避免
   transform 在动画期间成为 fixed 子元素的包含块，影响弹层/下拉等定位 */
.page-enter {
  animation: pageFadeIn 0.25s ease;
}

@keyframes pageFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
</style>
