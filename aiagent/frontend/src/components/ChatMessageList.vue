<template>
  <div class="chat-message-list" ref="listRef">
    <div
      v-for="msg in messages"
      :key="msg.id"
      class="message-item"
      :class="`message-role-${msg.role.toLowerCase()}`"
    >
      <div class="message-avatar">
        <el-avatar v-if="msg.role === 'USER'" :size="32" icon="User" />
        <el-avatar v-else-if="msg.role === 'ASSISTANT'" :size="32" icon="ChatDotRound" />
        <el-avatar v-else-if="msg.role === 'SYSTEM'" :size="32" icon="InfoFilled" />
        <el-avatar v-else :size="32" icon="SetUp" />
      </div>
      <div class="message-body">
        <div class="message-meta">
          <span class="message-role-label">{{ roleLabel(msg.role) }}</span>
          <span v-if="msg.agentId" class="message-agent-tag">@{{ msg.agentId }}</span>
          <span v-if="msg.skillId" class="message-skill-tag">/{{ msg.skillId }}</span>
          <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
        </div>
        <div class="message-content" v-html="renderMarkdown(msg.content)" />
      </div>
    </div>

    <!-- Streaming loading indicator (shown when streaming but last message has no content yet) -->
    <div v-if="isStreaming && lastMessageEmpty" class="message-item message-role-assistant streaming-indicator">
      <div class="message-avatar">
        <el-avatar :size="32" icon="ChatDotRound" />
      </div>
      <div class="message-body">
        <div class="message-meta">
          <span class="message-role-label">助手</span>
        </div>
        <div class="message-loading">
          <span class="dot" /><span class="dot" /><span class="dot" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { Message, MessageRole } from '@/types'

const props = defineProps<{
  messages: Message[]
  isStreaming: boolean
}>()

const listRef = ref<HTMLDivElement>()

const lastMessageEmpty = computed(() => {
  const last = props.messages[props.messages.length - 1]
  return !last || last.content === ''
})

// Auto-scroll to bottom when messages change
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
)

// Also scroll when streaming content updates
watch(
  () => props.messages[props.messages.length - 1]?.content,
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
)

function roleLabel(role: MessageRole): string {
  const labels: Record<MessageRole, string> = {
    USER: '用户',
    ASSISTANT: '助手',
    SYSTEM: '系统',
    TOOL: '工具',
  }
  return labels[role] ?? role
}

function formatTime(ts: string): string {
  try {
    return new Date(ts).toLocaleTimeString()
  } catch {
    return ''
  }
}

/**
 * Simple markdown renderer — handles code blocks, inline code,
 * bold, italic, and basic HTML escaping. No external dependency needed.
 */
function renderMarkdown(text: string): string {
  if (!text) return ''

  // Escape HTML first
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // Fenced code blocks: ```lang\n...\n```
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_match, lang, code) => {
    const langLabel = lang ? `<span class="code-lang">${lang}</span>` : ''
    return `<pre class="code-block">${langLabel}<code>${code}</code></pre>`
  })

  // Inline code: `...`
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

  // Bold: **...**
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')

  // Italic: *...*
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')

  // Line breaks
  html = html.replace(/\n/g, '<br />')

  return html
}
</script>

<style scoped>
.chat-message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message-role-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-role-assistant,
.message-role-system,
.message-role-tool {
  align-self: flex-start;
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-role-label {
  font-weight: 600;
}

.message-agent-tag,
.message-skill-tag {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
}

.message-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.message-content {
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.6;
  word-break: break-word;
}

.message-role-user .message-content {
  background: var(--el-color-primary);
  color: #fff;
}

.message-role-assistant .message-content {
  background: var(--el-fill-color-light);
}

.message-role-system .message-content {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  font-style: italic;
}

.message-role-tool .message-content {
  background: var(--el-color-info-light-9);
  font-family: monospace;
  font-size: 13px;
}

.message-content :deep(.code-block) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
  position: relative;
}

.message-content :deep(.code-lang) {
  position: absolute;
  top: 4px;
  right: 8px;
  font-size: 11px;
  color: #888;
}

.message-content :deep(.inline-code) {
  background: var(--el-fill-color);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}

/* Loading dots animation */
.message-loading {
  display: flex;
  gap: 4px;
  padding: 10px 14px;
}

.message-loading .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  animation: dot-bounce 1.4s infinite ease-in-out both;
}

.message-loading .dot:nth-child(1) { animation-delay: -0.32s; }
.message-loading .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
