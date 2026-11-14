<template>
  <div class="tags-view-container flex items-center h-10 bg-white border-b border-gray-200 px-2">
    <el-scrollbar>
      <div class="tags-wrapper flex items-center gap-1 h-full">
        <router-link
          v-for="tag in visitedViews"
          :key="tag.path"
          :to="tag.path"
          class="tag-item"
          :class="{ active: isActive(tag) }"
        >
          <span class="tag-text">{{ tag.meta?.title || tag.name }}</span>
          <span
            v-if="!tag.meta?.affix"
            class="tag-close"
            @click.prevent.stop="closeTag(tag)"
          >
            <el-icon :size="12"><Close /></el-icon>
          </span>
        </router-link>
      </div>
    </el-scrollbar>

    <!-- 右键菜单 -->
    <div
      v-show="contextVisible"
      class="context-menu"
      :style="{ left: contextPos.x + 'px', top: contextPos.y + 'px' }"
    >
      <div class="context-item" @click="closeSelectedTag">关闭当前</div>
      <div class="context-item" @click="closeOthersTags">关闭其他</div>
      <div class="context-item" @click="closeAllTags">关闭所有</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'

const route = useRoute()
const router = useRouter()
const store = useStore()

const visitedViews = computed(() => store.state.tagsView.visitedViews)

// 右键菜单
const contextVisible = ref(false)
const contextPos = ref({ x: 0, y: 0 })
let selectedTag = null

function isActive(tag) {
  return tag.path === route.path
}

function closeTag(tag) {
  store.dispatch('tagsView/delView', tag)
  if (isActive(tag)) {
    toLastView()
  }
}

function toLastView() {
  const views = store.state.tagsView.visitedViews
  const latest = views[views.length - 1]
  if (latest) {
    router.push(latest.path)
  } else {
    router.push('/')
  }
}

function closeSelectedTag() {
  if (selectedTag) closeTag(selectedTag)
  contextVisible.value = false
}

function closeOthersTags() {
  if (selectedTag) {
    store.dispatch('tagsView/delOthersViews', selectedTag)
    contextVisible.value = false
  }
}

function closeAllTags() {
  store.dispatch('tagsView/delAllViews')
  router.push('/')
  contextVisible.value = false
}

// 关闭右键菜单
function handleClickOutside() {
  contextVisible.value = false
}

// 监听路由变化，添加 tag
watch(
  () => route.path,
  (path) => {
    if (path && !route.meta?.hidden) {
      store.dispatch('tagsView/addView', {
        path: route.path,
        name: route.name,
        meta: route.meta
      })
    }
  },
  { immediate: true }
)

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.tags-view-container {
  user-select: none;
}

.tags-wrapper {
  white-space: nowrap;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  color: #606266;
  background: #fafafa;
  text-decoration: none;
  transition: all 0.2s;
  cursor: pointer;
}

.tag-item:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background: #ecf5ff;
}

.tag-item.active {
  color: #fff;
  background: #409eff;
  border-color: #409eff;
}

.tag-item.active .tag-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.tag-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  margin-left: 6px;
  transition: background 0.2s;
}

.tag-close:hover {
  background: #e0e0e0;
}

.context-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 120px;
}

.context-item {
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.context-item:hover {
  background: #f0f2f5;
  color: #1890ff;
}
</style>
