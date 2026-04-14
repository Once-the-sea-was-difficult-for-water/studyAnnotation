<template>
  <div class="chat-view">
    <!-- Left sidebar: Conversation History -->
    <aside class="sidebar-left">
      <ConversationHistory
        :conversations="chatStore.conversations"
        :current-id="chatStore.currentConversationId"
        @switch="chatStore.switchConversation"
        @create="chatStore.createConversation()"
        @rename="handleRename"
        @delete="chatStore.deleteConversation"
      />
    </aside>

    <!-- Center: Messages + Input -->
    <main class="center-panel">
      <ChatMessageList
        :messages="chatStore.sortedMessages"
        :is-streaming="chatStore.isStreaming"
      />
      <SkillProgressPanel
        :steps="progressSteps"
        :current-step="currentProgressStep"
        @confirm="handleConfirm"
      />
      <ChatInputArea
        :agents="agents"
        :skills="skills"
        :disabled="chatStore.isStreaming"
        @send="handleSend"
      />
      <SkillParamDialog
        v-model="showParamDialog"
        :params="pendingSkillParams"
        :skill-name="pendingSkillName"
        @submit="handleParamSubmit"
      />
    </main>

    <!-- Right sidebar: Skill Cards -->
    <aside class="sidebar-right">
      <SkillCards :skills="skills" @skill-select="handleSkillSelect" />
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useChatStore } from '@/stores/chatStore'
import { useAuth } from '@/composables/useAuth'
import {
  ChatMessageList,
  ChatInputArea,
  SkillProgressPanel,
  SkillParamDialog,
  ConversationHistory,
  SkillCards,
} from '@/components'
import type { Agent, Skill, SkillParam } from '@/types'
import type { ProgressStep } from '@/components/SkillProgressPanel.vue'

const chatStore = useChatStore()
const auth = useAuth()

// Demo data — replace with real API calls
const agents = ref<Agent[]>([])
const skills = ref<Skill[]>([])

// Skill progress tracking
const progressSteps = ref<ProgressStep[]>([])
const currentProgressStep = ref(0)

// Param dialog state
const showParamDialog = ref(false)
const pendingSkillParams = ref<SkillParam[]>([])
const pendingSkillName = ref<string>()
const pendingSkillId = ref<string>()

// Initialize auth + WebSocket on mount
onMounted(async () => {
  try {
    // Ensure we have a token (login if needed)
    if (!auth.isAuthenticated.value) {
      const defaultUserId = `user-${Date.now()}`
      await auth.login(defaultUserId)
    }

    // Create a default conversation if none exists
    if (!chatStore.currentConversationId) {
      chatStore.createConversation()
    }

    // Connect WebSocket with JWT token
    const sessionId = chatStore.currentConversationId!
    await chatStore.initWebSocket(sessionId, auth.token.value)
  } catch (e) {
    console.error('Failed to initialize WebSocket connection:', e)
  }
})

function handleSend(payload: { content?: string; agentId?: string; skillId?: string }) {
  if (!chatStore.currentConversationId) {
    chatStore.createConversation()
  }
  chatStore.sendMessage(payload.content, payload.agentId, payload.skillId)
}

function handleRename(id: string, title: string) {
  const conv = chatStore.conversations.find((c) => c.id === id)
  if (conv) {
    conv.title = title
    conv.updatedAt = new Date().toISOString()
  }
}

function handleSkillSelect(skill: Skill) {
  const requiredParams = skill.inputParams.filter((p) => p.required)
  if (requiredParams.length > 0) {
    pendingSkillId.value = skill.id
    pendingSkillName.value = skill.name
    pendingSkillParams.value = skill.inputParams
    showParamDialog.value = true
  } else {
    if (!chatStore.currentConversationId) chatStore.createConversation()
    chatStore.sendMessage(undefined, undefined, skill.id)
  }
}

function handleParamSubmit(params: Record<string, unknown>) {
  if (!chatStore.currentConversationId) chatStore.createConversation()
  chatStore.sendMessage(undefined, undefined, pendingSkillId.value, params)
  pendingSkillId.value = undefined
}

function handleConfirm(stepId: string, approved: boolean) {
  // Forward confirmation to backend via WebSocket
  void stepId
  void approved
}
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar-left {
  width: 240px;
  flex-shrink: 0;
  overflow: hidden;
}

.center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.sidebar-right {
  width: 280px;
  flex-shrink: 0;
  overflow: hidden;
}

/* Responsive: hide sidebars on small screens */
@media (max-width: 1024px) {
  .sidebar-right {
    display: none;
  }
}

@media (max-width: 768px) {
  .sidebar-left {
    display: none;
  }
}
</style>
