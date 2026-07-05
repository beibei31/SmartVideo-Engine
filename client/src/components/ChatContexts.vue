<template>
  <div class="chat-contexts">
    <button class="toggle-btn" @click="expanded = !expanded">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <polyline :points="expanded ? '6 15 12 9 18 15' : '6 9 12 15 18 9'" />
      </svg>
      参考来源 ({{ contexts.length }})
    </button>

    <div v-if="expanded" class="contexts-list">
      <div
        v-for="ctx in contexts"
        :key="ctx.index"
        class="context-item"
      >
        <div class="ctx-header">
          <span class="ctx-index">[{{ ctx.index }}]</span>
          <span class="ctx-source">{{ truncate(ctx.sourceTitle, 60) }}</span>
          <span class="ctx-score" :class="scoreClass(ctx.score)">
            {{ (ctx.score || 0).toFixed(3) }}
          </span>
        </div>
        <div class="ctx-bar-track">
          <div class="ctx-bar-fill" :class="scoreClass(ctx.score)"
               :style="{ width: Math.min((ctx.score || 0) * 100, 100) + '%' }"></div>
        </div>
        <div class="ctx-content" v-if="!isFullMap[ctx.index]">
          {{ truncate(ctx.content || '', 120) }}
          <button
            v-if="(ctx.content || '').length > 120"
            class="expand-btn"
            @click="isFullMap[ctx.index] = true"
          >展开全部</button>
        </div>
        <div class="ctx-content" v-else>
          {{ ctx.content }}
          <button
            class="expand-btn"
            @click="isFullMap[ctx.index] = false"
          >收起</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  contexts: {
    type: Array,
    default: () => []
  }
})

const expanded = ref(false)
const isFullMap = reactive({})

function truncate(str, maxLen) {
  if (!str) return ''
  return str.length > maxLen ? str.slice(0, maxLen) + '...' : str
}

function scoreClass(score) {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.6) return 'score-mid'
  return 'score-low'
}
</script>

<style scoped>
.chat-contexts {
  margin-top: 8px;
}

.toggle-btn {
  background: transparent;
  border: 1px solid var(--border-tech, #2a2d35);
  color: var(--text-sub, #71757a);
  font-size: 0.75rem;
  font-family: monospace;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}
.toggle-btn:hover {
  color: var(--accent-lime, #c5f946);
  border-color: var(--accent-lime, #c5f946);
}

.contexts-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 300px;
  overflow-y: auto;
}

.context-item {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-tech, #2a2d35);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 0.78rem;
}

.ctx-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ctx-index {
  color: var(--accent-lime, #c5f946);
  font-family: monospace;
  font-size: 0.7rem;
}

.ctx-source {
  flex: 1;
  color: var(--text-sub, #71757a);
  font-size: 0.7rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ctx-score {
  font-family: monospace;
  font-size: 0.7rem;
  font-weight: 700;
}
.ctx-score.score-high { color: #4ecca3; }
.ctx-score.score-mid { color: #f0c040; }
.ctx-score.score-low { color: #e94560; }

.ctx-bar-track {
  width: 100%;
  height: 3px;
  background: var(--border-tech, #2a2d35);
  border-radius: 2px;
  margin-bottom: 6px;
  overflow: hidden;
}
.ctx-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}
.ctx-bar-fill.score-high { background: #4ecca3; }
.ctx-bar-fill.score-mid { background: #f0c040; }
.ctx-bar-fill.score-low { background: #e94560; }

.ctx-content {
  color: var(--text-main, #e0e0e0);
  line-height: 1.5;
  word-break: break-word;
}

.expand-btn {
  background: none;
  border: none;
  color: var(--accent-lime, #c5f946);
  cursor: pointer;
  font-size: 0.7rem;
  font-family: monospace;
  padding: 0;
  margin-left: 4px;
  text-decoration: underline;
}
.expand-btn:hover {
  opacity: 0.8;
}
</style>
