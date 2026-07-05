/**
 * RAG API 封装
 *
 * 提供 SSE 流式问答、同步问答、记忆清空等功能。
 * 纯 JS 模块，不依赖 Vue。
 *
 * 使用示例：
 *   import { streamChat, syncChat, clearMemory, generateSessionId } from './api/rag.js'
 *
 *   const { abort } = streamChat('你好', sessionId, {
 *     onToken: t => console.log(t),
 *     onDone: () => console.log('done'),
 *     onError: e => console.error(e)
 *   })
 *   // 取消: abort()
 */

const API_BASE = ''  // 使用 Vite proxy，相对路径即可

/**
 * SSE 流式问答
 *
 * @param {string} question 用户问题
 * @param {string} sessionId 会话ID
 * @param {Object} callbacks 回调
 * @param {Function} callbacks.onStatus    收到 status 事件
 * @param {Function} callbacks.onContexts  收到 contexts 事件（已解析 JSON 数组）
 * @param {Function} callbacks.onToken     收到 message 事件（单个 token 字符串）
 * @param {Function} callbacks.onDone      生成完成
 * @param {Function} callbacks.onError     发生错误
 * @returns {{ abort: Function }} 可调用 abort() 取消请求
 */
export function streamChat(question, sessionId, callbacks, options = {}) {
  const controller = new AbortController()

  const go = async () => {
    try {
      const resp = await fetch(`${API_BASE}/api/agent/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question, sessionId, videoId: options.videoId }),
        signal: controller.signal
      })

      if (!resp.ok) {
        const text = await resp.text().catch(() => resp.statusText)
        callbacks.onError?.(`服务器错误 (${resp.status}): ${text}`)
        return
      }

      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = 'message'
      let currentData = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (!line.trim()) {
            // 空行表示一个 SSE 事件结束
            dispatchEvent(currentEvent, currentData, callbacks)
            currentEvent = 'message'
            currentData = ''
            continue
          }
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            currentData += line.slice(5)
          }
        }
      }

      // 处理流结束时可能残留的事件
      if (currentData && currentEvent !== 'message') {
        dispatchEvent(currentEvent, currentData, callbacks)
      }
    } catch (e) {
      if (e.name === 'AbortError') return
      callbacks.onError?.(`连接失败: ${e.message}`)
    }
  }

  go()

  return {
    abort: () => controller.abort()
  }
}

function dispatchEvent(event, data, callbacks) {
  switch (event) {
    case 'status':
    case 'agent_status':
      callbacks.onStatus?.(data)
      callbacks.onTrace?.({ type: 'status', text: data })
      break
    case 'tool_call':
      callbacks.onToolCall?.(parseJsonOrRaw(data))
      callbacks.onTrace?.({ type: 'tool_call', data: parseJsonOrRaw(data) })
      break
    case 'tool_result':
      callbacks.onToolResult?.(parseJsonOrRaw(data))
      callbacks.onTrace?.({ type: 'tool_result', data: parseJsonOrRaw(data) })
      break
    case 'final_answer':
      callbacks.onFinalAnswer?.(data)
      break
    case 'contexts':
      try {
        callbacks.onContexts?.(JSON.parse(data))
      } catch {
        callbacks.onContexts?.([])
      }
      break
    case 'message':
      if (data) callbacks.onToken?.(data)
      break
    case 'done':
      callbacks.onDone?.()
      break
    case 'error':
      callbacks.onError?.(data)
      break
    default:
      // 未识别的事件类型，按 message 处理（兼容）
      if (data) callbacks.onToken?.(data)
  }
}

function parseJsonOrRaw(data) {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

/**
 * 同步问答（非流式）
 *
 * @param {string} question
 * @param {string} sessionId
 * @returns {Promise<{ sessionId, question, answer, contexts }>}
 */
export async function syncChat(question, sessionId) {
  const resp = await fetch(`${API_BASE}/api/rag/chat/sync`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, sessionId })
  })
  if (!resp.ok) {
    throw new Error(`服务器错误 (${resp.status})`)
  }
  return resp.json()
}

/**
 * 清空服务端会话记忆
 *
 * @param {string} sessionId
 * @returns {Promise<{ sessionId, status }>}
 */
export async function clearMemory(sessionId) {
  const resp = await fetch(`${API_BASE}/api/rag/memory/${sessionId}`, {
    method: 'DELETE'
  })
  if (!resp.ok) {
    throw new Error(`清空记忆失败 (${resp.status})`)
  }
  return resp.json()
}

/**
 * 生成会话 ID
 * 优先使用 crypto.randomUUID()，降级为时间戳+随机数
 *
 * @returns {string}
 */
export function generateSessionId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  // 降级方案
  const ts = Date.now().toString(36)
  const rnd = Math.random().toString(36).slice(2, 10)
  return `${ts}-${rnd}`
}
