<template>
  <div class="page-container space-y-4">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">用户管理</h2>
        <p class="text-sm text-gray-400 mt-1">管理系统用户账号与角色分配</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
    </div>

    <!-- 搜索筛选栏 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="登录账号">
          <el-input v-model="query.username" placeholder="请输入" clearable style="width:150px" />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="query.realName" placeholder="请输入" clearable style="width:150px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" placeholder="请输入" clearable style="width:150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:110px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
            <el-option label="锁定" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="username" label="登录账号" width="130" />
        <el-table-column prop="realName" label="真实姓名" width="110" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag
              v-for="r in (row.roles || [])"
              :key="r.id"
              size="small"
              class="mr-1"
            >
              {{ r.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="170" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" :disabled="isSystemAdminUser(row)" @click="openDialog(row)">编辑</el-button>
            <el-button
              v-if="row.status === 2"
              text
              type="success"
              size="small"
              :icon="Unlock"
              :disabled="isSystemAdminUser(row)"
              @click="unlockUser(row)"
            >
              解锁
            </el-button>
            <el-button text type="info" size="small" :icon="Key" :disabled="isSystemAdminUser(row)" @click="resetPwd(row)">重置密码</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small" :icon="Delete" :disabled="isSystemAdminUser(row)">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-4 justify-end"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="fetchData"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="560px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="登录账号" prop="username">
              <el-input v-model="form.username" :disabled="isEdit" placeholder="字母+数字" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="user@example.com" />
            </el-form-item>
          </el-col>
          <el-col v-if="!isEdit" :span="12">
            <el-form-item label="初始密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="分配角色">
          <el-select
            v-model="form.roleIds"
            multiple
            placeholder="请选择角色（可多选）"
            style="width:100%"
          >
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="`${r.roleName} (${r.roleCode})`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Key, Delete, Search, Refresh, Unlock } from '@element-plus/icons-vue'
import { userApi, roleApi } from '@/api'

/** 判断用户是否拥有系统管理员角色 */
function isSystemAdminUser(row) {
  return (row.roles || []).some(r =>
    r.roleCode === 'SYSTEM_ADMIN' || r.code === 'SYSTEM_ADMIN' || r.isSystem === 1
  )
}

// ---------------- 表格数据 ----------------
const loading = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ---------------- 搜索条件 ----------------
const query = reactive({
  username: '',
  realName: '',
  phone: '',
  status: null
})

// ---------------- 对话框控制 ----------------
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

// ---------------- 角色选项 ----------------
const roleOptions = ref([])

// ---------------- 状态枚举 ----------------
const statusMap = { 0: '禁用', 1: '启用', 2: '锁定' }
const statusTagMap = { 0: 'danger', 1: 'success', 2: 'warning' }

function statusLabel(val) {
  return statusMap[val] ?? '未知'
}
function statusTagType(val) {
  return statusTagMap[val] ?? 'info'
}

// ---------------- 表单数据 ----------------
const defaultForm = () => ({
  id: null,
  username: '',
  realName: '',
  phone: '',
  email: '',
  password: '',
  status: 1,
  roleIds: []
})
const form = reactive(defaultForm())

const rules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9]+$/, message: '仅支持字母与数字', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}

// ---------------- 查询用户列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...query
    }
    const res = await userApi.getPage(params)
    // API 返回 PageResult: { total, pages, pageNum, pageSize, records }
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (_) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pageNum.value = 1
  fetchData()
}

function resetQuery() {
  Object.assign(query, { username: '', realName: '', phone: '', status: null })
  pageNum.value = 1
  fetchData()
}

// ---------------- 加载角色选项 ----------------
async function loadRoleOptions() {
  try {
    const res = await roleApi.getAll()
    // API: GET /api/sys/role/all → { data: { list: [...] } }
    roleOptions.value = res.data?.list || res.data || []
  } catch (_) {
    roleOptions.value = []
  }
}

// ---------------- 新增/编辑 ----------------
async function openDialog(row) {
  // 预加载角色选项
  await loadRoleOptions()

  isEdit.value = !!row
  if (row) {
    // 编辑模式：请求详情获取完整角色信息（含 roleIds）
    try {
      const detailRes = await userApi.getById(row.id)
      const detail = detailRes.data || {}
      Object.assign(form, {
        id: detail.id,
        username: detail.username || '',
        realName: detail.realName || '',
        phone: detail.phone || '',
        email: detail.email || '',
        password: '',
        status: detail.status ?? 1,
        // API 返回 roles: [{ id, roleName, roleCode }]
        roleIds: (detail.roles || []).map(r => r.id)
      })
    } catch (_) {
      // 降级：直接用表格行数据
      Object.assign(form, { ...row, password: '', roleIds: (row.roles || []).map(r => r.id) })
    }
  } else {
    // 新增模式：重置为默认值
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (isEdit.value) {
      // API: PUT /api/sys/user → { id, realName, phone, email, status, roleIds }
      await userApi.update({
        id: form.id,
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        status: form.status,
        roleIds: form.roleIds
      })
    } else {
      // API: POST /api/sys/user → { username, password, realName, phone, email, roleIds }
      await userApi.create({
        username: form.username,
        password: form.password,
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        roleIds: form.roleIds
      })
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (_) {
    /* 错误已在 request 拦截器中统一处理 */
  } finally {
    submitLoading.value = false
  }
}

// ---------------- 删除用户 ----------------
async function handleDelete(row) {
  try {
    // API: DELETE /api/sys/user/{ids} — ids 为逗号分隔
    await userApi.deleteBatch(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (_) {
    /* 错误已在 request 拦截器中统一处理 */
  }
}

// ---------------- 解锁用户 ----------------
async function unlockUser(row) {
  try {
    await ElMessageBox.confirm(
      `确认解锁用户「${row.realName || row.username}」？`,
      '解锁用户',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    // API: PUT /api/sys/user/{id}/status → { status: 1 }
    await userApi.updateStatus(row.id, { status: 1 })
    ElMessage.success('用户已解锁')
    fetchData()
  } catch {
    /* 用户取消或接口报错 */
  }
}

// ---------------- 重置密码 ----------------
async function resetPwd(row) {
  try {
    await ElMessageBox.confirm(
      `确定要重置用户「${row.realName || row.username}」的密码吗？系统将生成 6 位随机密码。`,
      '重置密码',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    // API: PUT /api/sys/user/{id}/resetPwd → { data: { newPassword, forceChange } }
    const res = await userApi.resetPassword(row.id)
    const pwdResult = res.data || {}
    const lines = [`新密码：${pwdResult.newPassword || '—'}`]
    if (pwdResult.forceChange) {
      lines.push('该密码为临时密码，首次登录需修改。')
    }
    ElMessageBox.alert(lines.join('\n'), '密码重置成功', {
      confirmButtonText: '知道了',
      type: 'success'
    })
  } catch {
    /* 用户取消 */
  }
}

// ---------------- 初始化 ----------------
onMounted(() => fetchData())
</script>

<style scoped>
.page-container {
  padding: 0;
}
</style>
