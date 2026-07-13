/**
 * RAG 聊天状态管理 Composable
 *
 * 模块级单例 —— 所有组件 import 的是同一份响应式状态，
 * 无需 Pinia 或 provide/inject 即可实现跨组件共享。
 *
 * 使用示例：
 *   import { useRagChat } from '../composables/useRagChat.js'
 *   const { messages, isStreaming, sendMessage, togglePanel } = useRagChat()
 */

import { ref, readonly } from 'vue'
import {
  streamChat,
  clearMemory as clearServerMemory,
  generateSessionId
} from '../api/rag.js'

// ==================== 模块级单例状态 ====================

const messages = ref([])
const isStreaming = ref(false)
const isPanelOpen = ref(false)
const sessionId = ref(loadOrCreateSessionId())
const statusText = ref('')
const error = ref(null)
const unreadCount = ref(0)

/** 当前活跃的 SSE 连接 abort 句柄 */
let activeAbort = null

// ==================== 工具函数 ====================

function loadOrCreateSessionId() {
  try {
    const stored = localStorage.getItem('rag_session_id')
    if (stored) return stored
  } catch { /* localStorage 不可用 */ }
  const id = generateSessionId()
  try { localStorage.setItem('rag_session_id', id) } catch {}
  return id
}

function newMessageId() {
  return generateSessionId()
}

// ==================== 动作 ====================

/**
 * 发送用户消息，开始 RAG 问答
 *
 * @param {string} text 用户输入的问题
 */
function sendMessage(text, options = {}) {
  const trimmed = text?.trim()
  if (!trimmed) return
  if (isStreaming.value) return // 防止连点

  error.value = null
  statusText.value = '正在检索...'

  // 1. 添加用户消息
  const userMsg = {
    id: newMessageId(),
    role: 'user',
    content: trimmed,
    contexts: null,
    timestamp: Date.now(),
    isStreaming: false
  }
  messages.value.push(userMsg)

  // 2. 添加 AI 占位消息
  const aiMsg = {
    id: newMessageId(),
    role: 'assistant',
    content: '',
    contexts: null,
    trace: [],
    timestamp: Date.now(),
    isStreaming: true
  }
  messages.value.push(aiMsg)
  isStreaming.value = true

  // 3. 发起 SSE 流式请求
  activeAbort = streamChat(trimmed, sessionId.value, {
    onStatus(status) {
      statusText.value = status
    },
    onTrace(traceEvent) {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        last.trace.push({
          ...traceEvent,
          timestamp: Date.now()
        })
      }
    },
    onToolCall(call) {
      const action = typeof call === 'object' ? call.action : String(call)
      statusText.value = action ? `正在${toolLabel(action)}` : '正在调用工具'
    },
    onToolResult(result) {
      const toolName = typeof result === 'object' ? result.toolName : ''
      statusText.value = toolName ? `${toolLabel(toolName)}完成` : '工具执行完成'
      appendContexts(extractToolContexts(result))
    },
    onFinalAnswer(answer) {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        last.content = answer
      }
    },
    onContexts(ctxs) {
      // 更新最后一条 AI 消息的 contexts
      appendContexts(ctxs)
    },
    onToken(token) {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        last.content += token
      }
    },
    onDone() {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        last.isStreaming = false
      }
      isStreaming.value = false
      statusText.value = '生成完成'
      activeAbort = null

      // 如果面板关闭中，增加未读计数
      if (!isPanelOpen.value) {
        unreadCount.value++
      }
    },
    onError(errMsg) {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        last.isStreaming = false
        // 如果没有任何内容，显示错误
        if (!last.content) {
          last.content = `[错误] ${errMsg}`
        }
      }
      error.value = errMsg
      isStreaming.value = false
      statusText.value = '发生错误'
      activeAbort = null
    }
  }, options)
}

function toolLabel(name) {
  const labels = {
    VideoSearchTool: '检索视频片段',
    VideoSegmentLocatorTool: '定位视频时间点',
    VideoSummaryTool: '生成视频总结',
    QuizTool: '生成自测题',
    KnowledgeQaTool: '进行知识问答'
  }
  return labels[name] || name || '执行工具'
}

function appendContexts(ctxs) {
  const normalized = normalizeContexts(ctxs)
  if (normalized.length === 0) return
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'assistant') {
    last.contexts = normalized
  }
}

function extractToolContexts(result) {
  if (!result || typeof result !== 'object') return []
  const data = result.data
  if (!data || typeof data !== 'object') return []
  if (Array.isArray(data.contexts)) return data.contexts
  if (Array.isArray(data.segments)) return data.segments
  if (Array.isArray(data.matchedSegments)) return data.matchedSegments
  return []
}

function normalizeContexts(ctxs) {
  if (!Array.isArray(ctxs)) return []
  return ctxs.map((ctx, index) => {
    const metadata = ctx?.metadata && typeof ctx.metadata === 'object' ? ctx.metadata : {}
    const startTime = firstDefined(ctx?.startTime, metadata.startTime, metadata.start_time)
    const endTime = firstDefined(ctx?.endTime, metadata.endTime, metadata.end_time)
    return {
      index: firstDefined(ctx?.index, ctx?.chunkIndex, index + 1),
      chunkId: ctx?.chunkId || metadata.chunkId || '',
      content: ctx?.content || ctx?.text || ctx?.reason || '',
      score: Number(ctx?.score || 0),
      sourceTitle: ctx?.sourceTitle || metadata.sourceTitle || metadata.filename || '未知来源',
      retrievalType: ctx?.retrievalType || metadata.retrievalType || '',
      startTime: Number.isFinite(Number(startTime)) ? Number(startTime) : null,
      endTime: Number.isFinite(Number(endTime)) ? Number(endTime) : null
    }
  }).filter(ctx => ctx.content || ctx.sourceTitle)
}

function firstDefined(...values) {
  return values.find(value => value !== undefined && value !== null && value !== '')
}

/**
 * 清空本地聊天消息（不删服务端记忆）
 */
function clearChat() {
  cancelStreaming()
  messages.value = []
  error.value = null
  statusText.value = '准备就绪'
  unreadCount.value = 0
}

/**
 * 清空服务端记忆 + 本地消息 + 刷新 sessionId
 */
async function clearMemory() {
  cancelStreaming()
  try {
    await clearServerMemory(sessionId.value)
  } catch (e) {
    console.warn('清空服务端记忆失败:', e)
  }
  messages.value = []
  error.value = null
  statusText.value = '记忆已清空'
  unreadCount.value = 0

  // 刷新 sessionId
  const newId = generateSessionId()
  sessionId.value = newId
  try { localStorage.setItem('rag_session_id', newId) } catch {}
}

/**
 * 切换面板开关
 */
function togglePanel() {
  isPanelOpen.value = !isPanelOpen.value
  if (isPanelOpen.value) {
    unreadCount.value = 0
  }
}

/**
 * 打开面板
 */
function openPanel() {
  isPanelOpen.value = true
  unreadCount.value = 0
}

/**
 * 关闭面板
 */
function closePanel() {
  isPanelOpen.value = false
}

/**
 * 取消当前流式请求
 */
function cancelStreaming() {
  if (activeAbort) {
    activeAbort.abort()
    activeAbort = null
  }
  isStreaming.value = false
}

/**
 * 重试最后一条用户消息
 */
function retryLast() {
  // 找到最后一条用户消息
  const userMsgs = messages.value.filter(m => m.role === 'user')
  if (userMsgs.length === 0) return

  const lastUser = userMsgs[userMsgs.length - 1]

  // 移除最后一条 AI 消息（如果存在）
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && lastMsg.role === 'assistant') {
    messages.value.pop()
  }

  // 重新发送
  sendMessage(lastUser.content)
}

// ==================== 导出 ====================

export function useRagChat() {
  return {
    // 只读状态
    messages: readonly(messages),
    isStreaming: readonly(isStreaming),
    isPanelOpen: readonly(isPanelOpen),
    sessionId: readonly(sessionId),
    statusText: readonly(statusText),
    error: readonly(error),
    unreadCount: readonly(unreadCount),

    // 动作
    sendMessage,
    clearChat,
    clearMemory,
    togglePanel,
    openPanel,
    closePanel,
    cancelStreaming,
    retryLast
  }
}
