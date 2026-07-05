<template>
  <div class="chat-messages" ref="containerRef">
    <!-- 空状态 -->
    <ChatEmpty v-if="messages.length === 0" @select-example="$emit('send', $event)" />

    <!-- 消息列表 -->
    <div v-else class="messages-list">
      <ChatBubble
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
        @retry="$emit('retry')"
      />
    </div>

    <!-- 生成中指示器（兜底：用户看不到占位消息时） -->
    <div v-if="isStreaming && messages.length === 0" class="generating-hint">
      <div class="typing-dots">
        <span></span><span></span><span></span>
      </div>
      <span class="generating-text">AI 正在思考...</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import ChatBubble from './ChatBubble.vue'
import ChatEmpty from './ChatEmpty.vue'

const props = defineProps({
  messages: { type: Array, required: true },
  isStreaming: { type: Boolean, default: false }
})

defineEmits(['send', 'retry'])

const containerRef = ref(null)

// 自动滚动到底部
watch(
  () => {
    const msgs = props.messages
    if (msgs.length === 0) return ''
    const last = msgs[msgs.length - 1]
    return last.content
  },
  async () => {
    await nextTick()
    if (containerRef.value) {
      containerRef.value.scrollTo({
        top: containerRef.value.scrollHeight,
        behavior: 'smooth'
      })
    }
  }
)

// 新消息出现时也滚动
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (containerRef.value) {
      containerRef.value.scrollTo({
        top: containerRef.value.scrollHeight,
        behavior: 'smooth'
      })
    }
  }
)
</script>

<style scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.messages-list {
  display: flex;
  flex-direction: column;
}

.generating-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  color: var(--text-sub, #71757a);
  font-size: 0.85rem;
  font-family: monospace;
}

/* 打字指示器（三个跳动的点） */
.typing-dots {
  display: flex;
  gap: 4px;
  align-items: center;
}
.typing-dots span {
  width: 6px;
  height: 6px;
  background: var(--accent-lime, #c5f946);
  border-radius: 50%;
  animation: dotBounce 1.2s infinite ease-in-out;
}
.typing-dots span:nth-child(1) { animation-delay: 0s; }
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-6px); opacity: 1; }
}

.generating-text {
  color: var(--text-sub, #71757a);
}
</style>
