<template>
  <button
    class="chat-fab"
    :class="{ active: isPanelOpen }"
    @click="togglePanel"
    title="AI 聊天"
  >
    <!-- 聊天图标 -->
    <svg v-if="!isPanelOpen" width="22" height="22" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" stroke-width="2"
         stroke-linecap="round" stroke-linejoin="round">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      <line x1="9" y1="10" x2="15" y2="10" />
      <line x1="12" y1="7" x2="12" y2="13" />
    </svg>
    <!-- 关闭图标 -->
    <svg v-else width="22" height="22" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" stroke-width="2"
         stroke-linecap="round" stroke-linejoin="round">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>

    <!-- 未读徽标 -->
    <span v-if="unreadCount > 0 && !isPanelOpen" class="badge">
      {{ unreadCount > 99 ? '99+' : unreadCount }}
    </span>
  </button>
</template>

<script setup>
import { useRagChat } from '../composables/useRagChat.js'

const { isPanelOpen, unreadCount, togglePanel } = useRagChat()
</script>

<style scoped>
.chat-fab {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 950;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--bg-card, #121418);
  border: 2px solid var(--border-tech, #2a2d35);
  color: var(--accent-lime, #c5f946);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.19, 1, 0.22, 1);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
}

.chat-fab:hover {
  border-color: var(--accent-lime, #c5f946);
  box-shadow: 0 0 25px rgba(197, 249, 70, 0.25);
  transform: scale(1.05);
}

.chat-fab.active {
  background: var(--accent-lime, #c5f946);
  color: var(--text-inverse, #0b0c10);
  border-color: var(--accent-lime, #c5f946);
  box-shadow: 0 0 20px rgba(197, 249, 70, 0.3);
}

/* 未读徽标 */
.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  background: #e94560;
  color: #fff;
  font-size: 0.65rem;
  font-family: monospace;
  font-weight: 700;
  border-radius: 10px;
  padding: 0 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 10px rgba(233, 69, 96, 0.5);
  animation: badgePop 0.3s ease;
}

@keyframes badgePop {
  0% { transform: scale(0); }
  60% { transform: scale(1.2); }
  100% { transform: scale(1); }
}
</style>
