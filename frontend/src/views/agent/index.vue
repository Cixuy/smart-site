<template>
  <div class="agent-page flex h-[calc(100vh-140px)] gap-0">
    <!-- 左侧历史会话 -->
    <div class="w-64 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col">
      <div class="p-3 border-b border-gray-100 flex items-center justify-between">
        <span class="font-semibold text-gray-700 text-sm">历史对话</span>
        <el-tooltip content="刷新会话列表" placement="top">
          <el-button :icon="Refresh" size="small" text :loading="loadingSessions" @click="loadSessions" />
        </el-tooltip>
      </div>

      <div class="flex-1 overflow-auto p-2 space-y-1">
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="p-2 rounded cursor-pointer hover:bg-gray-50 border border-transparent"
          :class="{ 'bg-blue-50 border-blue-100': s.sessionId === currentSessionId }"
          @click="selectSession(s)"
        >
          <div class="text-sm text-gray-700 truncate" :class="{ 'text-blue-600': s.sessionId === currentSessionId }">{{ s.title || '新对话' }}</div>
          <div class="text-xs text-gray-400 truncate mt-0.5">{{ s.lastMessagePreview || '暂无消息' }}</div>
          <div class="text-xs text-gray-300 mt-0.5">{{ formatTime(s.lastMessageAt || s.createdAt) }}</div>
        </div>

        <el-empty v-if="!loadingSessions && !sessions.length" description="暂无会话" :image-size="44" />
      </div>

      <div class="p-2 border-t border-gray-100">
        <el-button :icon="Plus" type="primary" size="small" class="w-full" @click="startNewChat">新对话</el-button>
      </div>
    </div>

    <!-- 右侧聊天 -->
    <div class="flex-1 flex flex-col bg-gray-50 min-w-0">
      <div class="h-12 bg-white border-b border-gray-200 flex items-center px-4 flex-shrink-0">
        <span class="font-semibold text-gray-700">{{ currentSessionTitle }}</span>
        <el-tag size="small" :type="generating ? 'warning' : 'success'" class="ml-2">
          {{ generating ? '生成中' : '在线' }}
        </el-tag>
        <span class="ml-auto text-xs text-gray-400">AI 安全助手 · 对话智能体</span>
      </div>

      <div ref="msgContainer" class="flex-1 overflow-auto p-4 space-y-4">
        <div v-if="messages.length === 0" class="flex items-center justify-center h-full">
          <div class="text-center text-gray-400">
            <el-icon :size="48"><ChatDotRound /></el-icon>
            <p class="mt-3 text-sm">我是智慧工地 AI 助手</p>
            <p class="text-xs mt-1">可以问我关于设备状态、告警分析、安全规范等问题</p>
            <div class="flex flex-wrap justify-center gap-2 mt-4 max-w-md">
              <el-tag v-for="s in suggestions" :key="s" size="small" class="cursor-pointer" :disabled="generating" @click="sendMsg(s)">{{ s }}</el-tag>
            </div>
          </div>
        </div>

        <div v-for="(msg, i) in messages" :key="msg.key" class="flex" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
          <div class="max-w-[75%]">
            <div
              class="px-4 py-2.5 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap break-words"
              :class="msg.role === 'user' ? 'bg-blue-500 text-white rounded-br-sm' : (msg.failed ? 'bg-red-50 border border-red-200 text-red-600 rounded-bl-sm' : 'bg-white border border-gray-200 text-gray-700 rounded-bl-sm shadow-sm')"
            >
              <span v-if="msg.content">{{ msg.content }}</span>
              <span v-if="msg.generating && !msg.content" class="dot-flashing inline-block align-middle"></span>
              <span v-if="msg.typing" class="typing-cursor inline-block align-middle"></span>
              <span v-if="msg.failed && !msg.messageId" class="inline-flex items-center gap-1"><el-icon><WarningFilled /></el-icon>{{ msg.errorMessage || '服务暂时不可用，请稍后重试' }}</span>
            </div>
            <p class="text-xs text-gray-400 mt-1 px-1 flex items-center gap-2">
              <span>{{ msg.time }}</span>
              <span v-if="msg.intentType" class="text-blue-400">{{ intentLabel(msg.intentType) }}</span>
            </p>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="p-3 bg-white border-t border-gray-200 flex-shrink-0">
        <div class="flex gap-2 items-end">
          <el-input
            v-model="inputText"
            placeholder="输入您的问题...（Enter 发送，Shift+Enter 换行）"
            type="textarea"
            :rows="1"
            resize="none"
            maxlength="4000"
            @keydown.enter.exact.prevent="sendMsg()"
          />
          <el-button type="primary" :icon="Promotion" :disabled="!inputText.trim() || generating" @click="sendMsg()">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useStore } from 'vuex'
import { ChatDotRound, Plus, Promotion, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { cozeApi } from '@/api'

const store = useStore()
const projectId = computed(() => store.getters.projectId)

const sessions = ref([])
const currentSessionId = ref('')
const messages = ref([])
const inputText = ref('')
const generating = ref(false)
const loadingSessions = ref(false)
const msgContainer = ref(null)
const streamController = ref(null)

// 打字机伪流式：先把完整回答缓存到 fullText，定时器逐字符/小批字符推送到消息气泡
const fullText = ref('')
const typeTimer = ref(null)
let typedCount = 0

/** 打字机播放：按固定节奏把 fullText 中未展示的字符逐步写入 assistantMsg.content */
function playTypewriter(assistantMsg) {
  if (typeTimer.value) return
  const total = fullText.value.length
  assistantMsg.typing = true
  typeTimer.value = setInterval(() => {
    const step = Math.max(1, Math.min(3, Math.ceil(total / 300)))
    typedCount += step
    if (typedCount > total) typedCount = total
    assistantMsg.content = fullText.value.slice(0, typedCount)
    scrollToBottom()
    if (typedCount >= total) {
      assistantMsg.content = fullText.value
      stopTypeTimer()
    }
  }, 24)
}

function stopTypeTimer() {
  if (typeTimer.value) {
    clearInterval(typeTimer.value)
    typeTimer.value = null
  }
}

const suggestions = ['当前有哪些未处置告警？', '塔吊TC001的运行状态如何？', '今日塔吊告警原因分析', '环境监测数据是否正常？']

const currentSessionTitle = computed(() => {
  if (currentSessionId.value) {
    const found = sessions.value.find(s => s.sessionId === currentSessionId.value)
    if (found && found.title) return found.title
  }
  return 'Coze 智能体'
})

const INTENT_LABELS = { safety_query: '安全问答', alarm_analysis: '告警分析', device_diagnosis: '设备诊断', suggestion: '安全建议' }
function intentLabel(type) {
  return INTENT_LABELS[type] || ''
}

function msgKey() {
  return `m_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function now() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 兼容秒级 ISO(+08:00) 与 Date 字符串，格式化为本地时间 */
function formatTime(t) {
  if (!t) return '-'
  try {
    const iso = t.replace(' ', 'T')
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return String(t).slice(0, 16)
    const pad = n => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch (_) {
    return String(t).slice(0, 16)
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  })
}

/** 加载会话列表（不中断当前对话） */
async function loadSessions() {
  loadingSessions.value = true
  try {
    const data = await cozeApi.listSessions({ pageNo: 1, pageSize: 50 })
    sessions.value = (data?.data?.records) || []
  } catch (_) {
    /* 静默失败，保留已有列表 */
  } finally {
    loadingSessions.value = false
  }
}

/** 点击历史会话 → 加载其消息 */
async function selectSession(session) {
  if (session.sessionId === currentSessionId.value) return
  abortStream()
  currentSessionId.value = session.sessionId
  messages.value = []
  try {
    const data = await cozeApi.listMessages(session.sessionId, { pageNo: 1, pageSize: 100 })
    messages.value = (data?.data?.records || []).map(m => normalizeHistoryMsg(m))
    scrollToBottom()
  } catch (_) {
    messages.value = []
  }
}

function normalizeHistoryMsg(m) {
  const failed = m.status === 'failed'
  return {
    key: msgKey(),
    messageId: m.messageId,
    role: m.role,
    content: m.content || '',
    typing: false,
    status: m.status,
    intentType: m.intentType,
    errorMessage: m.errorMessage,
    failed,
    generating: m.role === 'assistant' && m.status === 'generating',
    time: formatTime(m.createdAt)
  }
}

/** 新对话：清空本地输入状态（会话在首条消息发送时自动创建） */
function startNewChat() {
  abortStream()
  currentSessionId.value = ''
  messages.value = []
  inputText.value = ''
}

async function sendMsg(text) {
  const msg = (text || inputText.value).trim()
  if (!msg || generating.value) return

  inputText.value = ''
  scrollToBottom()

  // 无会话时先创建
  let sessionId = currentSessionId.value
  if (!sessionId) {
    try {
      const created = await cozeApi.createSession({ title: msg.slice(0, 50), projectId: projectId.value || undefined })
      sessionId = created?.data?.sessionId
      if (!sessionId) throw new Error('创建会话失败')
      currentSessionId.value = sessionId
      sessions.value.unshift(created.data)
    } catch (err) {
      const failed = { key: msgKey(), role: 'assistant', content: '', failed: true, generating: false, errorMessage: err?.message || '创建会话失败', time: now() }
      messages.value.push({ key: msgKey(), role: 'user', content: msg, time: now() }, failed)
      return
    }
  }

  // 追加用户消息 + 占位 AI 消息（reactive 包装，保证 content 逐字增改能触发重渲染）
  messages.value.push({ key: msgKey(), role: 'user', content: msg, time: now() })
  const assistantMsg = reactive({ key: msgKey(), role: 'assistant', content: '', typing: false, generating: true, status: 'generating', failed: false, time: now() })
  messages.value.push(assistantMsg)
  generating.value = true
  scrollToBottom()

  try {
    const data = await cozeApi.sendMessage(sessionId, {
      content: msg,
      projectId: projectId.value || undefined
    })
    const result = data?.data
    if (!result?.streamUrl) throw new Error('未获取到响应流')

    await new Promise((resolve, reject) => {
      let settled = false
      const finish = (fn) => {
        if (settled) return
        settled = true
        stopTypeTimer()
        assistantMsg.typing = false
        loadSessions()
        fn()
        generating.value = false
        resolve()
      }

      // 置空状态，准备本轮的打字机播放
      stopTypeTimer()
      fullText.value = ''
      typedCount = 0
      assistantMsg.content = ''
      assistantMsg.typing = false

      streamController.value = cozeApi.streamMessage(result.streamUrl, {
        onDelta: (delta) => {
          // 后端伪流式：增量一次性到达，这里仅累积；真正打字机在 onDone 拿到完整回答后进行
          fullText.value += delta || ''
        },
        onDone: (info) => {
          assistantMsg.generating = false
          assistantMsg.status = 'completed'
          assistantMsg.intentType = info?.intentType
          // 回答已完整收到，开始打字机播放
          const complete = String(fullText.value || '')
          if (complete) {
            fullText.value = complete
            typedCount = 0
            assistantMsg.content = ''
            assistantMsg.typing = true
          }
          // 待打字机播完再收尾
          const waitToFinish = () => {
            if (typedCount >= fullText.value.length) {
              assistantMsg.typing = false
              finish(() => scrollToBottom())
              return
            }
            if (!typeTimer.value) playTypewriter(assistantMsg)
            setTimeout(waitToFinish, 50)
          }
          waitToFinish()
        },
        onError: (err) => {
          stopTypeTimer()
          assistantMsg.typing = false
          assistantMsg.generating = false
          assistantMsg.failed = true
          assistantMsg.errorMessage = err?.message || '服务暂时不可用，请稍后重试'
          finish(() => scrollToBottom())
        }
      })
    })
  } catch (err) {
    // 发送请求本身失败（409 生成中/网络异常等）
    assistantMsg.generating = false
    assistantMsg.typing = false
    assistantMsg.failed = true
    assistantMsg.errorMessage = err?.message || '发送失败，请稍后重试'
    generating.value = false
    scrollToBottom()
  }
}

function abortStream() {
  if (streamController.value) {
    try { streamController.value.abort() } catch (_) { /* ignore */ }
    streamController.value = null
  }
  stopTypeTimer()
  // 若当前有生成中的占位消息，标记为中断
  messages.value.forEach(m => {
    if (m.generating) {
      m.generating = false
    }
    m.typing = false
  })
  generating.value = false
}

onMounted(() => {
  loadSessions()
})

onBeforeUnmount(() => {
  abortStream()
})
</script>

<style scoped>
.agent-page { border-radius: 12px; overflow: hidden; }

.dot-flashing {
  display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #1890ff;
  animation: dotFlashing 1s infinite linear alternate;
  box-shadow: 12px 0 0 -4px #1890ff, 24px 0 0 -4px #1890ff;
  margin-left: 6px;
}
@keyframes dotFlashing { 0% { box-shadow: 12px 0 0 -4px #e0e0e0, 24px 0 0 -4px #e0e0e0; } 100% { box-shadow: 12px 0 0 2px #1890ff, 24px 0 0 2px #1890ff; } }

.typing-cursor {
  width: 2px; height: 1em; background: #1890ff;
  animation: blink 0.8s step-end infinite;
  margin-left: 2px; vertical-align: -2px;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

:deep(.el-textarea__inner) { min-height: 40px !important; }
</style>