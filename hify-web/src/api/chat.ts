import { del, get, post } from '@/utils/request'

/* ------------------------------------------------------------------ */
/* 类型定义（与后端 com.hify.modules.conversation.api 对齐）            */
/* ------------------------------------------------------------------ */

export interface ChatSession {
  id: number
  agentId: number
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  tokens: number | null
  finishReason: string | null
  latencyMs: number | null
  createdAt: string
}

/** 游标分页（与后端 CursorPageDto 对齐） */
export interface CursorPage<T> {
  records: T[]
  nextCursor: number | null
}

/* ------------------------------------------------------------------ */
/* REST：会话与历史消息（走 axios 实例，自动解包 Result）               */
/* ------------------------------------------------------------------ */

const SESSIONS_URL = '/v1/chat/sessions'

export function createChatSession(agentId: number) {
  return post<ChatSession>(SESSIONS_URL, { agentId })
}

export function listChatSessions(params?: { cursorId?: number | null; size?: number }) {
  return get<CursorPage<ChatSession>>(SESSIONS_URL, {
    params: {
      size: params?.size ?? 20,
      ...(params?.cursorId ? { cursorId: params.cursorId } : {}),
    },
  })
}

export function deleteChatSession(id: number) {
  return del<void>(`${SESSIONS_URL}/${id}`)
}

export function listChatMessages(
  sessionId: number,
  params?: { cursorId?: number | null; size?: number },
) {
  return get<CursorPage<ChatMessage>>(`${SESSIONS_URL}/${sessionId}/messages`, {
    params: {
      size: params?.size ?? 20,
      ...(params?.cursorId ? { cursorId: params.cursorId } : {}),
    },
  })
}

/* ------------------------------------------------------------------ */
/* SSE：发送消息（POST + fetch ReadableStream，不能走 axios：           */
/* 15s 超时与 Result 解包拦截器不适用于流式响应）                       */
/* ------------------------------------------------------------------ */

interface SseDeltaEvent {
  type: 'delta'
  content: string
}

interface SseDoneEvent {
  type: 'done'
  finishReason: string
  latencyMs: number
}

interface SseErrorEvent {
  type: 'error'
  content: string
}

export interface ChatStreamHandlers {
  onDelta: (content: string) => void
  onDone: (event: { finishReason: string; latencyMs: number }) => void
  onError: (content: string) => void
  /** 主动 abort 或网络中断时回调（用于恢复输入态，不弹错误） */
  onAbort?: () => void
}

export interface ChatStreamHandle {
  /** 中断请求，上游会随之取消 LLM 调用 */
  abort: () => void
}

/**
 * 发送消息并消费 SSE 流。返回的句柄用于主动中断；
 * Promise 在流结束（done/error/abort）后 resolve，异常只走 onError 不抛出。
 */
export function streamChatMessage(
  sessionId: number,
  content: string,
  handlers: ChatStreamHandlers,
): ChatStreamHandle {
  const controller = new AbortController()

  void (async () => {
    try {
      const response = await fetch(`/api${SESSIONS_URL}/${sessionId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
        signal: controller.signal,
      })

      if (!response.ok || !response.body) {
        // 建流失败：GlobalExceptionHandler 仍返回 Result JSON
        const text = await response.text().catch(() => '')
        const message = extractErrorMessage(text) ?? `请求失败（${response.status}）`
        handlers.onError(message)
        return
      }

      await readSseStream(response.body, handlers)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        handlers.onAbort?.()
        return
      }
      handlers.onError(error instanceof Error ? error.message : '网络异常，请稍后重试')
    }
  })()

  return {
    abort: () => controller.abort(),
  }
}

/** 按 SSE 帧（空行分隔）切分并解析 data: 行 */
async function readSseStream(
  body: ReadableStream<Uint8Array>,
  handlers: ChatStreamHandlers,
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let separator: number
    while ((separator = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      dispatchFrame(frame, handlers)
    }
  }
  // 尾部残余（后端正常结束会带换行，这里兜底）
  if (buffer.trim()) {
    dispatchFrame(buffer, handlers)
  }
}

function dispatchFrame(frame: string, handlers: ChatStreamHandlers): void {
  // Spring 的事件可能拆成多条 data: 行，按 SSE 规范拼接；忽略空帧/注释行
  const dataLines = frame
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice('data:'.length).replace(/^ /, ''))
  if (dataLines.length === 0) return
  const payload = dataLines.join('\n').trim()
  if (!payload) return

  let event: SseDeltaEvent | SseDoneEvent | SseErrorEvent
  try {
    event = JSON.parse(payload)
  } catch {
    return
  }

  if (event.type === 'delta') {
    handlers.onDelta(event.content ?? '')
  } else if (event.type === 'done') {
    handlers.onDone({
      finishReason: event.finishReason ?? 'stop',
      latencyMs: event.latencyMs ?? 0,
    })
  } else if (event.type === 'error') {
    handlers.onError(event.content ?? '对话失败')
  }
}

function extractErrorMessage(text: string): string | null {
  try {
    const parsed = JSON.parse(text) as { message?: string }
    return parsed.message ?? null
  } catch {
    return null
  }
}
