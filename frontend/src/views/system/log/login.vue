<template>
  <div class="page-container space-y-4">
    <div><h2 class="text-xl font-bold text-gray-800">登录日志</h2><p class="text-sm text-gray-400 mt-1">查看用户登录记录</p></div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="登录账号">
          <el-input v-model="query.username" clearable placeholder="请输入" style="width:150px" />
        </el-form-item>
        <el-form-item label="登录状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
            <el-option label="成功" :value="0" />
            <el-option label="失败" :value="1" />
            <el-option label="锁定" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录IP">
          <el-input v-model="query.loginIp" clearable placeholder="请输入" style="width:150px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
      <el-table :data="tableData" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="username" label="登录账号" width="120" />
        <el-table-column prop="loginIp" label="登录IP" width="150" />
        <el-table-column prop="location" label="登录地点" min-width="140" show-overflow-tooltip />
        <el-table-column prop="browser" label="浏览器" width="120" show-overflow-tooltip />
        <el-table-column prop="os" label="操作系统" width="120" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="登录时间" width="170" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="row.status === 1" type="danger" size="small">失败</el-tag>
            <el-tag v-else-if="row.status === 2" type="warning" size="small">锁定</el-tag>
            <el-tag v-else type="info" size="small">未知</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="备注" min-width="150" show-overflow-tooltip />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { logApi } from '@/api'

const loading = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const query = reactive({
  username: '',
  status: null,
  loginIp: '',
  timeRange: null
})

/** 构建请求参数 */
function buildParams() {
  const params = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    username: query.username || undefined,
    status: query.status !== null && query.status !== '' ? query.status : undefined,
    loginIp: query.loginIp || undefined
  }
  if (query.timeRange && query.timeRange.length === 2) {
    params.beginTime = query.timeRange[0]
    params.endTime = query.timeRange[1]
  }
  return params
}

async function fetchData() {
  loading.value = true
  try {
    const res = await logApi.getLoginLogPage(buildParams())
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (_) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { username: '', status: null, loginIp: '', timeRange: null })
  pageNum.value = 1
  fetchData()
}

onMounted(() => fetchData())
</script>

<style scoped>.page-container { padding: 0; }</style>
