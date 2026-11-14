<template>
  <div class="page-container space-y-4">
    <!-- 顶部标题栏 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">数据大屏</h2>
        <p class="text-sm text-gray-400 mt-1">大屏配置的创建、编辑与默认设置，支持进入全屏预览</p>
      </div>
      <div class="flex items-center gap-2">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="1">启用</el-radio-button>
          <el-radio-button :value="0">停用</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新建大屏</el-button>
      </div>
    </div>

    <!-- 大屏列表 -->
    <div v-loading="loading">
      <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        <div
          v-for="item in screenList"
          :key="item.id"
          class="group bg-white rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition-all overflow-hidden cursor-pointer"
          @click="goEditor(item)"
        >
          <!-- 大屏封面 -->
          <div class="relative h-36 bg-gradient-to-br from-slate-800 via-blue-950 to-slate-900 flex items-center justify-center">
            <el-icon :size="44" color="#38bdf8"><DataBoard /></el-icon>
            <div v-if="item.isDefault" class="absolute top-2 left-2">
              <el-tag type="warning" effect="dark" size="small">默认大屏</el-tag>
            </div>
            <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <el-button size="small" type="primary" :icon="View" circle @click.stop="goEditor(item, true)" title="预览" />
              <el-button size="small" type="info" :icon="Setting" circle @click.stop="goEditor(item)" title="编辑" />
            </div>
            <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent px-3 pb-2 pt-6">
              <p class="text-white font-medium truncate">{{ item.screenName }}</p>
            </div>
          </div>

          <!-- 大屏信息 -->
          <div class="p-3 space-y-2">
            <div class="flex items-center gap-2 text-sm">
              <el-tag :type="item.status === 1 ? 'success' : 'info'" size="small" effect="light">
                {{ item.status === 1 ? '已启用' : '已停用' }}
              </el-tag>
              <el-tag v-if="!item.isDefault" type="info" size="small" effect="plain">普通大屏</el-tag>
              <span class="ml-auto text-xs text-gray-400">{{ item.layoutConfig?.components?.length ?? 0 }} 个组件</span>
            </div>
            <div class="flex items-center justify-between text-xs text-gray-400">
              <span><el-icon class="align-middle mr-1"><Timer /></el-icon>刷新：{{ item.refreshInterval || '-' }}s</span>
              <span><el-icon class="align-middle mr-1"><Clock /></el-icon>{{ formatTime(item.createdAt) }}</span>
            </div>
            <!-- 操作区 -->
            <div class="flex items-center gap-2 pt-2 border-t border-gray-50" @click.stop>
              <el-button size="small" :icon="View" @click="goEditor(item, true)">预览</el-button>
              <el-button size="small" :icon="Setting" @click="goEditor(item)">编辑</el-button>
              <el-button
                v-if="!item.isDefault"
                size="small"
                :icon="Star"
                type="warning"
                plain
                :loading="settingDefaultId === item.id"
                @click="setDefault(item)"
              >设为默认</el-button>
              <el-button
                size="small"
                :type="item.status === 1 ? 'info' : 'success'"
                plain
                @click="toggleStatus(item)"
              >{{ item.status === 1 ? '停用' : '启用' }}</el-button>
              <el-popconfirm :title="`确定删除大屏「${item.screenName}」？`" @confirm="delScreen(item)">
                <template #reference>
                  <el-button size="small" type="danger" :icon="Delete" plain />
                </template>
              </el-popconfirm>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && screenList.length === 0" description="暂无数据大屏配置，点击右上角「新建大屏」创建" />
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑大屏配置' : '新建大屏配置'"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="大屏名称" prop="screenName">
          <el-input v-model="form.screenName" maxlength="50" placeholder="如：综合安全态势大屏" />
        </el-form-item>
        <el-form-item label="刷新间隔">
          <el-input-number v-model="form.refreshInterval" :min="3" :max="3600" :step="1" />
          <span class="ml-2 text-xs text-gray-400">秒</span>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Clock, DataBoard, Delete, Plus, Refresh, Setting, Star, Timer, View
} from '@element-plus/icons-vue'
import screenApi from '@/api/screen'

const router = useRouter()

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const screenList = ref([])
const statusFilter = ref(null)

const defaultLayout = () => ({
  gridCols: 12,
  rowHeight: 80,
  components: []
})

async function loadList() {
  loading.value = true
  try {
    const res = await screenApi.getConfigList(
      statusFilter.value !== null ? { status: statusFilter.value } : {}
    )
    screenList.value = res?.data?.records || []
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    loading.value = false
  }
}

/* ---------------- 新建/编辑 ---------------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  screenName: '',
  refreshInterval: 5,
  isDefault: false,
  status: 1,
  layoutConfig: null
})

const formRules = {
  screenName: [{ required: true, message: '请输入大屏名称', trigger: 'blur' }]
}

function openDialog(item) {
  Object.assign(form, {
    id: item?.id ?? null,
    screenName: item?.screenName ?? '',
    refreshInterval: item?.refreshInterval ?? 5,
    isDefault: item?.isDefault ?? false,
    status: item?.status ?? 1,
    layoutConfig: item?.layoutConfig ?? null
  })
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: null, screenName: '', refreshInterval: 5, isDefault: false, status: 1, layoutConfig: null
  })
  formRef.value?.clearValidate()
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      screenName: form.screenName,
      layoutConfig: form.layoutConfig || defaultLayout(),
      refreshInterval: form.refreshInterval,
      isDefault: form.isDefault,
      status: form.status
    }
    if (form.id) {
      await screenApi.updateConfig(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      const res = await screenApi.createConfig(payload)
      ElMessage.success('创建成功')
      // 进入编辑器完善布局
      dialogVisible.value = false
      router.push({ path: '/screen/editor', query: { id: res?.data?.id ?? form.id } })
      return
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    saving.value = false
  }
}

/* ---------------- 操作 ---------------- */
const settingDefaultId = ref(null)

async function setDefault(item) {
  settingDefaultId.value = item.id
  try {
    await screenApi.setDefaultScreen(item.id)
    ElMessage.success(`已将「${item.screenName}」设为默认大屏`)
    loadList()
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    settingDefaultId.value = null
  }
}

async function toggleStatus(item) {
  const next = item.status === 1 ? 0 : 1
  try {
    await screenApi.updateConfig(item.id, {
      screenName: item.screenName,
      layoutConfig: item.layoutConfig || defaultLayout(),
      refreshInterval: item.refreshInterval,
      isDefault: item.isDefault,
      status: next
    })
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    loadList()
  } catch (e) {
    /* 错误由拦截器统一提示 */
  }
}

async function delScreen(item) {
  try {
    await screenApi.deleteConfig(item.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    /* 错误由拦截器统一提示 */
  }
}

function goEditor(item, preview = false) {
  router.push({ path: '/screen/editor', query: { id: item.id, ...(preview ? { preview: 1 } : {}) } })
}

/* ---------------- 工具 ---------------- */
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadList)
</script>
