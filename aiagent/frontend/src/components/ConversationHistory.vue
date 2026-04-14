<template>
  <div class="conversation-history">
    <div class="history-header">
      <span class="history-title">对话历史</span>
      <el-button type="primary" :icon="Plus" size="small" circle @click="handleNew" />
    </div>

    <div class="conversation-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ active: conv.id === currentId }"
        @click="$emit('switch', conv.id)"
        @contextmenu.prevent="openMenu($event, conv)"
      >
        <div class="conv-title-row">
          <template v-if="renamingId === conv.id">
            <el-input
              v-model="renameText"
              size="small"
              autofocus
              @keydown.enter.prevent="confirmRename(conv.id)"
              @blur="confirmRename(conv.id)"
              @click.stop
            />
          </template>
          <span v-else class="conv-title" :title="conv.title">{{ conv.title }}</span>
          <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, conv)" @click.stop>
            <el-icon class="conv-more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename"><el-icon><Edit /></el-icon>重命名</el-dropdown-item>
                <el-dropdown-item command="delete" divided><el-icon><Delete /></el-icon>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="conv-meta">
          <span class="conv-time">{{ formatTime(conv.updatedAt) }}</span>
          <span v-for="a in conv.agentsUsed?.slice(0, 2)" :key="a" class="conv-tag agent-tag">@{{ a }}</span>
          <span v-for="s in conv.skillsUsed?.slice(0, 2)" :key="s" class="conv-tag skill-tag">/{{ s }}</span>
        </div>
      </div>
      <div v-if="conversations.length === 0" class="empty-hint">暂无对话，点击 + 新建</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'
import type { Conversation } from '@/types'

defineProps<{
  conversations: Conversation[]
  currentId: string | null
}>()

const emit = defineEmits<{
  switch: [id: string]
  create: []
  rename: [id: string, title: string]
  delete: [id: string]
}>()

const renamingId = ref<string | null>(null)
const renameText = ref('')

function handleNew() {
  emit('create')
}

function handleCommand(cmd: string, conv: Conversation) {
  if (cmd === 'rename') {
    renamingId.value = conv.id
    renameText.value = conv.title
  } else if (cmd === 'delete') {
    emit('delete', conv.id)
  }
}

function openMenu(_e: MouseEvent, conv: Conversation) {
  // Context menu handled by dropdown; this prevents default browser menu
  void conv
}

function confirmRename(id: string) {
  if (renameText.value.trim()) {
    emit('rename', id, renameText.value.trim())
  }
  renamingId.value = null
}

function formatTime(ts: string): string {
  try {
    const d = new Date(ts)
    const now = new Date()
    if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}
</script>

<style scoped>
.conversation-history {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  font-weight: 600;
  font-size: 14px;
}

.history-title {
  color: var(--el-text-color-primary);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.conversation-item {
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.conversation-item:hover {
  background: var(--el-fill-color-light);
}

.conversation-item.active {
  background: var(--el-color-primary-light-9);
}

.conv-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.conv-more {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}

.conversation-item:hover .conv-more {
  opacity: 1;
}

.conv-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.conv-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.conv-tag {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 3px;
}

.agent-tag {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.skill-tag {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.empty-hint {
  text-align: center;
  padding: 24px 0;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
