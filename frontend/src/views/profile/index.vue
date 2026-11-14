<template>
  <div class="page-container max-w-3xl mx-auto space-y-6">
    <!-- 页面标题 -->
    <div>
      <h2 class="text-xl font-bold text-gray-800">个人中心</h2>
      <p class="text-sm text-gray-400 mt-1">查看和修改您的个人信息</p>
    </div>

    <!-- 基本信息卡片 -->
    <div class="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
      <h3 class="text-base font-semibold text-gray-700 mb-5 flex items-center gap-2">
        <el-icon :size="18"><User /></el-icon> 基本信息
      </h3>

      <div class="flex flex-col sm:flex-row items-start gap-6">
        <!-- 头像区域 -->
        <div class="flex flex-col items-center gap-3 shrink-0">
          <el-avatar :size="80" :src="avatar" class="shadow-sm">
            <el-icon :size="40"><UserFilled /></el-icon>
          </el-avatar>
          <span class="text-xs text-gray-400">头像</span>
        </div>

        <!-- 信息展示 -->
        <div class="flex-1 grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
          <div class="info-item">
            <span class="info-label">用户名</span>
            <span class="info-value">{{ username }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">真实姓名</span>
            <span class="info-value">{{ realName || '未设置' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">手机号</span>
            <span class="info-value">{{ phone || '未设置' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">邮箱</span>
            <span class="info-value">{{ email || '未设置' }}</span>
          </div>
          <div class="info-item sm:col-span-2">
            <span class="info-label">角色</span>
            <span class="info-value">
              <el-tag v-for="r in roles" :key="r.id || r" size="small" class="mr-1" type="info">
                {{ typeof r === 'object' ? r.roleName : r }}
              </el-tag>
              <span v-if="!roles.length" class="text-gray-400">无</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑信息卡片 -->
    <div class="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
      <h3 class="text-base font-semibold text-gray-700 mb-5 flex items-center gap-2">
        <el-icon :size="18"><EditPen /></el-icon> 编辑个人信息
      </h3>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        label-position="right"
        class="max-w-lg"
      >
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" clearable />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存修改
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { User, UserFilled, EditPen } from '@element-plus/icons-vue'

const store = useStore()

// 从 store 获取当前用户信息
const username = computed(() => store.getters.username)
const realName = computed(() => store.getters.realName)
const email = computed(() => store.getters.email)
const phone = computed(() => store.getters.phone)
const avatar = computed(() => store.getters.avatar)
const roles = computed(() => store.getters.roles)

// 表单
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  realName: '',
  phone: '',
  email: ''
})

// 邮箱校验
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const validateEmail = (_rule, value, callback) => {
  if (value && !emailPattern.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

// 手机号校验
const phonePattern = /^1[3-9]\d{9}$/
const validatePhone = (_rule, value, callback) => {
  if (value && !phonePattern.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  } else {
    callback()
  }
}

const rules = {
  email: [{ validator: validateEmail, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }]
}

// 初始化表单数据
function initForm() {
  form.realName = realName.value || ''
  form.phone = phone.value || ''
  form.email = email.value || ''
}

// 重置表单
function resetForm() {
  initForm()
  formRef.value?.clearValidate()
}

// 提交更新
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await store.dispatch('user/updateProfile', {
      realName: form.realName,
      phone: form.phone,
      email: form.email
    })
    ElMessage.success('个人信息修改成功')
  } catch (e) {
    // 错误已在 request 拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  initForm()
})

// 当 store 中数据变化时同步到表单（例如页面不刷新的情况下重新获取了用户信息）
watch([realName, phone, email], () => {
  form.realName = realName.value || ''
  form.phone = phone.value || ''
  form.email = email.value || ''
})
</script>

<style scoped>
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-label {
  font-size: 12px;
  color: #909399;
}

.info-value {
  font-size: 14px;
  color: #303133;
  word-break: break-all;
}

.page-container {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
