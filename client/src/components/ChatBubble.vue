<template>
  <div
    class="chat-bubble"
    :class="[message.role, { streaming: message.isStreaming }]"
  >
    <!-- 消息头部（角色标签 + 时间） -->
    <div class="bubble-header">
      <span class="role-badge">
        {{ message.role === 'user' ? 'YOU' : 'AI' }}
      </span>
      <span class="bubble-time">{{ formatTime(message.timestamp) }}</span>
    </div>

    <!-- 消息正文 -->
    <div
      v-if="message.role === 'assistant' && message.isStreaming"
      class="bubble-content raw"
    ><span class="streaming-text">{{ message.content }}</span><span class="cursor-blink">|</span></div>

    <div
      v-else-if="message.role === 'assistant'"
      class="bubble-content markdown-body"
      v-html="renderedContent"
    ></div>

    <div v-else class="bubble-content user-text">
      {{ message.content }}
    </div>

    <div
      v-if="message.role === 'assistant' && message.trace && message.trace.length > 0"
      class="agent-trace"
    >
      <div class="trace-title">执行过程</div>
      <div
        v-for="(item, index) in message.trace"
        :key="`${item.type}-${index}`"
        class="trace-item"
        :class="item.type"
      >
        <span class="trace-dot"></span>
        <span class="trace-text">{{ formatTrace(item) }}</span>
      </div>
    </div>

    <!-- 检索上下文（仅 AI 消息） -->
    <ChatContexts
      v-if="message.role === 'assistant' && message.contexts && message.contexts.length > 0"
      :contexts="message.contexts"
    />

    <!-- 错误重试按钮 -->
    <div v-if="message.role === 'assistant' && !message.isStreaming && isErrorMessage(message.content)" class="retry-row">
      <button class="retry-btn" @click="$emit('retry')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round"
             stroke-linejoin="round">
          <polyline points="23 4 23 10 17 10" />
          <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
        </svg>
        重试
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import ChatContexts from './ChatContexts.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
    // { id, role: 'user'|'assistant', content: string, contexts: Array|null,
    //   timestamp: number, isStreaming: boolean }
  }
})

defineEmits(['retry'])

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  return marked.parse(props.message.content)
})

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

function isErrorMessage(content) {
  return content && content.startsWith('[错误]')
}

function formatTrace(item) {
  if (item.type === 'status') return item.text
  if (item.type === 'tool_call') {
    const action = item.data && typeof item.data === 'object' ? item.data.action : item.data
    return `正在${toolLabel(action)}`
  }
  if (item.type === 'tool_result') {
    const toolName = item.data && typeof item.data === 'object' ? item.data.toolName : item.data
    const success = item.data && typeof item.data === 'object' ? item.data.success : true
    return `${toolLabel(toolName)}${success ? '完成' : '失败'}`
  }
  return String(item.text || item.data || '')
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
</script>

<style scoped>
.chat-bubble {
  max-width: 88%;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;
}

/* ---- 用户消息（右对齐） ---- */
.chat-bubble.user {
  align-self: flex-end;
  margin-left: auto;
}
.chat-bubble.user .bubble-content {
  background: var(--accent-lime, #c5f946);
  color: var(--text-inverse, #0b0c10);
  border-radius: 14px 14px 4px 14px;
  padding: 10px 14px;
  font-weight: 500;
}
.chat-bubble.user .role-badge {
  color: var(--accent-lime, #c5f946);
}
.chat-bubble.user .bubble-header {
  justify-content: flex-end;
}

/* ---- AI 消息（左对齐） ---- */
.chat-bubble.assistant {
  align-self: flex-start;
  margin-right: auto;
}
.chat-bubble.assistant .bubble-content {
  background: var(--bg-deep, #0b0c10);
  border: 1px solid var(--border-tech, #2a2d35);
  border-left: 2px solid var(--accent-lime, #c5f946);
  border-radius: 4px 14px 14px 14px;
  padding: 12px 16px;
  color: var(--text-main, #e0e0e0);
}
.chat-bubble.assistant .role-badge {
  color: #4ecca3;
}

/* ---- 流式中 ---- */
.chat-bubble.assistant.streaming .bubble-content {
  border-left-color: #f0c040;
}

.streaming-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.cursor-blink {
  color: var(--accent-lime, #c5f946);
  animation: cursorBlink 0.8s step-end infinite;
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ---- 头部 ---- */
.bubble-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  padding: 0 4px;
}

.role-badge {
  font-family: monospace;
  font-size: 0.65rem;
  font-weight: 700;
  letter-spacing: 1px;
}

.bubble-time {
  font-family: monospace;
  font-size: 0.65rem;
  color: var(--text-sub, #71757a);
}

/* ---- 正文 Markdown ---- */
.markdown-body {
  line-height: 1.7;
  font-size: 0.9rem;
}
/* scoped 下 v-html 的内容需要用 :deep() */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  color: var(--accent-lime, #c5f946);
  margin: 0.8em 0 0.4em;
  font-family: 'Space Grotesk', sans-serif;
}
.markdown-body :deep(h1) { font-size: 1.2rem; border-bottom: 1px solid var(--border-tech, #2a2d35); padding-bottom: 6px; }
.markdown-body :deep(h2) { font-size: 1.05rem; }
.markdown-body :deep(h3) { font-size: 0.95rem; }
.markdown-body :deep(p) { margin: 0.4em 0; }
.markdown-body :deep(strong) { color: var(--accent-lime, #c5f946); font-weight: 700; }
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.4);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 0.8rem;
}
.markdown-body :deep(pre) {
  background: #000;
  border: 1px solid var(--border-tech, #2a2d35);
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  font-size: 0.8rem;
  line-height: 1.5;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.5em;
  margin: 0.4em 0;
}
.markdown-body :deep(li) {
  margin: 0.2em 0;
}
.markdown-body :deep(blockquote) {
  border-left: 2px solid var(--accent-lime, #c5f946);
  padding-left: 12px;
  color: var(--text-sub, #71757a);
  margin: 0.5em 0;
}

/* ---- 用户纯文本 ---- */
.user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.agent-trace {
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-tech, #2a2d35);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.22);
  font-family: monospace;
}

.trace-title {
  margin-bottom: 6px;
  color: var(--accent-lime, #c5f946);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.trace-item {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-sub, #71757a);
  font-size: 0.72rem;
  line-height: 1.5;
}

.trace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-lime, #c5f946);
  flex: 0 0 auto;
}

.trace-item.tool_call .trace-dot {
  background: #f0c040;
}

.trace-item.tool_result .trace-dot {
  background: #4ecca3;
}

/* ---- 重试 ---- */
.retry-row {
  margin-top: 6px;
}
.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: 1px solid #e94560;
  color: #e94560;
  font-size: 0.75rem;
  font-family: monospace;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}
.retry-btn:hover {
  background: rgba(233, 69, 96, 0.15);
}

/* ---- 动画 ---- */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
