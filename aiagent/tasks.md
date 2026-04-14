# Implementation Plan: 多智能体编排平台 (Multi-Agent Orchestration Platform)

## Overview

本实现计划将多智能体编排平台的设计分解为增量式编码任务，涵盖后端（Spring Boot 3 + Spring WebFlux + Java SPI）和前端（Vue 3 + Element Plus）。每个任务构建在前一个任务之上，确保无孤立代码。后端使用 Java，前端使用 TypeScript/Vue。属性测试后端使用 jqwik，前端使用 fast-check。

## Tasks

- [x] 1. 项目基础结构与核心数据模型
  - [x] 1.1 初始化后端项目结构与依赖配置
    - 创建 Spring Boot 3 多模块项目结构（api、service、adapter、infrastructure）
    - 配置 pom.xml 引入 Spring WebFlux、Spring Security、MyBatis-Plus、WebSocket (STOMP/SockJS)、Redis、Spring AI 等核心依赖
    - 配置 application.yml 数据源（MySQL、Redis、Milvus/Qdrant）、WebSocket 端点、Nacos 配置中心
    - _Requirements: 12.1, 15.1, 17.2, 17.4_

  - [x] 1.2 定义核心枚举与数据模型
    - 创建 `RoutingMode` 枚举（DIRECT_AGENT, SKILL, AGENT）
    - 创建 `RoutingDecision` 类（mode, agentId, skillId, query, params）
    - 创建 `StepType` 枚举（TOOL_CALL, LLM_CALL, CONDITION, PARALLEL, HUMAN_CONFIRM）
    - 创建 `SkillCategory` 枚举（DIAGNOSE, OPTIMIZE, MONITOR, SECURITY）
    - 创建 `AgentType` 枚举（LLM, RULE, API, HYBRID）
    - 创建 `RiskLevel` 枚举（L1_READ_ONLY, L2_LOW_RISK, L3_MEDIUM_RISK, L4_HIGH_RISK）
    - 创建 `MessageRole` 枚举（USER, ASSISTANT, SYSTEM, TOOL）
    - _Requirements: 1.6, 4.1, 9.1_

  - [x] 1.3 创建 Skill 与 Agent 数据模型
    - 创建 `Skill`、`SkillParam`、`SkillWorkflow`、`WorkflowStep`、`Branch` 类
    - 创建 `AgentManifest`、`AgentCapability`、`AgentConfig` 类
    - 实现 Skill 验证逻辑（id kebab-case 唯一、agentId 引用有效、workflow.steps 非空、CONDITION 含 branches、PARALLEL 含 children）
    - _Requirements: 2.5, 2.6, 2.7, 14.2, 14.3, 14.4_

  - [x] 1.4 创建对话与消息数据模型及持久化层
    - 创建 `Conversation` 实体（id, userId, title, createdAt, updatedAt, agentsUsedJson, skillsUsedJson）
    - 创建 `Message` 实体（id, conversationId, role, content, timestamp, agentId, skillId, traceId, attachmentsJson）
    - 创建 MyBatis-Plus Mapper 接口和数据库建表 SQL
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_

  - [x] 1.5 创建统一请求/响应与追踪模型
    - 创建 `UnifiedRequest`、`UnifiedResponse`、`UnifiedChunk`、`SharedContext` 类
    - 创建 `RequestTrace`、`AgentCallTrace`、`ToolCallTrace` 类
    - _Requirements: 4.4, 17.6_


- [ ] 2. 意图识别与路由模块
  - [x] 2.1 实现 IntentRecognizer 接口与核心路由逻辑
    - 创建 `IntentRecognizer` 接口及 `DefaultIntentRecognizer` 实现
    - 实现 `@agent-name` 解析逻辑：提取 agentId 和查询内容，设置 mode 为 DIRECT_AGENT
    - 实现 `/skill-name` 解析逻辑：提取 skillId 和参数，设置 mode 为 SKILL
    - 实现自由文本路由：LLM 分类 + 关键词匹配，置信度 > 0.8 匹配 Skill，否则路由到 Agent
    - 确保 recognize() 不修改传入的 ConversationContext 状态
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [ ]* 2.2 编写 IntentRecognizer 属性测试 — 路由确定性
    - **Property 1: 路由确定性**
    - 使用 jqwik 验证：任意合法输入，recognize() 返回的 RoutingDecision.mode 必须是 DIRECT_AGENT、SKILL、AGENT 之一
    - **Validates: Requirements 1.6**

  - [ ]* 2.3 编写 IntentRecognizer 单元测试
    - 测试 `@agent-name` 输入正确解析为 DIRECT_AGENT 模式
    - 测试 `/skill-name param=value` 输入正确解析为 SKILL 模式并提取参数
    - 测试自由文本输入的 LLM 分类路由逻辑
    - 测试空输入和非法输入的异常处理
    - _Requirements: 1.1, 1.2, 1.3, 1.7_

- [ ] 3. 技能注册与发现模块
  - [x] 3.1 实现 SkillRegistry 接口与注册逻辑
    - 创建 `SkillRegistry` 接口及 `DefaultSkillRegistry` 实现
    - 实现 Skill 注册方法：验证配置完整性后加入注册中心
    - 实现 YAML 配置加载与解析（SnakeYAML）
    - 实现按分类获取 Skill 列表（listByCategory）
    - 实现 getById 查询
    - _Requirements: 2.1, 2.3, 2.5, 2.6, 2.7, 14.1_

  - [x] 3.2 实现 Skill 语义匹配与热加载
    - 实现基于向量索引的语义匹配（Embedding + VectorStore），返回 Top-K 结果
    - 集成 Nacos 配置监听，实现运行时热加载新 Skill
    - 实现 Redis 缓存 Skill 配置，减少数据库查询
    - _Requirements: 2.2, 2.4, 14.6, 17.2_

  - [ ]* 3.3 编写 Skill 参数完整性属性测试
    - **Property 2: Skill 参数完整性**
    - 使用 jqwik 验证：若 skill.inputParams 中存在 required=true 的参数 p，则 params 必须包含 key p.name，否则抛出 MissingParameterException
    - **Validates: Requirements 3.7, 14.5**

  - [ ]* 3.4 编写 SkillRegistry 单元测试
    - 测试 YAML 配置加载与验证逻辑
    - 测试 id 唯一性和 kebab-case 格式校验
    - 测试 agentId 引用有效性校验
    - 测试 CONDITION/PARALLEL 步骤配置验证
    - _Requirements: 2.5, 2.6, 2.7, 14.2, 14.3, 14.4_

- [ ] 4. Checkpoint — 确保所有测试通过
  - 确保所有测试通过，ask the user if questions arise.


- [ ] 5. 智能体适配层 (Agent Adapter)
  - [x] 5.1 实现 AgentAdapter 抽象类与 Java SPI 机制
    - 创建 `AgentAdapter` 抽象类（invoke, stream, cancel, toNativeFormat, fromNativeFormat）
    - 配置 Java SPI（META-INF/services）实现 Agent 可插拔
    - 创建 `AgentRegistry` 管理已注册 Agent，支持按 id 查找和按 capability 匹配
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 5.2 实现四种 Agent 适配器
    - 实现 `LLMAgentAdapter`：集成 Spring AI 调用 OpenAI/Claude/Qwen，支持同步和流式
    - 实现 `RuleAgentAdapter`：基于规则引擎执行，不调用 LLM
    - 实现 `APIAgentAdapter`：直接调用外部 API 并格式化结果为 UnifiedResponse
    - 实现 `HybridAgentAdapter`：组合多种能力的混合型 Agent
    - 实现 Agent 配置验证（timeoutMs > 0, confidence ∈ [0.0, 1.0]）
    - _Requirements: 4.1, 4.2, 4.4, 4.6, 4.7_

  - [ ]* 5.3 编写 AgentAdapter 单元测试
    - 测试 LLMAgentAdapter 的同步和流式调用
    - 测试 cancel 方法终止任务
    - 测试 UnifiedRequest/UnifiedResponse 格式转换
    - 测试配置验证（timeoutMs, confidence 范围）
    - _Requirements: 4.2, 4.3, 4.6, 4.7_

- [ ] 6. 技能执行引擎 (Skill Executor)
  - [x] 6.1 实现 SkillExecutor 核心执行逻辑
    - 创建 `SkillExecutor` 接口及 `DefaultSkillExecutor` 实现
    - 实现 TOOL_CALL 步骤：调用 ToolRunner 执行工具并存入 outputKey
    - 实现 LLM_CALL 步骤：模板引擎渲染 Prompt + AgentAdapter 调用
    - 实现 CONDITION 步骤：评估条件表达式，执行匹配分支
    - 实现 PARALLEL 步骤：CompletableFuture.supplyAsync 并行执行子步骤
    - 实现 HUMAN_CONFIRM 步骤：SSE 推送确认请求，暂停等待用户响应
    - 实现必填参数校验，缺失时抛出 MissingParameterException
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8_

  - [x] 6.2 实现 SSE 进度推送管理器
    - 创建 `SSEManager` 管理 SSE 连接和事件推送
    - 每个步骤执行完成后推送进度事件（stepId, result, status）
    - 支持 HUMAN_CONFIRM 步骤的确认请求推送和响应接收
    - _Requirements: 3.5, 3.6_

  - [ ]* 6.3 编写 SkillExecutor 单元测试
    - 测试五种步骤类型的执行逻辑
    - 测试 CONDITION 分支选择正确性
    - 测试 PARALLEL 步骤并行执行和结果收集
    - 测试必填参数缺失时的异常抛出
    - 测试 results map 在遍历过程中包含所有已执行步骤输出
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7, 3.8_

- [ ] 7. 上下文管理器 (Context Manager)
  - [x] 7.1 实现 ContextManager 接口与上下文构建逻辑
    - 创建 `ContextManager` 接口及 `DefaultContextManager` 实现
    - 实现 buildContext：截取最近 N 轮对话，确保 token 数不超过 maxContextTokens
    - 实现 Skill 模式注入 Skill 专属 system prompt，否则使用 Agent 默认 prompt
    - 实现从向量库检索 Top-5 相关历史作为 longTermMemory
    - 实现 persist：对话结束后生成摘要并存入向量库
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 7.2 实现上下文重置算法
    - 实现 token 估算方法（estimateTokenCount）
    - 当 token 超过 100K 阈值时，归档当前上下文并创建包含摘要的干净新上下文
    - 确保重置后 sessionId 和 systemPrompt 与原 context 一致
    - _Requirements: 6.6, 7.1, 7.6_

  - [ ]* 7.3 编写上下文 Token 安全属性测试
    - **Property 5: 上下文 Token 安全**
    - 使用 jqwik 验证：buildContext 返回的 messages 总 token 数 ≤ agent.config.maxContextTokens
    - **Validates: Requirements 7.1**

  - [ ]* 7.4 编写上下文重置一致性属性测试
    - **Property 8: 上下文重置一致性**
    - 使用 jqwik 验证：重置后的新 context 包含归档摘要，且 sessionId 和 systemPrompt 与原 context 一致
    - **Validates: Requirements 7.6**


- [ ] 8. Checkpoint — 确保核心引擎模块测试通过
  - 确保所有测试通过，ask the user if questions arise.

- [ ] 9. PAAL 循环引擎
  - [x] 9.1 实现 PAALEngine 接口与循环逻辑
    - 创建 `PAALEngine` 接口及 `DefaultPAALEngine` 实现
    - 实现 Plan 阶段：通过 RAG 检索历史案例，分解任务制定执行 DAG
    - 实现 Act 阶段：调用 SkillExecutor/AgentAdapter 执行操作
    - 实现 Assess 阶段：独立评估执行效果，判断是否达标
    - 实现 Learn 阶段：无论成功失败都将处置过程记录到知识库
    - 实现循环控制：Assess 未通过时将反馈注入下一轮 context，达到 maxIterations 返回失败
    - 确保每轮严格按 Plan → Act → Assess 顺序执行
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

  - [ ]* 9.2 编写 PAAL 终止性属性测试
    - **Property 3: PAAL 终止性**
    - 使用 jqwik 验证：任意 maxIterations，paalLoop 最多执行 maxIterations 轮后终止，且 Learn 阶段一定执行
    - **Validates: Requirements 5.6, 5.7, 5.9**

  - [ ]* 9.3 编写 PAALEngine 单元测试
    - 测试 Assess 通过时正确返回成功结果
    - 测试 Assess 未通过时反馈注入下一轮
    - 测试达到 maxIterations 时返回失败结果
    - 测试 Learn 阶段在成功和失败场景下都执行
    - _Requirements: 5.4, 5.5, 5.6, 5.7_

- [ ] 10. Harness 框架控制器
  - [x] 10.1 实现双 Agent 博弈执行器
    - 创建 `DualAgentExecutor` 接口及实现
    - 实现 Generator 生成 → Evaluator 独立评估循环
    - Evaluator 从正确性、完整性、质量三个维度评分（1-10），所有维度 ≥ 8 分输出 PASS
    - 未通过时将评估反馈注入下一轮 context
    - 达到 maxRounds 返回 maxRoundsReached 结果
    - 确保 Evaluator 仅基于 Generator 输出和原始任务要求评估
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.7_

  - [x] 10.2 实现三代理协作执行器
    - 创建 `FullHarnessExecutor` 接口及实现
    - 实现 Planner 生成规格书 → Generator+Evaluator 协商 Sprint 契约 → 逐 Sprint 迭代
    - 集成上下文重置：token 超 100K 阈值时归档 + 干净重启 + 注入摘要
    - _Requirements: 6.5, 6.6_

  - [ ]* 10.3 编写双 Agent 独立性属性测试
    - **Property 7: 双 Agent 独立性**
    - 使用 jqwik 验证：Evaluator 的评估不依赖 Generator 的内部状态，仅基于 Generator 的输出和原始任务要求
    - **Validates: Requirements 6.7**

  - [ ]* 10.4 编写 Harness 单元测试
    - 测试双 Agent 博弈的 PASS/FAIL 迭代逻辑
    - 测试 maxRounds 达到后的返回结果
    - 测试三代理协作的 Sprint 迭代流程
    - 测试上下文重置触发条件和摘要注入
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [ ] 11. RAG 知识库服务
  - [x] 11.1 实现 RAGKnowledgeService 接口与检索逻辑
    - 创建 `RAGKnowledgeService` 接口及 `DefaultRAGKnowledgeService` 实现
    - 实现向量检索粗筛 Top-N 候选（集成 Milvus/Qdrant）
    - 实现时间衰减因子：近期知识权重高于远期知识
    - 实现 ReRank 交叉编码器精排
    - 实现 ingest 方法：PAAL Learn 阶段自动写入处置经验
    - 确保返回结果数量 ≤ maxFragments，按 score 降序排列，无重复片段
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [ ]* 11.2 编写 RAG 检索单元测试
    - 测试向量检索返回结果数量限制
    - 测试时间衰减因子对排序的影响
    - 测试结果去重逻辑
    - 测试 ingest 写入知识库
    - _Requirements: 8.4, 8.5, 8.6, 8.7_

- [ ] 12. 分级授权网关
  - [x] 12.1 实现 AuthorizationGate 接口与分级授权逻辑
    - 创建 `AuthorizationGate` 接口及 `DefaultAuthorizationGate` 实现
    - 创建 `OperationClassifier` 实现操作风险等级分类
    - 实现 L1 只读操作：自动放行
    - 实现 L2 低危操作：自动执行 + 审计日志 + 事后通知
    - 实现 L3 中危操作：创建审批工单，等待人工审批
    - 实现 L4 高危操作：沙箱预演 + 多人审批
    - 确保所有级别操作记录完整审计日志
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_

  - [ ]* 12.2 编写授权不可绕过属性测试
    - **Property 6: 授权不可绕过**
    - 使用 jqwik 验证：任意 ToolCallRequest，在 Agent 执行工具调用前必须经过 authorize() 检查，L3/L4 操作未经审批不可执行
    - **Validates: Requirements 9.7**

  - [ ]* 12.3 编写 AuthorizationGate 单元测试
    - 测试 L1-L4 四个等级的授权逻辑
    - 测试 L4 沙箱预演失败时直接拒绝
    - 测试审计日志记录
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [ ] 13. 容错与降级执行器
  - [x] 13.1 实现 FallbackExecutor 接口与降级链逻辑
    - 创建 `FallbackExecutor` 接口及 `DefaultFallbackExecutor` 实现
    - 实现降级链构建：[primary, fallback1, fallback2, generic]
    - 实现超时控制：CompletableFuture.orTimeout() 触发超时切换
    - 实现 qualityGate 检查：结果不达标时切换到下一个 Agent
    - 所有 Agent 失败时返回兜底回复 "抱歉，当前服务繁忙，请稍后重试或联系管理员。"
    - 每次失败记录 warn 级别日志
    - 确保降级链至少包含一个 generic Agent
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [ ]* 13.2 编写降级链完整性属性测试
    - **Property 4: 降级链完整性**
    - 使用 jqwik 验证：任意 Agent 配置，FallbackExecutor 的降级链至少包含一个 generic Agent
    - **Validates: Requirements 10.5**

  - [ ]* 13.3 编写 FallbackExecutor 单元测试
    - 测试降级链顺序执行
    - 测试超时触发降级
    - 测试 qualityGate 不达标触发降级
    - 测试所有 Agent 失败时的兜底回复
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [ ] 14. Checkpoint — 确保后端核心模块全部测试通过
  - 确保所有测试通过，ask the user if questions arise.


- [ ] 15. 安全认证与幻觉治理
  - [x] 15.1 实现 Spring Security + JWT 认证
    - 配置 Spring Security 过滤链，集成 JWT Token 认证
    - 实现 Token 过期自动刷新机制
    - 实现用户间对话数据隔离（向量库按 userId 过滤）
    - 实现输入过滤防止 Prompt Injection 攻击
    - _Requirements: 15.1, 15.2, 15.3, 15.4_

  - [x] 15.2 实现幻觉治理三层防护
    - 实现生成层：RAG 注入相关知识减少幻觉
    - 实现验证层：Evaluator 交叉验证 Agent 输出
    - 创建 `HallucinationGuard`：检测输出与知识库已知事实矛盾时标记 flagged
    - 检测到幻觉时触发双 Agent 博弈重新生成或标记需人工复核
    - _Requirements: 16.1, 16.2, 16.3, 16.4_

  - [ ]* 15.3 编写幻觉检测覆盖属性测试
    - **Property 10: 幻觉检测覆盖**
    - 使用 jqwik 验证：若 Agent 输出与知识库已知事实矛盾，HallucinationGuard.check() 返回 flagged 状态
    - **Validates: Requirements 16.3**

- [ ] 16. WebSocket 通信与请求处理主流程
  - [x] 16.1 实现 WebSocket (STOMP/SockJS) 服务端配置
    - 配置 Spring WebSocket STOMP 端点和消息代理
    - 实现 WebSocket 消息处理器，接收用户消息并路由到编排引擎
    - 实现流式 chunk 推送（content, progress, done, error 四种类型）
    - 确保消息推送顺序与服务端生成顺序一致
    - _Requirements: 12.1, 12.2, 12.3, 12.6_

  - [x] 16.2 实现请求处理主流程编排
    - 实现 processRequest 主算法：意图识别 → 上下文构建 → 路由执行 → 持久化 → 流式返回
    - DIRECT_AGENT 模式：通过 FallbackExecutor 执行
    - SKILL 模式：通过 SkillExecutor 执行（含参数校验）
    - AGENT 模式：通过 FallbackExecutor 执行
    - 集成 RequestTrace 记录全链路追踪
    - _Requirements: 1.1, 1.2, 1.3, 3.7, 10.1, 13.2, 17.6_

  - [x] 16.3 实现限流与审计日志
    - 在 API Gateway 层实施限流（防止单用户过度消耗 LLM 资源）
    - 实现审计日志记录所有操作
    - 实现 L2+ 操作钉钉通知
    - _Requirements: 15.5, 15.6, 17.3_

  - [ ]* 16.4 编写消息顺序性属性测试
    - **Property 9: 消息顺序性**
    - 使用 jqwik 验证：conversation 中 messages 按 timestamp 严格递增排序，WebSocket 推送顺序与服务端生成顺序一致
    - **Validates: Requirements 13.4, 12.6**

  - [ ]* 16.5 编写 WebSocket 通信集成测试
    - 测试 WebSocket 连接建立与消息收发
    - 测试流式 chunk 推送的四种类型
    - 测试断线重连与消息恢复
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

- [ ] 17. Checkpoint — 确保后端全部模块集成测试通过
  - 确保所有测试通过，ask the user if questions arise.


- [ ] 18. 前端项目初始化与基础组件
  - [x] 18.1 初始化 Vue 3 前端项目结构
    - 创建 Vue 3 + TypeScript + Vite 项目
    - 配置 Element Plus、Pinia 状态管理、Vue Router
    - 配置 WebSocket (STOMP/SockJS) 客户端依赖
    - 创建目录结构：views、components、composables、stores、api、types
    - _Requirements: 11.1, 12.1_

  - [x] 18.2 实现 WebSocket 客户端通信层
    - 创建 `useWebSocket` composable：建立 STOMP/SockJS 连接
    - 实现消息发送方法（自由文本、@Agent 指定、/Skill 触发）
    - 实现流式响应接收：解析 chunk 类型（content, progress, done, error）
    - 实现断线自动重连与会话恢复
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 18.3 实现 Pinia 状态管理 (ChatStore)
    - 创建 `useChatStore`：管理对话列表、当前对话、消息列表、流式状态
    - 实现 sendMessage action：发送消息并处理流式响应
    - 实现对话 CRUD：创建新对话、切换对话、删除对话
    - 实现消息追加和流式内容更新
    - _Requirements: 11.1, 11.6, 11.7, 13.1_

- [ ] 19. 前端对话界面核心组件
  - [x] 19.1 实现 ChatMessageList 消息列表组件
    - 创建消息列表组件，区分 USER/ASSISTANT/SYSTEM/TOOL 角色样式
    - 实现 Markdown 渲染（markdown-it）、代码高亮（highlight.js）
    - 实现图表展示（ECharts）和表格渲染
    - 实现流式响应逐字渲染效果
    - 实现消息加载状态展示
    - _Requirements: 11.4, 11.6, 11.8_

  - [x] 19.2 实现 ChatInputArea 输入区域组件
    - 创建消息输入组件，支持自由文本输入
    - 实现 `@` 触发 Agent 选择器：输入 `@` 时弹出可用 Agent 列表
    - 实现 `/` 触发 Skill 选择器：输入 `/` 时弹出可用 Skill 列表
    - 实现消息发送（Enter 键 / 发送按钮）
    - _Requirements: 11.1, 11.2, 11.3_

  - [x] 19.3 实现 SkillProgressPanel 技能进度组件
    - 创建 Skill 执行进度面板，展示 Workflow 步骤执行状态
    - 实现步骤进度实时更新（基于 WebSocket progress chunk）
    - 实现 HUMAN_CONFIRM 步骤的确认交互 UI
    - _Requirements: 11.5, 11.9_

  - [x] 19.4 实现 SkillParamDialog 参数补充对话框
    - 创建 el-dialog 参数表单组件
    - 根据 Skill inputParams 动态生成表单字段（STRING, NUMBER, ENUM, INSTANCE_SELECTOR）
    - 实现必填参数校验
    - 用户填写后重新提交 Skill 执行
    - _Requirements: 11.9, 3.7_

  - [ ]* 19.5 编写前端输入解析属性测试
    - 使用 fast-check 验证：任意用户输入，InputArea 正确识别 `@`/`/` 前缀
    - **Validates: Requirements 11.2, 11.3**

- [ ] 20. 前端对话历史与页面集成
  - [x] 20.1 实现 ConversationHistory 对话历史面板
    - 创建侧边栏对话历史列表组件
    - 实现对话列表展示（标题、时间、使用的 Agent/Skill 标签）
    - 实现对话切换：点击切换当前对话并加载历史消息
    - 实现对话管理：新建、重命名、删除对话
    - _Requirements: 11.7_

  - [x] 20.2 实现 SkillCards 技能卡片展示
    - 创建 Skill 卡片网格组件，按分类展示可用 Skill
    - 实现卡片点击触发 Skill 执行
    - 实现 Skill 搜索和分类筛选
    - _Requirements: 2.3, 11.1_

  - [x] 20.3 集成主页面布局
    - 创建 ChatView 主页面：左侧对话历史 + 中间消息区域 + 右侧 Skill 卡片
    - 集成所有子组件：ChatMessageList、ChatInputArea、SkillProgressPanel、ConversationHistory、SkillCards
    - 实现响应式布局适配
    - _Requirements: 11.1, 11.7_

  - [ ]* 20.4 编写前端消息渲染属性测试
    - 使用 fast-check 验证：任意 Message 数据，ChatMessageList 总是正确渲染
    - **Validates: Requirements 11.4, 11.8**

- [ ] 21. Checkpoint — 确保前端组件测试通过
  - 确保所有测试通过，ask the user if questions arise.


- [ ] 22. 前后端集成与可观测性
  - [x] 22.1 实现前后端 WebSocket 端到端集成
    - 前端 WebSocket 客户端连接后端 STOMP 端点
    - 实现完整消息流：用户输入 → WebSocket 发送 → 后端处理 → 流式推送 → 前端渲染
    - 实现 JWT Token 在 WebSocket 握手中的传递和验证
    - _Requirements: 12.1, 12.2, 15.1_

  - [x] 22.2 实现全链路追踪与可观测性
    - 集成 SkyWalking 或 OpenTelemetry 实现全链路追踪
    - 为每个请求生成 RequestTrace 记录（Agent 调用链路、工具调用链路、总延迟）
    - 实现首字延迟监控（目标 < 500ms）
    - 实现向量检索延迟监控（目标 < 100ms）
    - _Requirements: 17.1, 17.4, 17.5, 17.6_

  - [ ]* 22.3 编写端到端集成测试
    - 测试完整对话流程：自由文本 → 意图识别 → Agent 调用 → 流式返回 → 前端渲染
    - 测试 @Agent 指定模式端到端流程
    - 测试 /Skill 触发模式端到端流程（含参数补充）
    - 测试 PAAL 循环端到端流程
    - _Requirements: 1.1, 1.2, 1.3, 3.1, 5.1, 12.2_

- [ ] 23. Final Checkpoint — 确保全部测试通过
  - 确保所有测试通过，ask the user if questions arise.

## Notes

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用具体的需求编号以确保可追溯性
- Checkpoint 任务确保增量验证，及时发现问题
- 属性测试验证设计文档中定义的 10 个正确性属性
- 后端使用 jqwik 进行属性测试，前端使用 fast-check
- 单元测试验证具体示例和边界条件
