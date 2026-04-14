import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Conversation, Message, UnifiedChunk } from '@/types'
import { useWebSocket, type SendMessagePayload } from '@/composables/useWebSocket'

export const useChatStore = defineStore('chat', () => {
  // ── State ──────────────────────────────────────────────
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<string | null>(null)
  const messages = ref<Message[]>([])
  const isStreaming = ref(false)
  const streamingContent = ref('')

  // ── Getters ────────────────────────────────────────────
  const currentConversation = computed(() =>
    conversations.value.find((c) => c.id === currentConversationId.value) ?? null,
  )

  const sortedMessages = computed(() =>
    [...messages.value].sort(
      (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
    ),
  )

  // ── WebSocket ──────────────────────────────────────────
  const ws = useWebSocket()

  function handleChunk(chunk: UnifiedChunk) {
    switch (chunk.type) {
      case 'content':
        isStreaming.value = true
        streamingContent.value += chunk.content ?? ''
        // Update the last assistant message in-place
        updateStreamingContent(chunk.content ?? '')
        break
      case 'progress':
        // Progress events can be handled by UI components
        break
      case 'done':
        isStreaming.value = false
        streamingContent.value = ''
        break
      case 'error':
        isStreaming.value = false
        streamingContent.value = ''
        // Append error as system message
        if (chunk.error) {
          appendMessage({
            id: crypto.randomUUID(),
            conversationId: currentConversationId.value ?? '',
            role: 'SYSTEM',
            content: `Error: ${chunk.error}`,
            timestamp: new Date().toISOString(),
          })
        }
        break
    }
  }

  ws.onChunk(handleChunk)

  // ── Actions ────────────────────────────────────────────

  /** Initialize WebSocket connection for a session */
  async function initWebSocket(sessionId: string, token?: string) {
    const wsInstance = useWebSocket({ token })
    wsInstance.onChunk(handleChunk)
    await wsInstance.connect(sessionId)
    return wsInstance
  }

  /** Send a message (free text, @Agent, or /Skill) */
  function sendMessage(
    content?: string,
    agentId?: string,
    skillId?: string,
    params?: Record<string, unknown>,
  ) {
    if (!currentConversationId.value) return

    // Build the display content
    let displayContent = content ?? ''
    if (agentId && content) displayContent = `@${agentId} ${content}`
    if (skillId) displayContent = `/${skillId}`

    // Append user message locally
    const userMessage: Message = {
      id: crypto.randomUUID(),
      conversationId: currentConversationId.value,
      role: 'USER',
      content: displayContent,
      timestamp: new Date().toISOString(),
    }
    appendMessage(userMessage)

    // Append a placeholder assistant message for streaming
    const assistantMessage: Message = {
      id: crypto.randomUUID(),
      conversationId: currentConversationId.value,
      role: 'ASSISTANT',
      content: '',
      timestamp: new Date().toISOString(),
      agentId,
      skillId,
    }
    appendMessage(assistantMessage)
    isStreaming.value = true
    streamingContent.value = ''

    // Send via WebSocket
    const payload: SendMessagePayload = {
      conversationId: currentConversationId.value,
      content,
      agentId,
      skillId,
      params,
    }
    ws.send(payload)
  }

  /** Create a new conversation */
  function createConversation(title?: string): Conversation {
    const conversation: Conversation = {
      id: crypto.randomUUID(),
      userId: '',
      title: title ?? `新对话 ${conversations.value.length + 1}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      agentsUsed: [],
      skillsUsed: [],
    }
    conversations.value.push(conversation)
    switchConversation(conversation.id)
    return conversation
  }

  /** Switch to a different conversation */
  function switchConversation(conversationId: string) {
    currentConversationId.value = conversationId
    // Clear messages — in a real app, load from API
    messages.value = []
    isStreaming.value = false
    streamingContent.value = ''
  }

  /** Delete a conversation */
  function deleteConversation(conversationId: string) {
    conversations.value = conversations.value.filter((c) => c.id !== conversationId)
    if (currentConversationId.value === conversationId) {
      currentConversationId.value = conversations.value[0]?.id ?? null
      messages.value = []
    }
  }

  /** Append a message to the current list */
  function appendMessage(message: Message) {
    messages.value.push(message)
  }

  /** Update the last assistant message with streaming content */
  function updateStreamingContent(contentDelta: string) {
    const lastAssistant = [...messages.value]
      .reverse()
      .find((m) => m.role === 'ASSISTANT')
    if (lastAssistant) {
      lastAssistant.content += contentDelta
    }
  }

  return {
    // State
    conversations,
    currentConversationId,
    messages,
    isStreaming,
    streamingContent,
    // Getters
    currentConversation,
    sortedMessages,
    // Actions
    initWebSocket,
    sendMessage,
    createConversation,
    switchConversation,
    deleteConversation,
    appendMessage,
    updateStreamingContent,
  }
})
