<template>
  <header class="navbar h-14 flex items-center justify-between px-4 bg-white shadow-sm z-40">
    <!-- 左侧 -->
    <div class="flex items-center gap-3">
      <!-- 折叠按钮 -->
      <span class="collapse-icon cursor-pointer flex items-center justify-center w-8 h-8 rounded
        hover:bg-gray-100 transition-colors" @click="$emit('toggle')">
        <el-icon :size="20">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
      </span>

      <!-- 面包屑 -->
      <el-breadcrumb separator="/" class="hidden sm:flex">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧 -->
    <div class="flex items-center gap-2">
      <!-- 全局搜索 -->
      <el-popover placement="bottom" :width="360" trigger="click">
        <template #reference>
          <span class="search-btn flex items-center gap-1 px-3 py-1.5 rounded-full
            bg-gray-100 hover:bg-gray-200 cursor-pointer text-sm text-gray-500 transition-colors">
            <el-icon :size="16"><Search /></el-icon>
            <span class="hidden md:inline">搜索...</span>
            <kbd class="hidden md:inline ml-2 px-1.5 py-0.5 text-xs bg-white rounded border
              text-gray-400">Ctrl+K</kbd>
          </span>
        </template>
        <div class="search-popover">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索设备、告警、功能..."
            prefix-icon="Search"
            clearable
            size="large"
            @keyup.enter="doSearch"
          />
          <div v-if="searchHistory.length" class="mt-3">
            <div class="flex items-center justify-between mb-2">
              <span class="text-xs text-gray-400">搜索历史</span>
              <el-button link type="danger" size="small" @click="clearHistory">清空</el-button>
            </div>
            <div class="flex flex-wrap gap-2">
              <el-tag
                v-for="(item, idx) in searchHistory"
                :key="idx"
                size="small"
                class="cursor-pointer"
                @click="searchKeyword = item; doSearch()"
              >{{ item }}</el-tag>
            </div>
          </div>
        </div>
      </el-popover>

      <!-- 全屏 -->
      <span class="icon-btn" title="全屏" @click="toggleFullscreen">
        <el-icon :size="18"><FullScreen /></el-icon>
      </span>

      <!-- 用户信息下拉 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <span class="user-info flex items-center gap-2 cursor-pointer px-2 py-1 rounded
          hover:bg-gray-100 transition-colors">
          <el-avatar :size="32" icon="UserFilled" :src="avatar" />
          <span class="hidden sm:inline text-sm font-medium text-gray-700">{{ realName }}</span>
          <el-icon :size="14" class="text-gray-400"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon> 个人信息
            </el-dropdown-item>
            <el-dropdown-item command="password">
              <el-icon><Lock /></el-icon> 修改密码
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>

  <!-- 修改密码对话框 -->
  <el-dialog v-model="pwdDialogVisible" title="修改密码" width="420px" @closed="resetPwdForm">
    <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSubmitting" @click="handleChangePassword">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workbenchApi } from '@/api'
import { authApi } from '@/api'

const props = defineProps({ collapsed: { type: Boolean, default: false } })
defineEmits(['toggle'])

const route = useRoute()
const router = useRouter()
const store = useStore()

const realName = computed(() => store.getters.realName || '管理员')
const avatar = computed(() => store.getters.avatar || '')

// 面包屑
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title && !r.meta?.hidden)
  return matched.map(r => ({ path: r.path, title: r.meta.title }))
})

// 搜索
const searchKeyword = ref('')
const searchHistory = ref([])

async function loadSearchHistory() {
  try {
    const res = await workbenchApi.getSearchHistory()
    // 新接口返回 data.records（对象数组），提取 keyword 展示
    const records = res.data?.records || []
    searchHistory.value = records.map(r => r.keyword).filter(Boolean)
  } catch (_) { /* ignore */ }
}

function doSearch() {
  if (!searchKeyword.value.trim()) return
  router.push({ path: '/home', query: { search: searchKeyword.value.trim() } })
}

async function clearHistory() {
  try {
    await workbenchApi.clearSearchHistory()
    searchHistory.value = []
    ElMessage.success('已清空搜索历史')
  } catch (_) { /* ignore */ }
}

// 全屏
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

// ---- 修改密码 ----
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_, value, callback) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

function resetPwdForm() {
  pwdFormRef.value?.resetFields()
}

async function handleChangePassword() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialogVisible.value = false
    // 修改密码后强制退出重新登录
    setTimeout(() => {
      store.dispatch('user/logout')
      router.push('/login')
    }, 1000)
  } catch (_) { /* ignore */ }
  finally { pwdSubmitting.value = false }
}

// 用户下拉
async function handleCommand(cmd) {
  switch (cmd) {
    case 'logout':
      ElMessageBox.confirm('确认退出登录？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        await store.dispatch('user/logout')
        router.push('/login')
      }).catch(() => {})
      break
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      pwdDialogVisible.value = true
      break
  }
}

// 键盘快捷键
function handleKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    document.querySelector('.search-btn')?.click()
  }
}

onMounted(() => {
  loadSearchHistory()
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.navbar {
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  color: #606266;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: #f0f2f5;
  color: #1890ff;
}

.search-popover {
  padding: 4px 0;
}
</style>
