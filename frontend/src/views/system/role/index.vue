<template>
  <div class="page-container space-y-4">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">角色管理</h2>
        <p class="text-sm text-gray-400 mt-1">管理系统角色与菜单权限分配</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增角色</el-button>
    </div>

    <!-- 搜索筛选栏 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="角色名称">
          <el-input v-model="query.roleName" placeholder="请输入" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="query.roleCode" placeholder="请输入" clearable style="width:160px" />
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
        <el-table-column prop="roleName" label="角色名称" width="140" />
        <el-table-column prop="roleCode" label="角色编码" width="150" />
        <el-table-column label="数据权限" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="dataScopeType(row.dataScope)" size="small">
              {{ dataScopeLabel(row.dataScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="内置" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isSystem === 1 ? 'warning' : 'info'" size="small">
              {{ row.isSystem === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" :disabled="isSystemAdmin(row)" @click="openDialog(row)">编辑</el-button>
            <el-button text type="warning" size="small" :icon="Key" :disabled="isSystemAdmin(row)" @click="assignPerms(row)">权限</el-button>
            <el-popconfirm
              title="确认删除？"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button
                  text
                  type="danger"
                  size="small"
                  :icon="Delete"
                  :disabled="isSystemAdmin(row)"
                >
                  删除
                </el-button>
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
      :title="isEdit ? '编辑角色' : '新增角色'"
      width="520px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="isEdit" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-radio-group v-model="form.dataScope">
            <el-radio :value="1">全部</el-radio>
            <el-radio :value="2">自定义</el-radio>
            <el-radio :value="3">仅本人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="显示排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" style="width:160px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permVisible" title="分配菜单权限" width="450px">
      <el-tree
        ref="permTreeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
      />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="permLoading" @click="savePerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Key, Delete, Search, Refresh } from '@element-plus/icons-vue'
import { roleApi, menuApi } from '@/api'

/** 判断是否为系统内置管理员角色（不可编辑/分配权限/删除） */
function isSystemAdmin(row) {
  return row.roleCode === 'SYSTEM_ADMIN'
    || row.roleCode === 'admin'
    || row.isSystem === 1
    || row.roleName === '系统管理员'
}

// ---------------- 表格数据 ----------------
const loading = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ---------------- 搜索条件 ----------------
const query = reactive({
  roleName: '',
  roleCode: ''
})

// ---------------- 对话框控制 ----------------
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

// ---------------- 表单数据 ----------------
const defaultForm = () => ({
  id: null,
  roleName: '',
  roleCode: '',
  dataScope: 1,
  sort: 0,
  remark: ''
})
const form = reactive(defaultForm())

const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据权限', trigger: 'change' }]
}

// ---------------- 权限分配 ----------------
const permVisible = ref(false)
const permLoading = ref(false)
const permTreeRef = ref(null)
const menuTree = ref([])
let currentRoleId = null

// ---------------- 数据权限枚举 ----------------
const dataScopeMap = { 1: '全部', 2: '自定义', 3: '仅本人' }
const dataScopeTypeMap = { 1: 'danger', 2: 'warning', 3: 'info' }

function dataScopeLabel(val) {
  return dataScopeMap[val] || '未知'
}

function dataScopeType(val) {
  return dataScopeTypeMap[val] || 'info'
}

// ---------------- 查询角色列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...query
    }
    const res = await roleApi.getPage(params)
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
  Object.assign(query, { roleName: '', roleCode: '' })
  pageNum.value = 1
  fetchData()
}

// ---------------- 新增/编辑 ----------------
function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    // 编辑模式：回填已有数据
    Object.assign(form, {
      id: row.id,
      roleName: row.roleName || '',
      roleCode: row.roleCode || '',
      dataScope: row.dataScope ?? 1,
      sort: row.sort ?? 0,
      remark: row.remark || ''
    })
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
      await roleApi.update({ ...form })
    } else {
      await roleApi.create({ ...form })
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

// ---------------- 删除角色 ----------------
async function handleDelete(row) {
  if (row.isSystem === 1) {
    ElMessage.warning('内置角色不可删除')
    return
  }
  try {
    await roleApi.deleteBatch(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (_) {
    /* 错误已在 request 拦截器中统一处理 */
  }
}

// ---------------- 分配菜单权限 ----------------

async function assignPerms(row) {
  currentRoleId = row.id
  try {
    const [menuRes, listRes] = await Promise.all([
      roleApi.getRoleMenus(row.id),
      menuApi.getList()
    ])

    // 直接以后端返回的平铺列表构建权限树，过滤 root、首页及已下线的消息中心
    const flatList = Array.isArray(listRes.data)
      ? listRes.data
      : (listRes.data?.list || listRes.data?.records || [])
    menuTree.value = flatList
      .filter(m => {
        const name = m.menuName || m.name || ''
        const path = m.path || m.routePath || m.url || ''
        // 过滤无效节点
        if (m.id === 0 || m.id === 1 || name === 'root' || name === '首页') return false
        // 过滤已下线/删除的消息中心（兼容名称或路径两种标识）
        if (name === '消息中心') return false
        if (path === '/message' || path.startsWith('/message/')) return false
        return true
      })
      .map(m => ({
        id: m.id,
        name: m.menuName || m.name || '',
        children: []
      }))

    permVisible.value = true
    await nextTick()

    const roleMenuIds = Array.isArray(menuRes.data)
      ? menuRes.data
      : (menuRes.data?.menuIds || [])
    permTreeRef.value?.setCheckedKeys(roleMenuIds)
  } catch (_) {
    /* 错误已在 request 拦截器中统一处理 */
  }
}

async function savePerms() {
  const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
  const halfCheckedKeys = permTreeRef.value?.getHalfCheckedKeys() || []
  const menuIds = [...checkedKeys, ...halfCheckedKeys]

  permLoading.value = true
  try {
    await roleApi.assignMenus(currentRoleId, { menuIds })
    ElMessage.success('权限分配成功')
    permVisible.value = false
    fetchData()
  } catch (_) {
    /* 错误已在 request 拦截器中统一处理 */
  } finally {
    permLoading.value = false
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
