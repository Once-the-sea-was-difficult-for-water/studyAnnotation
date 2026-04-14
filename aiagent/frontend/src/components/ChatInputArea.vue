<template>
  <div class="chat-input-area">
    <div class="input-row">
      <el-input
        ref="inputRef"
        v-model="inputText"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="输入消息… 使用 @ 选择 Agent，/ 选择 Skill"
        :disabled="disabled"
        @keydown.enter.exact.prevent="handleSend"
        @input="handleInputChange"
      />
      <el-button
        type="primary"
        :icon="Promotion"
        :disabled="disabled || !canSend"
        @click="handleSend"
      />
    </div>

    <!-- Agent selector popover -->
    <el-popover
      :visible="showAgentSelector"
      placement="top-start"
      :width="260"
      trigger="manual"
      @hide="showAgentSelector = false"
    >
      <template #reference>
        <span class="popover-anchor" />
      </template>
      <div class="selector-list">
        <div class="selector-title">选择 Agent</div>
        <div
          v-for="agent in agents"
          :key="agent.id"
          class="selector-item"
          @click="selectAgent(agent)"
        >
          <el-avatar :size="24" :src="agent.avatar" icon="User" />
          <div class="selector-item-info">
            <span class="selector-item-name">{{ agent.name }}</span>
            <span class="selector-item-type">{{ agent.type }}</span>
          </div>
        </div>
        <div v-if="agents.length === 0" class="selector-empty">暂无可用 Agent</div>
      </div>
    </el-popover>

    <!-- Skill selector popover -->
    <el-popover
      :visible="showSkillSelector"
      placement="top-start"
      :width="280"
      trigger="manual"
      @hide="showSkillSelector = false"
    >
      <template #reference>
        <span class="popover-anchor" />
      </template>
      <div class="selector-list">
        <div class="selector-title">选择 Skill</div>
        <div
          v-for="skill in skills"
          :key="skill.id"
          class="selector-item"
          @click="selectSkill(skill)"
        >
          <el-icon><Aim /></el-icon>
          <div class="selector-item-info">
            <span class="selector-item-name">{{ skill.name }}</span>
            <span class="selector-item-desc">{{ skill.description }}</span>
          </div>
        </div>
        <div v-if="skills.length === 0" class="selector-empty">暂无可用 Skill</div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Promotion, Aim } from '@element-plus/icons-vue'
import type { Agent, Skill } from '@/types'

const props = defineProps<{
  agents: Agent[]
  skills: Skill[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  send: [payload: { content?: string; agentId?: string; skillId?: string }]
}>()

const inputRef = ref()
const inputText = ref('')
const selectedAgentId = ref<string>()
const selectedSkillId = ref<string>()
const showAgentSelector = ref(false)
const showSkillSelector = ref(false)

const canSend = computed(() => {
  return inputText.value.trim().length > 0 || !!selectedSkillId.value
})

function handleInputChange() {
  const text = inputText.value
  // Detect @ trigger — show agent selector when last char is @
  if (text.endsWith('@')) {
    showAgentSelector.value = true
    showSkillSelector.value = false
    return
  }
  // Detect / trigger — show skill selector when input starts with /
  if (text === '/') {
    showSkillSelector.value = true
    showAgentSelector.value = false
    return
  }
  // Close selectors if user types more
  if (!text.includes('@')) showAgentSelector.value = false
  if (!text.startsWith('/')) showSkillSelector.value = false
}

function selectAgent(agent: Agent) {
  selectedAgentId.value = agent.id
  // Replace the trailing @ with @agentName
  inputText.value = inputText.value.replace(/@$/, `@${agent.name} `)
  showAgentSelector.value = false
  inputRef.value?.focus()
}

function selectSkill(skill: Skill) {
  selectedSkillId.value = skill.id
  inputText.value = `/${skill.name}`
  showSkillSelector.value = false
  // Auto-send skill trigger
  handleSend()
}

function handleSend() {
  if (!canSend.value) return

  const content = inputText.value.trim() || undefined
  emit('send', {
    content,
    agentId: selectedAgentId.value,
    skillId: selectedSkillId.value,
  })

  // Reset state
  inputText.value = ''
  selectedAgentId.value = undefined
  selectedSkillId.value = undefined
  showAgentSelector.value = false
  showSkillSelector.value = false
}
</script>

<style scoped>
.chat-input-area {
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  position: relative;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-row .el-input {
  flex: 1;
}

.popover-anchor {
  position: absolute;
  bottom: 100%;
  left: 16px;
  width: 1px;
  height: 1px;
}

.selector-list {
  max-height: 240px;
  overflow-y: auto;
}

.selector-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 0 0 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 4px;
}

.selector-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.selector-item:hover {
  background: var(--el-fill-color-light);
}

.selector-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.selector-item-name {
  font-size: 13px;
  font-weight: 500;
}

.selector-item-type,
.selector-item-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selector-empty {
  padding: 12px;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
