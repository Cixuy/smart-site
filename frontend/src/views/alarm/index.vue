<template>
  <div class="space-y-4 p-4 lg:p-6">
    <!-- 页头 -->
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 class="text-xl font-bold text-gray-800">告警管理</h2>
        <p class="mt-1 text-sm text-gray-400">设备监测 / 环境监测 / AI识别 告警统一查询、接单与处置</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button :icon="Refresh" @click="refresh">刷新</el-button>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="rounded-xl border border-gray-100 bg-white p-4 shadow-sm">
      <el-form :inline="true" :model="query" class="flex flex-wrap" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="编号 / 来源 / 内容模糊搜索"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="告警来源">
          <el-select v-model="query.sourceType" placeholder="全部" clearable style="width: 130px" @change="handleSourceChange">
            <el-option v-for="(meta, key) in ALARM_SOURCE" :key="key" :label="meta.label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="query.alarmLevel" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(meta, key) in ALARM_LEVEL" :key="key" :label="meta.label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(meta, key) in ALARM_STATUS" :key="key" :label="meta.label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警类型">
          <el-select v-model="query.alarmTypeCode" placeholder="全部" clearable filterable style="width: 190px">
            <el-option-group v-for="group in filteredTypeGroups" :key="group.source" :label="group.label">
              <el-option v-for="opt in group.options" :key="opt.code" :label="`${opt.code} ${opt.desc}`" :value="opt.code" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="告警时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="rounded-xl border border-gray-100 bg-white p-4 shadow-sm">
      <div class="mb-3 flex items-center justify-between">
        <span class="text-sm font-semibold text-gray-700">
          告警列表
          <span class="ml-1 text-xs font-normal text-gray-400">共 {{ total }} 条 · 未处置置顶 → 控制>警报>预警 → 时间倒序</span>
        </span>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column type="index" label="#" width="56" align="center" :index="indexMethod" />
        <el-table-column prop="alarmNo" label="告警编号" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="cursor-pointer text-blue-600 hover:underline" @click="openDetail(row)">{{ row.alarmNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警内容" min-width="220">
          <template #default="{ row }">
            <div class="flex items-center gap-1.5">
              <span class="truncate font-medium text-gray-800">{{ row.alarmTypeDesc || row.alarmTypeCode || '告警' }}</span>
              <el-tag v-if="row.relatedCount > 0" size="small" type="info" effect="plain">关联 {{ row.relatedCount }}</el-tag>
              <el-tag v-if="row.recovered" size="small" type="success" effect="plain">已恢复</el-tag>
            </div>
            <div class="mt-0.5 truncate text-xs text-gray-400" :title="row.alarmContent">{{ row.alarmContent }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="104" align="center">
          <template #default="{ row }">
            <el-tag :type="dictMeta(ALARM_SOURCE, row.sourceType).tag" effect="light" size="small">{{ row.sourceTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="84" align="center">
          <template #default="{ row }">
            <el-tag :type="dictMeta(ALARM_LEVEL, row.alarmLevel).tag" effect="dark" size="small">{{ row.alarmLevelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置状态" width="94" align="center">
          <template #default="{ row }">
            <el-tag :type="dictMeta(ALARM_STATUS, row.status).tag" effect="light" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联对象" min-width="160">
          <template #default="{ row }">
            <template v-if="row.deviceName">
              <div class="text-gray-700">{{ row.deviceName }}</div>
              <div v-if="row.monitorPointName" class="text-xs text-gray-400">{{ row.monitorPointName }}</div>
            </template>
            <template v-else-if="row.cameraName">
              <div class="text-gray-700">{{ row.cameraName }}</div>
              <div class="text-xs text-gray-400">AI识别</div>
            </template>
            <span v-else class="text-gray-300">-</span>
          </template>
        </el-table-column>
        <el-table-column label="当前值 / 阈值" width="140" align="center">
          <template #default="{ row }">
            <div class="font-medium text-gray-700">{{ row.currentValue ?? '-' }}</div>
            <div class="truncate text-xs text-gray-400" :title="row.thresholdRange">{{ row.thresholdRange || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="告警时间" width="172" align="center">
          <template #default="{ row }">
            <span class="text-gray-600">{{ formatTime(row.alarmTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" text type="warning" size="small" @click="handleClaim(row)">接单</el-button>
            <el-button v-if="row.status !== 2" text type="primary" size="small" @click="openDisposal(row)">处置</el-button>
            <el-button text type="info" size="small" @click="openDetail(row)">详情</el-button>
            <el-button text type="info" size="small" @click="showHistory(row)">历史</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无告警记录" :image-size="80" />
        </template>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="fetchData"
        />
      </div>
    </div>

    <!-- 处置弹窗 -->
    <el-dialog v-model="disposalVisible" title="告警处置" width="560px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="disposalFormRef" :model="disposalForm" :rules="disposalRules" label-width="90px">
        <el-form-item label="告警信息">
          <div class="rounded-lg bg-gray-50 px-3 py-2 leading-6">
            <div class="flex items-center gap-2">
              <span class="font-semibold text-gray-800">{{ disposalForm.alarmNo }}</span>
              <el-tag :type="dictMeta(ALARM_LEVEL, disposalForm.alarmLevel).tag" effect="dark" size="small">{{ disposalForm.alarmLevelName }}</el-tag>
              <el-tag :type="dictMeta(ALARM_STATUS, disposalForm.status).tag" effect="light" size="small">{{ disposalForm.statusName }}</el-tag>
            </div>
            <div class="mt-1 text-sm text-gray-500">{{ disposalForm.alarmContent }}</div>
            <div v-if="disposalForm.alarmTime" class="mt-0.5 text-xs text-gray-400">告警时间：{{ formatTime(disposalForm.alarmTime) }}</div>
          </div>
        </el-form-item>
        <el-form-item label="处置内容" prop="disposalContent">
          <el-input v-model="disposalForm.disposalContent" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写处置措施与说明（如：已安排人员排查、已停机检修等）" />
        </el-form-item>
        <el-form-item label="处置结论" prop="disposalConclusion">
          <el-radio-group v-model="disposalForm.disposalConclusion">
            <el-radio :value="1">属实</el-radio>
            <el-radio :value="2">误报</el-radio>
            <el-radio :value="3">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="disposalForm.disposalConclusion === 2" label="误报原因" prop="falseReason">
          <el-input v-model="disposalForm.falseReason" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选择【误报】时必须填写误报原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disposalVisible = false">取消</el-button>
        <el-button type="primary" :loading="disposalLoading" @click="submitDisposal">确认处置</el-button>
      </template>
    </el-dialog>

    <!-- 处置与恢复历史弹窗 -->
    <el-dialog v-model="historyVisible" title="处置与恢复历史" width="620px" destroy-on-close>
      <div v-loading="historyLoading">
        <template v-if="historyData">
          <el-alert v-if="!historyData.disposalRecords?.length && !historyData.recoveryRecord" title="暂无处置与恢复记录" type="info" :closable="false" show-icon />
          <template v-else>
            <div v-if="historyData.disposalRecords?.length" class="mb-5">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
                <el-icon><Document /></el-icon> 处置记录
              </div>
              <el-timeline style="padding-left: 4px">
                <el-timeline-item v-for="(item, idx) in historyData.disposalRecords" :key="idx" :timestamp="formatTime(item.disposalTime)" placement="top" type="primary">
                  <div class="rounded-lg border border-gray-100 bg-gray-50 p-3">
                    <div class="flex items-center gap-2">
                      <span class="font-medium text-gray-800">{{ item.operatorName || '系统操作员' }}</span>
                      <el-tag :type="dictMeta(DISPOSAL_CONCLUSION, item.disposalConclusion).tag" effect="light" size="small">{{ dictMeta(DISPOSAL_CONCLUSION, item.disposalConclusion).label }}</el-tag>
                    </div>
                    <p class="mt-1.5 whitespace-pre-wrap text-sm text-gray-600">{{ item.disposalContent }}</p>
                    <p v-if="item.falseReason" class="mt-1 text-xs text-amber-600">误报原因：{{ item.falseReason }}</p>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
            <div v-if="historyData.recoveryRecord" class="rounded-lg border border-emerald-100 bg-emerald-50 p-3">
              <div class="flex items-center gap-2 text-sm font-semibold text-emerald-700">
                <el-icon><CircleCheck /></el-icon> 恢复正常
                <span class="ml-auto text-xs font-normal text-emerald-500">{{ formatTime(historyData.recoveryRecord.recoveryTime) }}</span>
              </div>
              <p class="mt-1.5 text-sm text-emerald-600">
                恢复值：{{ historyData.recoveryRecord.recoveryValue ?? '-' }}
                <span v-if="historyData.recoveryRecord.recoveryNote" class="ml-2 text-emerald-500">备注：{{ historyData.recoveryRecord.recoveryNote }}</span>
              </p>
            </div>
          </template>
        </template>
      </div>
      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 详情工作台弹窗 -->
    <el-dialog v-model="detailVisible" title="告警详情" width="880px" :close-on-click-modal="false" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="告警编号"><span class="font-medium text-gray-800">{{ detail.alarmNo }}</span></el-descriptions-item>
            <el-descriptions-item label="告警来源">
              <el-tag :type="dictMeta(ALARM_SOURCE, detail.sourceType).tag" effect="light" size="small">{{ detail.sourceTypeText || detail.sourceTypeName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="告警级别">
              <el-tag :type="dictMeta(ALARM_LEVEL, detail.alarmLevel).tag" effect="dark" size="small">{{ detail.alarmLevelText || detail.alarmLevelName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处置状态">
              <el-tag :type="dictMeta(ALARM_STATUS, detail.status).tag" effect="light" size="small">{{ detail.statusText || detail.statusName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="告警时间">{{ formatTime(detail.alarmTime) }}</el-descriptions-item>
            <el-descriptions-item label="恢复状态">
              <el-tag v-if="detail.recovered" type="success" size="small" effect="plain">已恢复 {{ formatTime(detail.recoveryTime) }}</el-tag>
              <span v-else class="text-gray-400">未恢复</span>
            </el-descriptions-item>
            <el-descriptions-item label="告警类型">{{ detail.alarmTypeText || detail.alarmTypeDesc || detail.alarmTypeCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前值 / 阈值">{{ detail.currentValue ?? '-' }} <span v-if="detail.thresholdRange" class="text-gray-400">/ {{ detail.thresholdRange }}</span></el-descriptions-item>
            <el-descriptions-item label="最新推送">
              <el-tag v-if="detail.latestPushStatus !== null && detail.latestPushStatus !== undefined" :type="dictMeta(ALARM_PUSH_STATUS, detail.latestPushStatus).tag" size="small" effect="plain">{{ dictMeta(ALARM_PUSH_STATUS, detail.latestPushStatus).label }}</el-tag>
              <span v-else class="text-gray-400">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="关联对象" :span="2">
              <template v-if="detail.deviceName || detail.cameraName">
                <span>{{ detail.deviceName || detail.cameraName }}</span>
                <span v-if="detail.monitorPointName" class="ml-2 text-gray-500">{{ detail.monitorPointName }}</span>
                <span v-if="detail.deviceTypeName" class="ml-2 text-gray-400">（{{ detail.deviceTypeName }}）</span>
              </template>
              <span v-else class="text-gray-400">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="告警内容" :span="3"><span class="text-gray-700">{{ detail.alarmContent }}</span></el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.screenshotUrls?.length" class="mt-4">
            <div class="mb-2 text-sm font-semibold text-gray-700">AI 截图</div>
            <div class="flex flex-wrap gap-3">
              <img v-for="(url, i) in detail.screenshotUrls" :key="i" :src="url" class="h-24 w-36 cursor-pointer rounded-lg border border-gray-200 object-cover transition hover:shadow-md" @click="previewUrl = url" />
            </div>
          </div>

          <div class="mt-4 flex items-center gap-2 border-t border-gray-100 pt-3">
            <el-button v-if="detail.status === 0" type="warning" :loading="claimLoading" @click="handleClaim(detail)">接单处理</el-button>
            <el-button v-if="detail.status !== 2" type="primary" @click="openDisposal(detail)">处置告警</el-button>
            <el-button @click="loadDetail(detail.id)">刷新详情</el-button>
          </div>

          <el-tabs v-model="detailTab" class="mt-3" type="border-card">
            <el-tab-pane label="处置记录" name="disposals">
              <el-timeline v-if="detail.disposalRecords?.length" style="padding-left: 4px">
                <el-timeline-item v-for="(item, idx) in detail.disposalRecords" :key="idx" :timestamp="formatTime(item.disposalTime)" placement="top" type="primary">
                  <div class="rounded-lg bg-gray-50 p-3">
                    <div class="flex items-center gap-2">
                      <span class="font-medium text-gray-800">{{ item.operatorName || '系统操作员' }}</span>
                      <el-tag :type="dictMeta(DISPOSAL_CONCLUSION, item.disposalConclusion).tag" effect="light" size="small">{{ dictMeta(DISPOSAL_CONCLUSION, item.disposalConclusion).label }}</el-tag>
                    </div>
                    <p class="mt-1 whitespace-pre-wrap text-sm text-gray-600">{{ item.disposalContent }}</p>
                    <p v-if="item.falseReason" class="mt-1 text-xs text-amber-600">误报原因：{{ item.falseReason }}</p>
                  </div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无处置记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane label="恢复记录" name="recovery">
              <div v-if="detail.recoveryRecord" class="rounded-lg border border-emerald-100 bg-emerald-50 p-4">
                <div class="flex items-center gap-2 text-sm font-semibold text-emerald-700">
                  <el-icon><CircleCheck /></el-icon> 恢复正常
                  <span class="ml-auto text-xs font-normal text-emerald-500">{{ formatTime(detail.recoveryRecord.recoveryTime) }}</span>
                </div>
                <p class="mt-2 text-sm text-emerald-600">
                  恢复值：{{ detail.recoveryRecord.recoveryValue ?? '-' }}
                  <span v-if="detail.recoveryRecord.recoveryNote" class="ml-2 text-emerald-500">备注：{{ detail.recoveryRecord.recoveryNote }}</span>
                </p>
              </div>
              <el-empty v-else description="尚未恢复" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane label="关联告警" name="related">
              <el-table v-if="detail.relatedAlarms?.length" :data="detail.relatedAlarms" size="small" stripe border>
                <el-table-column prop="alarmNo" label="告警编号" min-width="140">
                  <template #default="{ row }">
                    <span class="cursor-pointer text-blue-600 hover:underline" @click="loadDetail(row.id)">{{ row.alarmNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="alarmContent" label="告警内容" min-width="180" show-overflow-tooltip />
                <el-table-column label="来源" width="96" align="center">
                  <template #default="{ row }">
                    <el-tag :type="dictMeta(ALARM_SOURCE, row.sourceType).tag" effect="light" size="small">{{ row.sourceTypeText || row.sourceTypeName }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="级别" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="dictMeta(ALARM_LEVEL, row.alarmLevel).tag" effect="dark" size="small">{{ row.alarmLevelText || row.alarmLevelName }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="dictMeta(ALARM_STATUS, row.status).tag" effect="light" size="small">{{ row.statusText || row.statusName }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="告警时间" width="150" align="center">
                  <template #default="{ row }">{{ formatTime(row.alarmTime) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="无关联告警" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane label="推送记录" name="pushLogs">
              <el-table v-loading="pushLoading" :data="pushLogs" size="small" stripe border>
                <el-table-column label="推送渠道" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="dictMeta(ALARM_PUSH_CHANNEL, row.pushChannel).tag" effect="light" size="small">{{ dictMeta(ALARM_PUSH_CHANNEL, row.pushChannel).label }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="pushTarget" label="接收方" min-width="140" show-overflow-tooltip />
                <el-table-column label="推送状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="dictMeta(ALARM_PUSH_STATUS, row.pushStatus).tag" effect="light" size="small">{{ dictMeta(ALARM_PUSH_STATUS, row.pushStatus).label }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="推送时间" width="150" align="center">
                  <template #default="{ row }">{{ formatTime(row.pushTime) }}</template>
                </el-table-column>
                <el-table-column label="响应" min-width="150">
                  <template #default="{ row }">
                    <div v-if="row.responseMsg" class="truncate text-xs text-gray-500" :title="row.responseMsg">{{ row.responseMsg }}</div>
                    <span v-else class="text-gray-300">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="retryCount" label="重试次数" width="80" align="center" />
                <el-table-column label="操作" width="90" align="center">
                  <template #default="{ row }">
                    <el-button v-if="row.pushStatus === 2" text type="warning" size="small" :loading="retryingId === row.id" @click="handleRetryPush(row)">重试</el-button>
                    <span v-else class="text-gray-300">-</span>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                v-if="pushTotal > 0"
                v-model:current-page="pushPage"
                v-model:page-size="pushSize"
                class="mt-3 justify-end"
                :total="pushTotal"
                :page-sizes="[5, 10, 20]"
                layout="total, sizes, prev, pager, next"
                small
                background
                @size-change="loadPushLogs"
                @current-change="loadPushLogs"
              />
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <el-image-viewer v-if="previewUrl" :url-list="[previewUrl]" @close="previewUrl = ''" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, CircleCheck, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import alarmApi from '@/api/alarm'
import {
  ALARM_SOURCE,
  ALARM_LEVEL,
  ALARM_STATUS,
  ALARM_TYPE_GROUPS,
  ALARM_PUSH_CHANNEL,
  ALARM_PUSH_STATUS,
  DISPOSAL_CONCLUSION,
  getAlarmTypeDesc,
  dictMeta
} from '@/constants/alarm'

const store = useStore()
const route = useRoute()
const projectId = computed(() => store.getters.projectId)

/* ---------------- 查询条件 ---------------- */
const query = reactive({
  keyword: '',
  sourceType: null,
  alarmLevel: null,
  alarmTypeCode: '',
  status: null,
  // 2026-08-19 新增：deviceId 用于接收场景详情页"全部告警"跳转带过来的塔吊设备ID，
  // 按设备筛选展示该塔吊的全部告警；int64 长整型统一以字符串存储传递，避免精度丢失
  deviceId: '',
  startTime: '',
  endTime: ''
})
const timeRange = ref(null)

/** 告警类型下拉随来源联动 */
const filteredTypeGroups = computed(() =>
  query.sourceType ? ALARM_TYPE_GROUPS.filter(g => g.source === query.sourceType) : ALARM_TYPE_GROUPS
)

function handleSourceChange() {
  query.alarmTypeCode = ''
}

/* ---------------- 列表数据 ---------------- */
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

/**
 * 归一化新旧接口字段差异：
 *  - 统一告警列表 /api/alarms 返回 sourceTypeText / alarmLevelText / statusText / alarmTypeText
 *  - record 接口返回 sourceTypeName / alarmLevelName / statusName / alarmTypeDesc
 */
function normalizeRow(row) {
  if (!row) return row
  return {
    ...row,
    sourceTypeName: row.sourceTypeName || row.sourceTypeText || dictMeta(ALARM_SOURCE, row.sourceType).label,
    alarmLevelName: row.alarmLevelName || row.alarmLevelText || dictMeta(ALARM_LEVEL, row.alarmLevel).label,
    statusName: row.statusName || row.statusText || dictMeta(ALARM_STATUS, row.status).label,
    alarmTypeDesc: row.alarmTypeDesc || row.alarmTypeText || getAlarmTypeDesc(row.alarmTypeCode)
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await alarmApi.getPageList({
      ...query,
      projectId: projectId.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      startTime: timeRange.value?.[0] || '',
      endTime: timeRange.value?.[1] || ''
    })
    const data = res.data || {}
    tableData.value = (data.records || data.list || []).map(normalizeRow)
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchData()
}

function resetQuery() {
  Object.assign(query, {
    keyword: '',
    sourceType: null,
    alarmLevel: null,
    alarmTypeCode: '',
    status: null,
    deviceId: '',
    startTime: '',
    endTime: ''
    })
    timeRange.value = null
    handleSearch()
    }

function refresh() {
  fetchData()
}

function handleSizeChange() {
  pageNum.value = 1
  fetchData()
}

const indexMethod = index => (pageNum.value - 1) * pageSize.value + index + 1

/* ---------------- 接单处理 ---------------- */
const claimLoading = ref(false)

async function handleClaim(row) {
  try {
    await ElMessageBox.confirm(`确认接单处理告警【${row.alarmNo}】？接单后状态将变为"处置中"。`, '接单确认', {
      type: 'warning',
      confirmButtonText: '确认接单',
      cancelButtonText: '取消'
    })
  } catch (_) {
    return
  }
  claimLoading.value = true
  try {
    await alarmApi.claimAlarm(row.id)
    ElMessage.success('接单成功')
    fetchData()
    if (detailVisible.value && detail.value?.id === row.id) loadDetail(row.id)
  } finally {
    claimLoading.value = false
  }
}

/* ---------------- 逻辑删除 ---------------- */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定逻辑删除告警【${row.alarmNo}】？仅系统管理员可执行，未处置/处置中的告警禁止删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch (_) {
    return
  }
  try {
    await alarmApi.removeAlarm(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (_) {
    /* 错误信息由拦截器统一提示（如 403 无权限 / 400 未处置禁止删除） */
  }
}

/* ---------------- 告警处置 ---------------- */
const disposalVisible = ref(false)
const disposalLoading = ref(false)
const disposalFormRef = ref()
const disposalForm = reactive({
  alarmId: null,
  alarmNo: '',
  alarmContent: '',
  alarmLevel: null,
  alarmLevelName: '',
  status: null,
  statusName: '',
  alarmTime: '',
  disposalContent: '',
  disposalConclusion: null,
  falseReason: ''
})

const disposalRules = {
  disposalContent: [{ required: true, message: '请填写处置内容', trigger: 'blur' }],
  disposalConclusion: [{ required: true, message: '请选择处置结论', trigger: 'change' }],
  falseReason: [
    {
      validator: (rule, value, callback) => {
        if (disposalForm.disposalConclusion === 2 && !value?.trim()) {
          callback(new Error('误报时请填写误报原因'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openDisposal(row) {
  const src = normalizeRow(row)
  Object.assign(disposalForm, {
    alarmId: src.id,
    alarmNo: src.alarmNo,
    alarmContent: src.alarmContent,
    alarmLevel: src.alarmLevel,
    alarmLevelName: src.alarmLevelName,
    status: src.status,
    statusName: src.statusName,
    alarmTime: src.alarmTime,
    disposalContent: '',
    disposalConclusion: null,
    falseReason: ''
  })
  disposalVisible.value = true
}

async function submitDisposal() {
  await disposalFormRef.value.validate()
  disposalLoading.value = true
  try {
    await alarmApi.dispose({
      alarmId: disposalForm.alarmId,
      disposalContent: disposalForm.disposalContent.trim(),
      disposalConclusion: disposalForm.disposalConclusion,
      falseReason: disposalForm.disposalConclusion === 2 ? disposalForm.falseReason.trim() : null
    })
    ElMessage.success('处置成功')
    disposalVisible.value = false
    fetchData()
    if (detailVisible.value && detail.value?.id === disposalForm.alarmId) loadDetail(disposalForm.alarmId)
  } finally {
    disposalLoading.value = false
  }
}

/* ---------------- 处置与恢复历史 ---------------- */
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyData = ref(null)

async function showHistory(row) {
  historyVisible.value = true
  historyData.value = null
  historyLoading.value = true
  try {
    const res = await alarmApi.getHistory(row.id)
    historyData.value = res.data || {}
  } finally {
    historyLoading.value = false
  }
}

/* ---------------- 详情工作台 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailTab = ref('disposals')
const previewUrl = ref('')

async function openDetail(row) {
  detailVisible.value = true
  detail.value = null
  detailTab.value = 'disposals'
  await loadDetail(row.id)
}

async function loadDetail(id) {
  detailLoading.value = true
  try {
    const res = await alarmApi.getDetail(id)
    detail.value = normalizeRow(res.data)
    loadPushLogs()
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 推送记录 ---------------- */
const pushLogs = ref([])
const pushTotal = ref(0)
const pushPage = ref(1)
const pushSize = ref(10)
const pushLoading = ref(false)
const retryingId = ref(null)

async function loadPushLogs() {
  if (!detail.value?.id) return
  pushLoading.value = true
  try {
    const res = await alarmApi.getPushLogs(detail.value.id, {
      pageNum: pushPage.value,
      pageSize: pushSize.value
    })
    const data = res.data || {}
    pushLogs.value = data.records || []
    pushTotal.value = data.total || 0
  } finally {
    pushLoading.value = false
  }
}

async function handleRetryPush(row) {
  try {
    await ElMessageBox.confirm(`确认重试该推送记录？仅失败（状态=2）的记录允许重试。`, '重试确认', {
      type: 'warning',
      confirmButtonText: '确认重试',
      cancelButtonText: '取消'
    })
  } catch (_) {
    return
  }
  retryingId.value = row.id
  try {
    await alarmApi.retryPush(row.id)
    ElMessage.success('重试成功')
    loadPushLogs()
  } finally {
    retryingId.value = null
  }
}

/* ---------------- 工具 ---------------- */
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  // 2026-08-19 新增：支持从可视化场景详情页携带 deviceId 跳转过来，
  // 自动按该塔吊筛选告警；点击"重置"按钮即可清除筛选查看全部告警
  const qDeviceId = route.query.deviceId
  if (qDeviceId) {
    query.deviceId = String(qDeviceId)
  }
  fetchData()
})
</script>
