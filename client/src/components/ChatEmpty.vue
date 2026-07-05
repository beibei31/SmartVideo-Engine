<template>
  <!-- 空状态：未开始聊天时显示 -->
  <div class="chat-empty">
    <div class="empty-icon">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
           stroke-linejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
    </div>
    <p class="empty-title">向你的视频提问</p>
    <p class="empty-desc">
      基于已上传视频的 ASR 转写内容，AI 会检索相关知识并回答你的问题。
    </p>
    <div class="example-prompts">
      <button
        v-for="(p, i) in examples"
        :key="i"
        class="example-chip"
        @click="$emit('selectExample', p)"
      >{{ p }}</button>
    </div>
  </div>
</template>

<script setup>
defineEmits(['selectExample'])

const examples = [
  '这个视频的主要内容是什么？',
  '视频中提到了哪些关键概念？',
  '帮我总结一下视频的核心观点',
]
</script>

<style scoped>
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  color: var(--accent-lime, #c5f946);
  opacity: 0.4;
  margin-bottom: 20px;
}

.empty-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 1.2rem;
  color: var(--text-main, #e0e0e0);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 0.8rem;
  color: var(--text-sub, #71757a);
  max-width: 320px;
  line-height: 1.6;
  margin: 0 0 24px;
}

.example-prompts {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 320px;
}

.example-chip {
  background: var(--bg-deep, #0b0c10);
  border: 1px solid var(--border-tech, #2a2d35);
  color: var(--text-sub, #71757a);
  font-size: 0.8rem;
  font-family: monospace;
  padding: 10px 14px;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}
.example-chip:hover {
  color: var(--accent-lime, #c5f946);
  border-color: var(--accent-lime, #c5f946);
  background: rgba(197, 249, 70, 0.05);
}
</style>
