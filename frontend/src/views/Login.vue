<template>
  <div class="login-page h-screen w-full flex items-center justify-center relative overflow-hidden">
    <!-- 动态背景 -->
    <div class="bg-animation">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
      <div class="bg-shape shape-4"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 左侧品牌区 -->
    <div class="brand-section hidden lg:flex flex-col items-center justify-center text-white z-10">
      <div class="brand-content text-center">
        <div class="brand-icon mb-6">
          <svg class="w-20 h-20 mx-auto" viewBox="0 0 80 80" fill="none">
            <rect width="80" height="80" rx="16" fill="rgba(255,255,255,0.15)" />
            <path d="M24 56V28l16 20 16-20v28" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none" />
            <circle cx="40" cy="22" r="3" fill="white" />
          </svg>
        </div>
        <h1 class="text-4xl font-bold tracking-wider mb-3">建筑安全智能工地平台</h1>
        <p class="text-lg opacity-80 tracking-wide">Smart Construction Site</p>
        <div class="mt-8 flex flex-col gap-2 text-sm opacity-60">
          <span>🏗️ 设备监控 · 实时预警</span>
          <span>🌐 3D可视化 · AI识别</span>
          <span>📊 数据大屏 · 智能分析</span>
        </div>
      </div>
    </div>

    <!-- 右侧登录卡片 -->
    <div class="login-card z-10">
      <div class="card-header text-center mb-8">
        <div class="flex lg:hidden justify-center mb-4">
          <div class="brand-icon-sm">
            <svg class="w-12 h-12" viewBox="0 0 48 48" fill="none">
              <rect width="48" height="48" rx="10" fill="#1890ff" />
              <path d="M14 33V16l10 13 10-13v17" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none" />
              <circle cx="24" cy="13" r="2" fill="white" />
            </svg>
          </div>
        </div>
        <h2 class="text-2xl font-bold text-gray-800">欢迎登录</h2>
        <p class="text-gray-400 mt-1 text-sm">智慧工地安全智能监控平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <div class="flex items-center justify-between mb-4">
          <el-checkbox v-model="rememberMe" label="记住密码" size="small" />
          <el-link type="primary" :underline="false" class="text-xs">忘记密码？</el-link>
        </div>

        <el-form-item>
          <el-button
            type="primary"
            class="w-full h-11 text-base font-medium tracking-wide"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="text-center text-xs text-gray-400 mt-4">
        <span>© 2026 智慧工地安全智能监控平台</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const store = useStore()

const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码至少4位', trigger: 'blur' }
  ]
}

/** 校验 redirect 参数，防止 Open Redirect 攻击 */
function safeRedirect(defaultPath = '/home') {
  const raw = route.query.redirect
  if (!raw) return defaultPath
  // 只允许以 / 开头的站内相对路径，拒绝 // 或 http 开头的绝对 URL
  if (typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//')) {
    return raw
  }
  return defaultPath
}

/** 登录 */
async function handleLogin() {
  if (loading.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await store.dispatch('user/login', {
      username: loginForm.username,
      password: loginForm.password
    })

    if (rememberMe.value) {
      localStorage.setItem('remembered_username', loginForm.username)
    } else {
      localStorage.removeItem('remembered_username')
    }

    // 获取用户信息（角色/权限/菜单）
    await store.dispatch('user/getUserInfo')

    ElMessage.success('登录成功')

    // 跳转到目标页（默认为 /home 工作台）
    const redirect = safeRedirect('/home')
    router.push(redirect)
  } catch (error) {
    // request 拦截器已对 HTTP 错误做了统一提示，此处仅兜底未被拦截器覆盖的错误
    const msg = error?.msg || error?.message || ''
    if (error?.code === 'ECONNABORTED' || msg.includes('timeout')) {
      ElMessage.error('连接超时，请检查网络或后端服务是否正常')
    } else if (msg && !msg.includes('canceled')) {
      // 避免与拦截器重复提示：拦截器 reject 时已调过 ElMessage.error
      // 此处仅在拦截器未提示时兜底（如 reject 了一个纯 Error 对象）
      ElMessage.error(msg)
    } else {
      ElMessage.error('登录失败，请检查用户名和密码')
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 恢复记住的用户名
  const remembered = localStorage.getItem('remembered_username')
  if (remembered) {
    loginForm.username = remembered
    rememberMe.value = true
  }
})
</script>

<style scoped>
.login-page {
  background: linear-gradient(135deg, #0a1628 0%, #0d2137 30%, #132d4a 60%, #0a1628 100%);
  min-height: 100vh;
}

/* 动态背景 */
.bg-animation {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.15;
  animation: float 20s ease-in-out infinite;
}

.shape-1 {
  width: 500px;
  height: 500px;
  background: #1890ff;
  top: -150px;
  right: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: #36cfc9;
  bottom: -100px;
  left: -100px;
  animation-delay: -5s;
}

.shape-3 {
  width: 300px;
  height: 300px;
  background: #b37feb;
  top: 50%;
  left: 50%;
  animation-delay: -10s;
}

.shape-4 {
  width: 250px;
  height: 250px;
  background: #ff7a45;
  bottom: 30%;
  right: 20%;
  animation-delay: -15s;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
  background-size: 60px 60px;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -20px) scale(1.05); }
  50% { transform: translate(-20px, 30px) scale(0.95); }
  75% { transform: translate(-30px, -10px) scale(1.02); }
}

/* 左侧品牌区 */
.brand-section {
  flex: 1;
  max-width: 520px;
  padding: 60px;
}

/* 登录卡片 */
.login-card {
  width: 420px;
  max-width: calc(100vw - 32px);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 48px 40px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.3),
              0 0 1px rgba(255, 255, 255, 0.1) inset;
  transition: transform 0.3s;
}

.login-card:hover {
  transform: translateY(-2px);
}

/* Element Plus 表单微调 */
:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #e4e7ed inset;
  transition: all 0.2s;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

:deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px #1890ff inset;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #1890ff, #096dd9);
  border: none;
  border-radius: 8px;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(24, 144, 255, 0.35);
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #40a9ff, #1890ff);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(24, 144, 255, 0.45);
}

:deep(.el-button--primary:active) {
  transform: translateY(0);
}

/* 响应式 */
@media (max-width: 1024px) {
  .login-card {
    padding: 36px 28px;
  }
}
</style>
