/** 消息角色 */
export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'

/** 消息 */
export interface Message {
  id: string
  conversationId: string
  role: MessageRole
  content: string
  timestamp: string
  agentId?: string
  skillId?: string
  traceId?: string
  attachments?: Record<string, unknown>
}

/** 对话 */
export interface Conversation {
  id: string
  userId: string
  title: string
  createdAt: string
  updatedAt: string
  agentsUsed: string[]
  skillsUsed: string[]
}

/** 流式 chunk 类型 */
export type ChunkType = 'content' | 'progress' | 'done' | 'error'

/** 统一流式 chunk */
export interface UnifiedChunk {
  type: ChunkType
  content?: string
  data?: Record<string, unknown>
  skillId?: string
  stepId?: string
  error?: string
}

/** 参数类型 */
export type ParamType = 'STRING' | 'NUMBER' | 'ENUM' | 'INSTANCE_SELECTOR'

/** Skill 参数 */
export interface SkillParam {
  name: string
  type: ParamType
  required: boolean
  defaultValue?: unknown
  description: string
  enumValues?: string[]
}

/** Skill 分类 */
export type SkillCategory = 'DIAGNOSE' | 'OPTIMIZE' | 'MONITOR' | 'SECURITY'

/** Skill */
export interface Skill {
  id: string
  name: string
  description: string
  icon: string
  category: SkillCategory
  agentId: string
  inputParams: SkillParam[]
}

/** Agent 类型 */
export type AgentType = 'LLM' | 'RULE' | 'API' | 'HYBRID'

/** Agent */
export interface Agent {
  id: string
  name: string
  avatar?: string
  type: AgentType
  capabilities: { domain: string; skills: string[]; confidence: number }[]
}
