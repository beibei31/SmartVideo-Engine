<template>
  <div class="chat-input-wrapper">
    <div class="chat-input-row">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="chat-textarea"
        :placeholder="placeholder"
        :disabled="disabled"
        rows="1"
        @keydown="onKeydown"
        @input="autoResize"
      ></textarea>
      <button
        class="send-btn"
        :disabled="disabled || !inputText.trim()"
        @click="doSend"
        title="发送 (Enter)"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round"
             stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13" />
          <polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
      </button>
    </div>
    <div v-if="hint" class="input-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '输入你的问题...' },
  hint: { type: String, default: '' }
})

const emit = defineEmits(['send'])

const inputText = ref('')
const textareaRef = ref(null)

// 当 disabled 从 true 变 false 时自动聚焦
watch(() => props.disabled, (val) => {
  if (!val) {
    setTimeout(() => textareaRef.value?.focus(), 100)
  }
})

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const maxHeight = 120 // ~4 lines
  el.style.height = Math.min(el.scrollHeight, maxHeight) + 'px'
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSend()
  }
}

function doSend() {
  const text = inputText.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  inputText.value = ''
  // 重置 textarea 高度
  const el = textareaRef.value
  if (el) el.style.height = 'auto'
}
</script>

<style scoped>
.chat-input-wrapper {
  padding: 12px 16px;
  border-top: 1px solid var(--border-tech, #2a2d35);
  background: var(--bg-card, #121418);
}

.chat-input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.chat-textarea {
  flex: 1;
  background: #000;
  border: 1px solid var(--border-tech, #2a2d35);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--text-main, #e0e0e0);
  font-family: 'Space Grotesk', 'Noto Sans SC', monospace;
  font-size: 0.9rem;
  line-height: 1.5;
  resize: none;
  outline: none;
  min-height: 40px;
  max-height: 120px;
  transition: border-color 0.3s;
}
.chat-textarea:focus {
  border-color: var(--accent-lime, #c5f946);
  box-shadow: 0 0 8px rgba(197, 249, 70, 0.15);
}
.chat-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.chat-textarea::placeholder {
  color: var(--text-sub, #71757a);
}

.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  background: var(--accent-lime, #c5f946);
  color: var(--text-inverse, #0b0c10);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.send-btn:hover:not(:disabled) {
  box-shadow: 0 0 15px rgba(197, 249, 70, 0.4);
  transform: scale(1.05);
}
.send-btn:disabled {
  background: var(--border-tech, #2a2d35);
  color: var(--text-sub, #71757a);
  cursor: not-allowed;
  opacity: 0.6;
}

.input-hint {
  margin-top: 6px;
  font-size: 0.7rem;
  color: var(--text-sub, #71757a);
  font-family: monospace;
}
</style>
