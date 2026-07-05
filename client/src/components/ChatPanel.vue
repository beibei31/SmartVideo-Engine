<template>
  <!-- 背景遮罩 -->
  <div
    v-if="isPanelOpen"
    class="chat-backdrop"
    @click="closePanel"
  ></div>

  <!-- 聊天面板 -->
  <div class="chat-panel" :class="{ open: isPanelOpen }">
    <!-- Header -->
    <header class="chat-header">
      <div class="header-left">
        <span class="header-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" stroke-linecap="round"
               stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </span>
        <span class="header-title">Agent Chat</span>
        <span v-if="videoTitle" class="scope-pill" :title="videoTitle">
          {{ videoTitle }}
        </span>
        <span v-if="statusText" class="header-status">{{ statusText }}</span>
      </div>
      <div class="header-actions">
        <button class="action-btn" title="新对话" @click="clearChat"
                :disabled="messages.length === 0">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" stroke-linecap="round"
               stroke-linejoin="round">
            <path d="M12 3v18M3 12h18" />
          </svg>
        </button>
        <button class="action-btn" title="清空记忆" @click="clearMemory"
                :disabled="messages.length === 0">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" stroke-linecap="round"
               stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            <path d="M10 11v6" />
            <path d="M14 11v6" />
          </svg>
        </button>
        <button class="action-btn close-btn" title="关闭" @click="closePanel">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" stroke-linecap="round"
               stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
    </header>

    <!-- 错误提示条 -->
    <div v-if="error" class="error-bar">
      <span class="error-text">{{ error }}</span>
      <button class="error-dismiss" @click="error = null">×</button>
    </div>

    <!-- 消息列表 -->
    <ChatMessages
      :messages="messages"
      :isStreaming="isStreaming"
      @send="handleSend"
      @retry="retryLast"
    />

    <!-- 底部输入框 -->
    <ChatInput
      :disabled="isStreaming"
      :hint="isStreaming ? 'AI 正在回复中...' : ''"
      placeholder="输入你的问题，Enter 发送"
      @send="handleSend"
    />
  </div>
</template>

<script setup>
import { useRagChat } from '../composables/useRagChat.js'
import ChatMessages from './ChatMessages.vue'
import ChatInput from './ChatInput.vue'

const props = defineProps({
  videoId: { type: [Number, String], default: null },
  videoTitle: { type: String, default: '' }
})

const {
  messages,
  isStreaming,
  isPanelOpen,
  statusText,
  error,
  sendMessage,
  clearChat,
  clearMemory,
  closePanel,
  retryLast
} = useRagChat()

function handleSend(text) {
  const numericVideoId = props.videoId != null && props.videoId !== ''
    ? Number(props.videoId)
    : null
  sendMessage(text, { videoId: Number.isFinite(numericVideoId) ? numericVideoId : null })
}
</script>

<style scoped>
/* ---- 遮罩 ---- */
.chat-backdrop {
  position: fixed;
  inset: 0;
  z-index: 960;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  animation: fadeIn 0.3s ease;
}

/* ---- 面板 ---- */
.chat-panel {
  position: fixed;
  top: 0;
  right: -520px;
  width: 480px;
  max-width: 100vw;
  height: 100%;
  z-index: 970;
  background: var(--bg-card, #121418);
  border-left: 2px solid var(--border-tech, #2a2d35);
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 40px rgba(0, 0, 0, 0.8);
  transition: right 0.4s cubic-bezier(0.19, 1, 0.22, 1);
}

.chat-panel.open {
  right: 0;
  border-left-color: var(--accent-lime, #c5f946);
}

/* ---- Header ---- */
.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-tech, #2a2d35);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(11, 12, 16, 0.9);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-icon {
  color: var(--accent-lime, #c5f946);
  display: flex;
  align-items: center;
}

.header-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-main, #e0e0e0);
  white-space: nowrap;
}

.header-status {
  font-family: monospace;
  font-size: 0.7rem;
  color: var(--text-sub, #71757a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.scope-pill {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid rgba(197, 249, 70, 0.35);
  border-radius: 999px;
  padding: 2px 8px;
  color: var(--accent-lime, #c5f946);
  background: rgba(197, 249, 70, 0.08);
  font-family: monospace;
  font-size: 0.68rem;
}

.header-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.action-btn {
  background: transparent;
  border: 1px solid var(--border-tech, #2a2d35);
  color: var(--text-sub, #71757a);
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.action-btn:hover:not(:disabled) {
  color: var(--accent-lime, #c5f946);
  border-color: var(--accent-lime, #c5f946);
}
.action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.close-btn:hover:not(:disabled) {
  color: #e94560;
  border-color: #e94560;
}

/* ---- 错误条 ---- */
.error-bar {
  margin: 0;
  padding: 8px 16px;
  background: rgba(233, 69, 96, 0.15);
  border-bottom: 1px solid rgba(233, 69, 96, 0.3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.error-text {
  font-size: 0.78rem;
  color: #e94560;
  font-family: monospace;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.error-dismiss {
  background: none;
  border: none;
  color: #e94560;
  font-size: 1.2rem;
  cursor: pointer;
  padding: 0 0 0 8px;
  line-height: 1;
}
.error-dismiss:hover {
  opacity: 0.7;
}

/* ---- 动画 ---- */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
