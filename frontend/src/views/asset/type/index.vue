<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-800">设备类型管理</h2>
        <p class="text-sm text-gray-400 mt-1">管理设备类型的分类树结构</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增类型</el-button>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" row-key="id" border :tree-props="{ children: 'children', hasChildren: 'hasChildren' }" style="width:100%">
        <el-table-column prop="typeName" label="类型名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="typeCode" label="编码" width="140" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="remark" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备类型' : '新增设备类型'" width="520px" :close-on-click-modal="false" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="类型名称" prop="typeName">
          <el-input v-model="form.typeName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="父级类型">
          <el-tree-select v-model="form.parentId" :data="tableData"
            :props="{ label: 'typeName', value: 'id', children: 'children' }" check-strictly clearable placeholder="无（顶级）" />
        </el-form-item>
        <el-form-item label="编码" prop="typeCode">
          <el-input v-model="form.typeCode" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="层级" prop="level">
          <el-input-number v-model="form.level" :min="1" :max="4" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-select v-model="form.status" placeholder="请选择">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入" />
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
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { assetApi } from '@/api'

const loading = ref(false); const submitLoading = ref(false)
const dialogVisible = ref(false); const isEdit = ref(false)
const formRef = ref(null); const tableData = ref([])
const form = reactive({ id: null, typeName: '', parentId: null, typeCode: '', level: 1, sort: 0, status: 1, remark: '' })
const rules = {
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  typeCode: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

function withTimeout(promise, ms = 5000) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('TIMEOUT')), ms))
  ])
}

async function fetchData() {
  loading.value = true
  try {
    // 并行请求：树结构 + 平铺列表（含 createTime/remark）
    const [treeRes, listRes] = await Promise.all([
      withTimeout(assetApi.getTypeTree(), 5000),
      withTimeout(assetApi.getTypePage({}), 5000)
    ])
    const tree = treeRes?.data?.tree ?? []
    const list = listRes?.data?.list ?? []

    // 用列表数据补充树节点缺失的 createTime / remark
    const extraMap = new Map(list.map(item => [item.id, item]))
    function enrich(nodes) {
      for (const node of nodes) {
        const extra = extraMap.get(node.id)
        if (extra) {
          node.createTime = extra.createTime
          node.remark = extra.remark
        }
        if (node.children?.length) enrich(node.children)
      }
    }
    enrich(tree)
    tableData.value = Array.isArray(tree) ? tree : []
  } catch (err) {
    tableData.value = []
    if (err?.message === 'TIMEOUT') {
      ElMessage.warning('网络连接异常，请检查网络后刷新重试')
    }
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  isEdit.value = !!row
  Object.assign(form, row ? { ...row } : { id: null, typeName: '', parentId: null, typeCode: '', level: 1, sort: 0, status: 1, remark: '' })
  dialogVisible.value = true
}

function resetForm() { formRef.value?.resetFields() }

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (isEdit.value) await assetApi.updateType({ ...form })
    else await assetApi.createType({ ...form })
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (_) { /* ignore */ }
  finally { submitLoading.value = false }
}

async function handleDelete(row) {
  try { await assetApi.deleteTypeBatch([row.id]); ElMessage.success('删除成功'); fetchData() } catch (_) { /* ignore */ }
}

onMounted(() => fetchData())
</script>

<style scoped>.page-container { padding: 0; }</style>
