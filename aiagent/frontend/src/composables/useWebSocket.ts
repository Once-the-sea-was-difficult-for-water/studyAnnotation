import { ref, onUnmounted } from 'vue'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import type { UnifiedChunk } from '@/types'

export interface UseWebSocketOptions {
  /** WebSocket endpoint URL, defaults to '/ws' */
  url?: string
  /** JWT token for authentication */
  token?: string
  /** Auto-reconnect delay in ms */
  reconnectDelay?: number
  /** Max reconnect attempts, 0 = infinite */
  maxReconnectAttempts?: number
}

export interface SendMessagePayload {
  conversationId: string
  content?: string
  agentId?: string
  skillId?: string
  params?: Record<string, unknown>
}

export function useWebSocket(options: UseWebSocketOptions = {}) {
  const {
    url = '/ws',
    token = '',
    reconnectDelay = 3000,
    maxReconnectAttempts = 10,
  } = options

  const connected = ref(false)
  const reconnectCount = ref(0)

  let client: Client | null = null
  let subscription: StompSubscription | null = null
  let chunkCallback: ((chunk: UnifiedChunk) => void) | null = null

  /** Register a callback for incoming chunks */
  function onChunk(cb: (chunk: UnifiedChunk) => void) {
    chunkCallback = cb
  }

  /** Connect to the STOMP broker over SockJS */
  function connect(sessionId: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (client?.active) {
        resolve()
        return
      }

      client = new Client({
        webSocketFactory: () => new SockJS(url) as unknown as WebSocket,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        onConnect: () => {
          connected.value = true
          reconnectCount.value = 0

          // Subscribe to the user's session topic
          subscription = client!.subscribe(
            `/topic/chat/${sessionId}`,
            (message: IMessage) => {
              try {
                const chunk: UnifiedChunk = JSON.parse(message.body)
                chunkCallback?.(chunk)
              } catch {
                // ignore malformed messages
              }
            },
          )

          resolve()
        },

        onStompError: (frame) => {
          console.error('STOMP error:', frame.headers['message'])
          reject(new Error(frame.headers['message'] || 'STOMP connection error'))
        },

        onWebSocketClose: () => {
          connected.value = false
          reconnectCount.value++
          if (maxReconnectAttempts > 0 && reconnectCount.value >= maxReconnectAttempts) {
            client?.deactivate()
          }
        },
      })

      client.activate()
    })
  }

  /** Send a chat message to the server */
  function send(payload: SendMessagePayload) {
    if (!client?.active) {
      throw new Error('WebSocket is not connected')
    }
    client.publish({
      destination: '/app/chat.send',
      body: JSON.stringify(payload),
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  }

  /** Disconnect from the STOMP broker */
  function disconnect() {
    subscription?.unsubscribe()
    subscription = null
    client?.deactivate()
    client = null
    connected.value = false
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    reconnectCount,
    connect,
    disconnect,
    send,
    onChunk,
  }
}
