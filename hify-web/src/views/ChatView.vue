<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Plus } from '@element-plus/icons-vue'

import {
  createChatSession,
  deleteChatSession,
  listChatMessages,
  listChatSessions,
  streamChatMessage,
  type ChatMessage,
  type ChatSession,
  type ChatStreamHandle,
} from '@/api/chat'
import { getAgentList, type AgentListItem } from '@/api/agent'
import { CONFIRM_CANCELLED, useConfirm } from '@/components/useConfirm'

/* ---------------- 类型 ---------------- */

interface DisplayMessage {
  /** 服务端消息用数字 id，本地临时消息用 tmp- 前缀 */
  key: string
  role: 'user' | 'assistant'
  content: string
  /** SSE error 事件渲染为红色气泡（失败消息后端不落库） */
  error?: boolean
}

/* ---------------- Agent 选项 / 新建会话弹窗 ---------------- */

const agentOptions = ref<AgentListItem[]>([])
const createDialogVisible = ref(false)
const createAgentId = ref<number | undefined>()
const creating = ref(false)

async function loadAgentOptions() {
  try {
    const page = await getAgentList({ page: 1, pageSize: 50, enabled: true })
    agentOptions.value = page.records
  } catch {
    // 拦截器已提示
  }
}

function openCreateDialog() {
  if (streaming.value) {
    ElMessage.warning('回复进行中，请等待结束或停止后再新建会话')
    return
  }
  createAgentId.value = undefined
  createDialogVisible.value = true
}

async function handleCreateSession() {
  if (!createAgentId.value) return
  creating.value = true
  try {
    const session = await createChatSession(createAgentId.value)
    sessions.value.unshift(session)
    createDialogVisible.value = false
    await selectSession(session)
  } catch {
    // 拦截器已提示，保持弹窗打开
  } finally {
    creating.value = false
  }
}

/* ---------------- 会话列表（游标分页） ---------------- */

const sessions = ref<ChatSession[]>([])
const sessionsLoading = ref(false)
const sessionsCursor = ref<number | null>(null)
const sessionsHasMore = ref(false)
const currentSession = ref<ChatSession | null>(null)

async function loadSessions(append = false) {
  sessionsLoading.value = true
  try {
    const page = await listChatSessions({
      cursorId: append ? sessionsCursor.value : null,
      size: 20,
    })
    sessions.value = append ? [...sessions.value, ...page.records] : page.records
    sessionsCursor.value = page.nextCursor
    sessionsHasMore.value = page.nextCursor !== null
  } catch {
    // 拦截器已提示
  } finally {
    sessionsLoading.value = false
  }
}

const removeSession = useConfirm(deleteChatSession, {
  title: '删除会话',
  text: '消息将一并删除，确定删除该会话吗？',
  confirmText: '删除',
  successText: '删除成功',
})

async function handleDeleteSession(session: ChatSession, event: Event) {
  event.stopPropagation()
  // useConfirm 内部已吞掉「取消」；接口失败时错误继续抛出，交由拦截器提示
  const result = await removeSession(session.id).catch(() => null)
  if (result === CONFIRM_CANCELLED || result === null) return
  if (currentSession.value?.id === session.id) {
    currentSession.value = null
    messages.value = []
  }
  sessions.value = sessions.value.filter((item) => item.id !== session.id)
}

/* ---------------- 消息区 ---------------- */

const messages = ref<DisplayMessage[]>([])
const messagesLoading = ref(false)
const messagesCursor = ref<number | null>(null)
const messagesHasMore = ref(false)
const messageArea = ref<HTMLElement>()
/** 用户是否贴在消息区底部；上翻历史时不强制滚动 */
let stickToBottom = true
let localSeq = 0

function toDisplay(message: ChatMessage): DisplayMessage {
  return {
    key: String(message.id),
    role: message.role,
    content: message.content,
  }
}

async function selectSession(session: ChatSession) {
  if (streaming.value) {
    ElMessage.warning('当前回复进行中，请等待结束或停止后再切换会话')
    return
  }
  currentSession.value = session
  messagesLoading.value = true
  stickToBottom = true
  try {
    const page = await listChatMessages(session.id, { size: 20 })
    // 接口 id 倒序（最新在前），展示反转为时间升序
    messages.value = [...page.records].reverse().map(toDisplay)
    messagesCursor.value = page.nextCursor
    messagesHasMore.value = page.nextCursor !== null
    await scrollToBottom()
  } catch {
    // 拦截器已提示
  } finally {
    messagesLoading.value = false
  }
}

async function loadOlderMessages() {
  if (!currentSession.value || messagesLoading.value || !messagesHasMore.value) return
  const area = messageArea.value
  const prevScrollHeight = area?.scrollHeight ?? 0
  messagesLoading.value = true
  try {
    const page = await listChatMessages(currentSession.value.id, {
      cursorId: messagesCursor.value,
      size: 20,
    })
    const older = [...page.records].reverse().map(toDisplay)
    messages.value = [...older, ...messages.value]
    messagesCursor.value = page.nextCursor
    messagesHasMore.value = page.nextCursor !== null
    // 前置旧消息后保持视觉位置不跳动
    await nextTick()
    if (area) {
      area.scrollTop = area.scrollHeight - prevScrollHeight
    }
  } catch {
    // 拦截器已提示
  } finally {
    messagesLoading.value = false
  }
}

function handleScroll() {
  const area = messageArea.value
  if (!area) return
  const distance = area.scrollHeight - area.scrollTop - area.clientHeight
  stickToBottom = distance < 40
  // 到顶加载更早的消息
  if (area.scrollTop === 0 && messagesHasMore.value && !messagesLoading.value) {
    void loadOlderMessages()
  }
}

async function scrollToBottom() {
  await nextTick()
  const area = messageArea.value
  if (area) {
    area.scrollTop = area.scrollHeight
  }
}

/* ---------------- 发送消息 / SSE ---------------- */

const input = ref('')
const streaming = ref(false)
/** 当前流式回复气泡的 key，用于「正在生成…」占位 */
const streamingAssistantKey = ref<string | null>(null)
let streamHandle: ChatStreamHandle | null = null

const canSend = computed(() =>
  currentSession.value !== null && input.value.trim().length > 0 && !streaming.value,
)

async function handleSend() {
  const session = currentSession.value
  const content = input.value.trim()
  if (!session || !content || streaming.value) return

  // 用户消息立即上屏（后端也会先落库），并插入待填充的 assistant 气泡
  messages.value.push({ key: `tmp-user-${localSeq}`, role: 'user', content })
  localSeq += 1
  const assistantKey = `tmp-assistant-${localSeq}`
  messages.value.push({ key: assistantKey, role: 'assistant', content: '' })
  localSeq += 1

  input.value = ''
  streaming.value = true
  streamingAssistantKey.value = assistantKey
  stickToBottom = true
  await scrollToBottom()

  const assistant = messages.value[messages.value.length - 1]
  const finishStreaming = () => {
    streaming.value = false
    streamingAssistantKey.value = null
    streamHandle = null
  }

  streamHandle = streamChatMessage(session.id, content, {
    onDelta: (delta) => {
      assistant.content += delta
      if (stickToBottom) {
        void scrollToBottom()
      }
    },
    onDone: async () => {
      finishStreaming()
      // 首条消息后服务端会改标题，刷新左侧列表同步标题
      await loadSessions(false)
      const refreshed = sessions.value.find((item) => item.id === session.id)
      if (refreshed) {
        currentSession.value = refreshed
      }
    },
    onError: (message) => {
      assistant.error = true
      assistant.content = message
      finishStreaming()
      void scrollToBottom()
    },
    onAbort: () => {
      // 主动停止：保留已生成片段，不标红
      finishStreaming()
    },
  })
}

function handleStop() {
  streamHandle?.abort()
}

function handleEnterSend(event: KeyboardEvent) {
  // Enter 发送，Shift+Enter 换行
  if (event.shiftKey) return
  event.preventDefault()
  void handleSend()
}

/* ---------------- 展示辅助 ---------------- */

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(5, 16) : ''
}

const sessionAgentName = computed(() => {
  const session = currentSession.value
  if (!session) return ''
  return (
    agentOptions.value.find((agent) => agent.id === session.agentId)?.name ??
    `Agent #${session.agentId}`
  )
})

onMounted(() => {
  void loadAgentOptions()
  void loadSessions(false)
})

onUnmounted(() => {
  streamHandle?.abort()
})
</script>

<template>
  <div class="chat-page">
    <!-- 左：会话列表 -->
    <aside class="chat-sidebar">
      <div class="chat-sidebar__header">
        <span class="chat-sidebar__title">会话</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openCreateDialog">
          新建会话
        </el-button>
      </div>

      <div v-loading="sessionsLoading" class="chat-sidebar__list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="chat-session"
          :class="{ 'is-active': currentSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <div class="chat-session__body">
            <span class="chat-session__title">{{ session.title }}</span>
            <span class="chat-session__time">{{ formatTime(session.updatedAt) }}</span>
          </div>
          <el-button
            link
            type="danger"
            class="chat-session__delete"
            @click="handleDeleteSession(session, $event)"
          >
            删除
          </el-button>
        </div>
        <el-button
          v-if="sessionsHasMore"
          link
          type="primary"
          class="chat-sidebar__more"
          :loading="sessionsLoading"
          @click="loadSessions(true)"
        >
          加载更多
        </el-button>
        <div v-if="!sessionsLoading && sessions.length === 0" class="chat-sidebar__empty">
          暂无会话，点击右上角新建
        </div>
      </div>
    </aside>

    <!-- 右：对话区 -->
    <section class="chat-main">
      <template v-if="currentSession">
        <header class="chat-main__header">
          <div class="chat-main__heading">
            <ChatDotRound class="chat-main__icon" />
            <span>{{ currentSession.title }}</span>
          </div>
          <el-tag size="small" effect="plain">{{ sessionAgentName }}</el-tag>
        </header>

        <div ref="messageArea" class="chat-messages" @scroll="handleScroll">
          <el-button
            v-if="messagesHasMore"
            link
            type="primary"
            class="chat-messages__more"
            :loading="messagesLoading"
            @click="loadOlderMessages"
          >
            加载更早消息
          </el-button>
          <div
            v-for="message in messages"
            :key="message.key"
            class="chat-row"
            :class="`is-${message.role}`"
          >
            <div class="chat-bubble" :class="{ 'is-error': message.error }">
              <template v-if="message.content">{{ message.content }}</template>
              <span
                v-else-if="streaming && message.key === streamingAssistantKey"
                class="chat-bubble__typing"
              >正在生成…</span>
              <span v-else>（空回复）</span>
            </div>
          </div>
          <div v-if="messages.length === 0 && !messagesLoading" class="chat-messages__empty">
            开始和 Agent 对话吧
          </div>
        </div>

        <footer class="chat-composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown.enter="handleEnterSend"
          />
          <div class="chat-composer__actions">
            <el-button v-if="streaming" type="danger" plain @click="handleStop">停止</el-button>
            <el-button type="primary" :disabled="!canSend" @click="handleSend">
              {{ streaming ? '回复中…' : '发送' }}
            </el-button>
          </div>
        </footer>
      </template>

      <div v-else class="chat-placeholder">
        <ChatDotRound class="chat-placeholder__icon" />
        <p>选择左侧会话，或新建一个会话开始对话</p>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建会话</el-button>
      </div>
    </section>

    <!-- 新建会话：选择 Agent -->
    <el-dialog v-model="createDialogVisible" title="新建会话" width="420px">
      <el-select
        v-model="createAgentId"
        placeholder="选择已启用的 Agent"
        class="chat-create__select"
        filterable
      >
        <el-option
          v-for="agent in agentOptions"
          :key="agent.id"
          :label="agent.modelName ? `${agent.name}（${agent.modelName}）` : agent.name"
          :value="agent.id"
        />
      </el-select>
      <div v-if="agentOptions.length === 0" class="chat-create__hint">
        暂无已启用的 Agent，请先到 Agent 管理页面创建
      </div>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="!createAgentId"
          @click="handleCreateSession"
        >
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 40px);
}

/* 左侧会话列表 */
.chat-sidebar {
  width: 264px;
  flex-shrink: 0;
  background: var(--hify-bg-surface);
  border: 1px solid var(--hify-border-subtle);
  border-radius: var(--hify-radius-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
}

.chat-sidebar__title {
  font-weight: 600;
  color: var(--hify-text-1);
}

.chat-sidebar__list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.chat-session {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px;
  border-radius: var(--hify-radius-sm);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.chat-session:hover {
  background: var(--hify-bg-muted);
}

.chat-session.is-active {
  background: var(--hify-color-primary-soft);
}

.chat-session__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-session__title {
  font-size: 13px;
  color: var(--hify-text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-session__time {
  font-size: 11px;
  color: var(--hify-text-4);
}

.chat-session__delete {
  opacity: 0;
  transition: opacity 0.15s ease;
}

.chat-session:hover .chat-session__delete {
  opacity: 1;
}

.chat-sidebar__more {
  width: 100%;
  margin: 6px 0;
}

.chat-sidebar__empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--hify-text-4);
}

/* 右侧对话区 */
.chat-main {
  flex: 1;
  min-width: 0;
  background: var(--hify-bg-surface);
  border: 1px solid var(--hify-border-subtle);
  border-radius: var(--hify-radius-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-main__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid var(--hify-border-subtle);
}

.chat-main__heading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--hify-text-1);
  /* 中文可逐字断行，flex 压缩时会被挤成竖排，需要 nowrap + 省略号 */
  min-width: 0;
}

.chat-main__heading span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-main__icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  color: var(--hify-color-primary-500);
}

.chat-main__header :deep(.el-tag) {
  flex-shrink: 0;
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-main__icon {
  color: var(--hify-color-primary-500);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-messages__more {
  align-self: center;
}

.chat-messages__empty {
  margin: auto;
  color: var(--hify-text-4);
  font-size: 13px;
}

.chat-row {
  display: flex;
}

.chat-row.is-user {
  justify-content: flex-end;
}

.chat-bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: var(--hify-radius-md);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--hify-bg-muted);
  color: var(--hify-text-1);
}

.is-user .chat-bubble {
  background: var(--hify-color-primary-500);
  color: var(--hify-text-inverse, #fff);
}

.chat-bubble.is-error {
  background: var(--hify-color-danger-soft);
  color: var(--hify-color-danger);
  border: 1px solid var(--hify-color-danger);
}

.chat-bubble__typing {
  color: var(--hify-text-4);
}

.chat-composer {
  border-top: 1px solid var(--hify-border-subtle);
  padding: 12px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-composer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.chat-placeholder {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--hify-text-3);
}

.chat-placeholder__icon {
  width: 40px;
  height: 40px;
  color: var(--hify-color-primary-500);
}

.chat-create__select {
  width: 100%;
}

.chat-create__hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--hify-text-4);
}
</style>
