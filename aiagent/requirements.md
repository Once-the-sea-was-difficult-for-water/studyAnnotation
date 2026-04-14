# Requirements Document: 多智能体编排平台 (Multi-Agent Orchestration Platform)

## Introduction

多智能体编排平台是一个支持对话式交互的智能运维平台，融合信通院 SRE Agent 四层参考模型、PAAL（Plan-Act-Assess-Learn）循环引擎和 Anthropic Harness 框架。平台以 Skill 为中心组织能力，以 Agent 为执行单元，以 PAAL 循环为驱动引擎，支持自由对话、技能触发、@Agent 指定路由和历史对话管理四种核心交互模式。

前端采用 Vue 3 + Element Plus 构建对话式 UI，后端基于 Spring Boot 3 + Spring WebFlux 实现响应式编排引擎，通过 WebSocket（STOMP/SockJS）实现流式通信。

## Glossary

- **Platform**: 多智能体编排平台，本系统的整体
- **Intent_Recognizer**: 意图识别器，解析用户输入并确定路由模式的组件
- **Skill_Registry**: 技能注册中心，管理 Skill 的注册、发现和匹配的组件
- **Skill_Executor**: 技能执行引擎，按 Workflow 定义执行 Skill 步骤的组件
- **Agent_Adapter**: 智能体适配层，通过 Java SPI 统一不同类型 Agent 调用协议的组件
- **PAAL_Engine**: PAAL 循环引擎，实现 Plan-Act-Assess-Learn 闭环的组件
- **Harness_Controller**: Harness 框架控制器，实现双 Agent 博弈和三代理协作的组件
- **Context_Manager**: 上下文管理器，构建和管理 Agent 上下文的组件
- **RAG_Service**: RAG 知识库服务，提供向量检索和知识写入能力的组件
- **Authorization_Gate**: 分级授权网关，根据操作风险等级进行授权检查的组件
- **Fallback_Executor**: 容错执行器，构建 Agent 降级链并处理失败场景的组件
- **Chat_UI**: 前端对话界面，基于 Vue 3 + Element Plus 构建的用户交互界面
- **WebSocket_Service**: WebSocket 通信服务，基于 STOMP/SockJS 实现流式通信的组件
- **Skill**: 技能，以 YAML 声明式配置定义的可执行能力单元
- **Agent**: 智能体，执行具体任务的可插拔执行单元（LLM/Rule/API/Hybrid 四种类型）
- **Conversation**: 对话，用户与系统之间的一次完整交互会话
- **Message**: 消息，对话中的单条交互记录
- **SharedContext**: 共享上下文，发送给 Agent 的上下文信息集合
- **RoutingDecision**: 路由决策，意图识别后确定的处理路径
- **PAAL_Cycle**: Plan-Act-Assess-Learn 循环，驱动任务执行的核心引擎模式
- **Harness_Framework**: Harness 框架，包含双 Agent 博弈和三代理协作两种模式
- **Risk_Level**: 风险等级，L1（只读）到 L4（高危）的操作分级

## Requirements

### Requirement 1: 意图识别与路由

**User Story:** 作为平台用户，我希望系统能自动识别我的输入意图并路由到正确的处理链路，以便我可以通过自由对话、@Agent 指定或 /Skill 触发三种方式与系统交互。

#### Acceptance Criteria

1. WHEN 用户输入以 `@` 开头时, THE Intent_Recognizer SHALL 将路由模式设为 DIRECT_AGENT 并提取 agentId 和查询内容
2. WHEN 用户输入以 `/` 开头时, THE Intent_Recognizer SHALL 将路由模式设为 SKILL 并提取 skillId 和参数
3. WHEN 用户输入为自由文本时, THE Intent_Recognizer SHALL 通过 LLM 分类和关键词匹配进行智能路由
4. WHILE 自由文本路由中 Skill 匹配置信度大于 0.8 时, THE Intent_Recognizer SHALL 将路由模式设为 SKILL
5. WHILE 自由文本路由中 Skill 匹配置信度不超过 0.8 时, THE Intent_Recognizer SHALL 将路由模式设为 AGENT 并路由到最佳匹配的 Agent
6. THE Intent_Recognizer SHALL 对任意合法输入返回包含有效 mode 字段的 RoutingDecision，mode 值为 DIRECT_AGENT、SKILL 或 AGENT 之一
7. THE Intent_Recognizer SHALL 在识别过程中不修改传入的 ConversationContext 状态

### Requirement 2: 技能注册与发现

**User Story:** 作为平台管理员，我希望能够注册、管理和发现技能，以便系统能够根据用户需求匹配合适的 Skill。

#### Acceptance Criteria

1. WHEN 新 Skill 通过 YAML 配置注册时, THE Skill_Registry SHALL 验证配置完整性并将 Skill 加入注册中心
2. WHEN 用户查询匹配 Skill 时, THE Skill_Registry SHALL 基于向量索引进行语义匹配并返回按相关度排序的 Top-K 结果
3. WHEN 前端请求 Skill 列表时, THE Skill_Registry SHALL 按分类（DIAGNOSE、OPTIMIZE、MONITOR、SECURITY）返回 Skill 列表
4. WHEN Nacos 配置变更时, THE Skill_Registry SHALL 实现运行时热加载新 Skill 而无需重启服务
5. THE Skill_Registry SHALL 确保每个 Skill 的 id 唯一且格式为 kebab-case
6. THE Skill_Registry SHALL 确保每个 Skill 的 agentId 引用已注册的 Agent
7. THE Skill_Registry SHALL 确保每个 Skill 的 workflow.steps 至少包含一个步骤

### Requirement 3: 技能执行引擎

**User Story:** 作为平台用户，我希望系统能按 Skill Workflow 定义的步骤执行技能，以便我可以获得结构化的诊断和操作结果。

#### Acceptance Criteria

1. WHEN 执行 TOOL_CALL 类型步骤时, THE Skill_Executor SHALL 调用指定工具并将结果存入 outputKey 对应的上下文
2. WHEN 执行 LLM_CALL 类型步骤时, THE Skill_Executor SHALL 通过模板引擎渲染 Prompt 并调用 Agent 获取响应
3. WHEN 执行 CONDITION 类型步骤时, THE Skill_Executor SHALL 评估条件表达式并仅执行匹配的分支
4. WHEN 执行 PARALLEL 类型步骤时, THE Skill_Executor SHALL 使用 CompletableFuture 并行执行所有子步骤并等待全部完成
5. WHEN 执行 HUMAN_CONFIRM 类型步骤时, THE Skill_Executor SHALL 通过 SSE 推送确认请求并暂停等待用户响应
6. WHEN 每个步骤执行完成后, THE Skill_Executor SHALL 通过 SSE 推送该步骤的执行进度
7. IF Skill 的 required 参数缺失, THEN THE Skill_Executor SHALL 抛出 MissingParameterException 并阻止执行
8. THE Skill_Executor SHALL 确保遍历 steps 时 results map 包含所有已执行步骤的输出


### Requirement 4: 智能体适配层

**User Story:** 作为平台开发者，我希望系统通过 Java SPI 机制支持可插拔的 Agent 类型，以便我可以灵活扩展 LLM Agent、Rule Agent、API Agent 和 Hybrid Agent。

#### Acceptance Criteria

1. THE Agent_Adapter SHALL 通过 Java SPI 机制实现 Agent 可插拔，支持 LLM、RULE、API、HYBRID 四种类型
2. WHEN 调用 Agent 时, THE Agent_Adapter SHALL 提供同步调用（invoke）和流式调用（stream）两种方式
3. WHEN 需要取消正在执行的任务时, THE Agent_Adapter SHALL 通过 cancel 方法终止指定 taskId 的执行
4. THE Agent_Adapter SHALL 将不同 Agent 的原生请求/响应格式统一转换为 UnifiedRequest/UnifiedResponse
5. THE Agent_Adapter SHALL 确保每个 Agent 的 id 唯一
6. THE Agent_Adapter SHALL 确保 Agent 的 config.timeoutMs 大于 0
7. THE Agent_Adapter SHALL 确保 Agent 的 capabilities.confidence 值在 [0.0, 1.0] 范围内

### Requirement 5: PAAL 循环引擎

**User Story:** 作为平台用户，我希望复杂任务通过 Plan-Act-Assess-Learn 闭环执行，以便系统能够迭代优化直到达到预期效果。

#### Acceptance Criteria

1. WHEN PAAL 循环开始时, THE PAAL_Engine SHALL 在 Plan 阶段通过 RAG 检索历史案例并分解任务制定执行计划
2. WHEN Plan 阶段完成后, THE PAAL_Engine SHALL 在 Act 阶段调用工具、Agent 和 API 执行操作
3. WHEN Act 阶段完成后, THE PAAL_Engine SHALL 在 Assess 阶段独立评估执行效果
4. WHEN Assess 评估通过时, THE PAAL_Engine SHALL 返回成功结果并进入 Learn 阶段
5. WHEN Assess 评估未通过时, THE PAAL_Engine SHALL 将评估反馈注入下一轮 context 并重新进入 Plan 阶段
6. WHEN 达到 maxIterations 轮次仍未通过评估时, THE PAAL_Engine SHALL 返回失败结果 "超过最大迭代次数仍未通过评估"
7. THE PAAL_Engine SHALL 无论成功或失败都在 Learn 阶段将处置过程记录到知识库
8. THE PAAL_Engine SHALL 确保每轮迭代严格按 Plan → Act → Assess 顺序执行
9. THE PAAL_Engine SHALL 确保迭代次数不超过 maxIterations

### Requirement 6: Harness 框架控制器

**User Story:** 作为平台用户，我希望长程复杂任务通过双 Agent 博弈和三代理协作模式执行，以便解决自我评估偏差和上下文焦虑问题。

#### Acceptance Criteria

1. WHEN 执行双 Agent 博弈时, THE Harness_Controller SHALL 由 Generator 生成结果后交由独立的 Evaluator 评估
2. WHEN Evaluator 评估所有维度（正确性、完整性、质量）均达到 8 分及以上时, THE Harness_Controller SHALL 输出 PASS 并返回结果
3. WHEN Evaluator 评估未通过时, THE Harness_Controller SHALL 将评估反馈注入下一轮 context 继续迭代
4. WHEN 达到 maxRounds 仍未 PASS 时, THE Harness_Controller SHALL 返回 maxRoundsReached 结果
5. WHEN 执行三代理协作时, THE Harness_Controller SHALL 由 Planner 生成规格书，Generator 和 Evaluator 协商 Sprint 契约后逐 Sprint 迭代
6. WHILE 上下文 token 数超过 100K 阈值时, THE Harness_Controller SHALL 归档当前上下文并创建包含摘要的干净新上下文
7. THE Harness_Controller SHALL 确保 Evaluator 的评估仅基于 Generator 的输出和原始任务要求，不依赖 Generator 的内部状态

### Requirement 7: 上下文管理

**User Story:** 作为平台用户，我希望系统能智能管理对话上下文，以便 Agent 能获得充分的历史信息同时不超过 token 限制。

#### Acceptance Criteria

1. WHEN 构建 Agent 上下文时, THE Context_Manager SHALL 截取最近 N 轮对话确保总 token 数不超过 agent.config.maxContextTokens
2. WHEN 路由决策包含 skillId 时, THE Context_Manager SHALL 注入 Skill 专属 system prompt
3. WHEN 路由决策不包含 skillId 时, THE Context_Manager SHALL 使用 Agent 默认 system prompt
4. WHEN 构建上下文时, THE Context_Manager SHALL 从向量库检索 Top-5 相关历史作为长期记忆
5. WHEN 对话结束后, THE Context_Manager SHALL 生成对话摘要并存入向量库
6. WHEN 上下文重置后, THE Context_Manager SHALL 确保新 context 的 sessionId 和 systemPrompt 与原 context 一致

### Requirement 8: RAG 知识库服务

**User Story:** 作为平台用户，我希望系统能利用知识库增强 Agent 的回答质量，以便获得更准确和有依据的诊断结果。

#### Acceptance Criteria

1. WHEN 执行知识检索时, THE RAG_Service SHALL 通过向量检索粗筛 Top-N 候选片段
2. WHEN 对候选片段排序时, THE RAG_Service SHALL 应用时间衰减因子使近期知识权重高于远期知识
3. WHEN 精排候选片段时, THE RAG_Service SHALL 使用 ReRank 交叉编码器进行精排
4. THE RAG_Service SHALL 确保返回结果数量不超过 config.maxFragments
5. THE RAG_Service SHALL 确保返回结果按 score 降序排列
6. THE RAG_Service SHALL 确保返回结果中无重复片段
7. WHEN PAAL Learn 阶段完成时, THE RAG_Service SHALL 自动写入处置经验到知识库

### Requirement 9: 分级授权

**User Story:** 作为平台管理员，我希望系统根据操作风险等级进行分级授权，以便高危操作得到充分审查和控制。

#### Acceptance Criteria

1. WHEN Agent 执行 L1 只读操作时, THE Authorization_Gate SHALL 自动放行
2. WHEN Agent 执行 L2 低危操作时, THE Authorization_Gate SHALL 自动执行并记录审计日志和发送事后通知
3. WHEN Agent 执行 L3 中危操作时, THE Authorization_Gate SHALL 等待人工审批后返回 approved 或 denied
4. WHEN Agent 执行 L4 高危操作时, THE Authorization_Gate SHALL 先执行沙箱预演，通过后提交多人审批
5. IF L4 操作沙箱预演失败, THEN THE Authorization_Gate SHALL 直接拒绝执行并返回失败原因
6. THE Authorization_Gate SHALL 对所有级别的操作记录完整审计日志
7. THE Authorization_Gate SHALL 确保在 Agent 执行任何工具调用前完成授权检查，L3/L4 操作未经审批不可执行

### Requirement 10: 容错与降级

**User Story:** 作为平台用户，我希望系统在 Agent 失败时能自动降级到备选 Agent，以便我始终能获得响应。

#### Acceptance Criteria

1. WHEN 主 Agent 调用失败时, THE Fallback_Executor SHALL 按降级链 [primary, fallback1, fallback2, generic] 顺序尝试下一个 Agent
2. WHEN Agent 在 config.timeoutMs 内未返回响应时, THE Fallback_Executor SHALL 通过 CompletableFuture.orTimeout() 触发超时并切换到降级链下一个 Agent
3. WHEN Agent 返回结果未通过 qualityGate 检查时, THE Fallback_Executor SHALL 切换到降级链下一个 Agent
4. IF 降级链中所有 Agent 均失败, THEN THE Fallback_Executor SHALL 返回兜底回复 "抱歉，当前服务繁忙，请稍后重试或联系管理员。"
5. THE Fallback_Executor SHALL 确保降级链至少包含一个 generic Agent
6. WHEN Agent 调用失败时, THE Fallback_Executor SHALL 记录 warn 级别日志


### Requirement 11: 前端对话界面

**User Story:** 作为平台用户，我希望通过直观的对话界面与系统交互，以便我可以方便地发送消息、查看响应和管理对话历史。

#### Acceptance Criteria

1. THE Chat_UI SHALL 提供消息输入区域，支持自由文本输入、@Agent 指定和 /Skill 触发三种输入模式
2. WHEN 用户在输入框中输入 `@` 时, THE Chat_UI SHALL 显示可用 Agent 列表供用户选择
3. WHEN 用户在输入框中输入 `/` 时, THE Chat_UI SHALL 显示可用 Skill 列表供用户选择
4. WHEN 收到流式响应时, THE Chat_UI SHALL 实时逐字渲染 Agent 的回复内容
5. WHEN 收到 Skill 执行进度时, THE Chat_UI SHALL 展示步骤执行进度和中间结果
6. WHEN 用户发送消息后, THE Chat_UI SHALL 在消息列表中显示用户消息并展示加载状态直到收到响应
7. THE Chat_UI SHALL 提供对话历史面板，支持查看、切换和管理历史对话
8. WHEN 显示消息时, THE Chat_UI SHALL 支持 Markdown 渲染、代码高亮和图表展示等富文本格式
9. WHEN Skill 需要用户补充参数时, THE Chat_UI SHALL 弹出参数表单（el-dialog）供用户填写

### Requirement 12: WebSocket 流式通信

**User Story:** 作为平台用户，我希望系统通过 WebSocket 实现实时流式通信，以便我能即时看到 Agent 的响应过程。

#### Acceptance Criteria

1. THE WebSocket_Service SHALL 基于 STOMP/SockJS 协议建立客户端与服务端的实时通信连接
2. WHEN 服务端生成响应内容时, THE WebSocket_Service SHALL 以 chunk 形式流式推送到客户端
3. WHEN 推送 chunk 时, THE WebSocket_Service SHALL 区分 content（内容）、progress（进度）、done（完成）和 error（错误）四种类型
4. IF WebSocket 连接断开, THEN THE WebSocket_Service SHALL 通过 SockJS fallback 机制自动重连
5. WHEN 重连成功后, THE WebSocket_Service SHALL 恢复会话上下文并重新推送未送达的消息
6. THE WebSocket_Service SHALL 确保消息推送顺序与服务端生成顺序一致

### Requirement 13: 对话与消息持久化

**User Story:** 作为平台用户，我希望系统持久化我的对话和消息记录，以便我可以随时查看历史交互。

#### Acceptance Criteria

1. WHEN 用户发起新对话时, THE Platform SHALL 创建 Conversation 记录并自动生成标题
2. WHEN 对话中产生消息时, THE Platform SHALL 将 Message 记录持久化到数据库
3. THE Platform SHALL 确保每条 Message 包含 conversationId、role、content 和 timestamp 字段
4. THE Platform SHALL 确保 Conversation 中的 messages 按 timestamp 严格递增排序
5. THE Platform SHALL 为每条消息分配 traceId 用于全链路追踪关联
6. WHEN 消息包含富文本结果（图表、表格）时, THE Platform SHALL 将附件信息序列化存储在 attachmentsJson 字段

### Requirement 14: Skill 配置与验证

**User Story:** 作为平台管理员，我希望通过 YAML 声明式配置定义 Skill，以便我可以版本化管理技能并确保配置正确性。

#### Acceptance Criteria

1. THE Platform SHALL 支持通过 YAML 文件声明式定义 Skill 的 id、name、description、category、agentId、inputParams 和 workflow
2. WHEN 加载 Skill 配置时, THE Platform SHALL 验证 id 格式为 kebab-case 且全局唯一
3. WHEN 加载 Skill 配置时, THE Platform SHALL 验证 CONDITION 类型步骤包含 branches 定义
4. WHEN 加载 Skill 配置时, THE Platform SHALL 验证 PARALLEL 类型步骤包含 children 定义
5. WHEN 加载 Skill 配置时, THE Platform SHALL 验证 required 参数在执行前已提供
6. THE Platform SHALL 支持 Skill 配置通过 YAML + Git 管理实现版本化和可审计

### Requirement 15: 安全与认证

**User Story:** 作为平台管理员，我希望系统具备完善的安全机制，以便保护用户数据和防止未授权访问。

#### Acceptance Criteria

1. THE Platform SHALL 通过 Spring Security + JWT 实现统一认证鉴权
2. WHEN JWT Token 过期时, THE Platform SHALL 自动刷新 Token
3. THE Platform SHALL 对用户间对话数据实施严格隔离，向量库按 userId 过滤
4. THE Platform SHALL 对用户输入进行过滤以防止 Prompt Injection 攻击
5. WHEN L2 及以上操作执行时, THE Platform SHALL 自动发送钉钉通知
6. THE Platform SHALL 记录所有操作的完整审计日志支持回溯

### Requirement 16: 幻觉治理

**User Story:** 作为平台用户，我希望系统能检测和防范 Agent 输出中的幻觉内容，以便我获得可靠的诊断结果。

#### Acceptance Criteria

1. WHEN Agent 生成回复时, THE Platform SHALL 在生成层通过 RAG 注入相关知识减少幻觉
2. WHEN Agent 输出完成后, THE Platform SHALL 在验证层通过 Evaluator 进行交叉验证
3. IF Agent 输出与知识库已知事实矛盾, THEN THE Platform SHALL 标记为 flagged 状态并附带矛盾证据
4. WHEN 检测到幻觉内容时, THE Platform SHALL 触发双 Agent 博弈重新生成或标记为需人工复核

### Requirement 17: 性能与可观测性

**User Story:** 作为平台运维人员，我希望系统具备良好的性能和可观测性，以便我可以监控系统运行状态并快速定位问题。

#### Acceptance Criteria

1. WHEN 流式响应开始时, THE Platform SHALL 确保首字延迟小于 500ms
2. THE Platform SHALL 通过 Redis 缓存 Skill 配置和 Agent 注册信息以减少数据库查询
3. THE Platform SHALL 在 API Gateway 层实施限流以防止单用户过度消耗 LLM 资源
4. THE Platform SHALL 通过 SkyWalking 或 OpenTelemetry 实现全链路追踪
5. THE RAG_Service SHALL 确保向量检索延迟小于 100ms
6. THE Platform SHALL 为每个请求生成 RequestTrace 记录，包含 Agent 调用链路、工具调用链路和总延迟

