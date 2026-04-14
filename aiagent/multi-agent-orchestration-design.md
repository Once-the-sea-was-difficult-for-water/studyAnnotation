# 多智能体编排平台 — 技术设计方案

> 参考产品形态：类 RDS AI 助手，支持对话式交互、预置技能（Skills）、专属 Agent（@Agent）路由、历史对话管理。
>
> 融合业界最新实践：信通院 SRE Agent 四层参考模型 + PAAL 循环引擎、Anthropic Harness 框架（双 Agent 博弈 + 三代理协作）、RAG 知识库增强。

---

## 1. 产品功能概览

核心交互模式：
- 自由对话：用户直接输入问题，由 Router 自动匹配最合适的 Agent
- 技能触发：用户点击 Skill 卡片或输入 /skill-name，直接调用预置诊断流程
- @Agent 指定：用户通过 @agent-name 指定某个专属 Agent 处理
- 历史对话：左侧面板管理多轮会话，支持上下文延续

---

## 2. 系统架构（信通院四层参考模型 + Harness 增强）

参考中国信通院《运维智能体（SRE Agent）能力要求》四层架构标准，结合 Anthropic Harness 框架理念：

```
┌─────────────────────────────────────────────────────────────────┐
│  第四层：场景层 — Frontend (Vue 3 + Element Plus)                │
│  Chat UI / Skill Cards / @Agent Selector / History Panel        │
└────────────────────────────────┬────────────────────────────────┘
                                 │ WebSocket (STOMP/SockJS)
┌────────────────────────────────▼────────────────────────────────┐
│  第三层：协同能力层 — API Gateway + 安全边界                      │
│  Spring Security 认证 / 分级授权矩阵 / 限流 / 审计日志           │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────┐
│  第二层：核心层 — Orchestrator 编排引擎（PAAL 循环驱动）          │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │  Plan    │→│  Act     │→│  Assess  │→│  Learn   │        │
│  │  规划    │  │  执行    │  │  评估    │  │  学习    │        │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │
│                                                                 │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────────┐       │
│  │ Intent      │ │ Skill        │ │ Agent Router       │       │
│  │ Recognizer  │ │ Registry     │ │ (主管+专家模式)     │       │
│  └─────────────┘ └──────────────┘ └────────────────────┘       │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────────┐       │
│  │ Context Mgr │ │ Harness      │ │ RAG Knowledge      │       │
│  │ + 上下文重置 │ │ Controller   │ │ Base               │       │
│  └─────────────┘ └──────────────┘ └────────────────────┘       │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────┐
│  第一层：智能体底座 — Agent Adapter Layer (Java SPI)             │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐     │
│  │ LLM Agent │ │ Rule Agent│ │ API Agent │ │ Hybrid Agent│     │
│  │ (大模型)   │ │ (规则引擎) │ │ (外部API)  │ │ (混合型)     │     │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘     │
│  模型管理 / 高可用容灾 / Agent 自维护 / 健康检查                  │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────┐
│  Tool & Data Layer                                              │
│  DB Query / Metrics / Log Analyzer / CMDB / External API        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心数据模型

### 3.1 Skill（技能）定义

```java
@Data
public class Skill {
    private String id;                          // e.g. "disk-space-diagnose"
    private String name;                        // e.g. "磁盘空间分析"
    private String description;
    private String icon;
    private SkillCategory category;             // DIAGNOSE, OPTIMIZE, MONITOR, SECURITY
    private String agentId;                     // 技能绑定的 Agent
    private List<SkillParam> inputParams;       // 技能需要的输入参数
    private SkillWorkflow workflow;             // 技能执行的工作流
    private DisplayConfig display;
    private List<String> permissions;
    private RateLimitConfig rateLimit;
}

@Data
public class SkillParam {
    private String name;
    private ParamType type;                     // STRING, NUMBER, ENUM, INSTANCE_SELECTOR
    private boolean required;
    private Object defaultValue;
    private String description;
    private List<String> enumValues;
}

@Data
public class SkillWorkflow {
    private List<WorkflowStep> steps;
}

@Data
public class WorkflowStep {
    private String id;
    private StepType type;                      // TOOL_CALL, LLM_CALL, CONDITION, PARALLEL, HUMAN_CONFIRM
    private String toolName;
    private String prompt;
    private String condition;
    private List<Branch> branches;
    private List<WorkflowStep> children;        // parallel 子步骤
    private String outputKey;                   // 结果存入上下文的 key
}
```

### 3.2 Agent 注册

```java
@Data
public class AgentManifest {
    private String id;
    private String name;                        // 展示名，如 "RDS 诊断专家"
    private String avatar;
    private AgentType type;                     // LLM, RULE, API, HYBRID
    private String platform;                    // openai, claude, qwen, custom
    private List<AgentCapability> capabilities;
    private AgentConfig config;
    private String fallbackAgentId;             // 降级到哪个 Agent
}

@Data
public class AgentCapability {
    private String domain;                      // e.g. "database-diagnose"
    private List<String> skills;                // 该 Agent 支持的 Skill ID 列表
    private double confidence;
}

@Data
public class AgentConfig {
    private String model;
    private Double temperature;
    private String systemPrompt;
    private List<String> tools;                 // 可调用的 Tool ID 列表
    private int maxConcurrency;
    private int timeoutMs;
}
```

### 3.3 对话与会话

```java
@Data
@TableName("conversation")
public class Conversation {
    private String id;
    private String userId;
    private String title;                       // 自动生成或用户自定义
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField(exist = false)
    private List<Message> messages;
    private String agentsUsedJson;              // JSON 序列化
    private String skillsUsedJson;
}

@Data
@TableName("message")
public class Message {
    private String id;
    private String conversationId;
    private MessageRole role;                   // USER, ASSISTANT, SYSTEM, TOOL
    private String content;
    private LocalDateTime timestamp;
    private String agentId;
    private String skillId;
    private String traceId;
    private String attachmentsJson;             // 图表、表格等富文本结果（JSON）
}
```

---

## 4. 核心流程设计

### 4.1 请求处理主流程

```
用户输入
  │
  ▼
┌─────────────────┐
│ 输入解析         │  解析 @agent、/skill、纯文本三种模式
└────────┬────────┘
         │
    ┌────▼────┐
    │ 模式判断 │
    └────┬────┘
         │
    ┌────┴──────────────────────┐
    │            │              │
    ▼            ▼              ▼
 @Agent       /Skill        自由对话
 指定模式      触发模式       智能路由
    │            │              │
    ▼            ▼              ▼
 直接路由     查找 Skill     Intent Recognition
 到指定Agent  加载 Workflow   → Skill 匹配 or Agent 匹配
    │            │              │
    └────────────┴──────┬───────┘
                        │
                   ┌────▼─────┐
                   │ 构建上下文 │  合并会话历史 + 参数 + 系统 Prompt
                   └────┬─────┘
                        │
                   ┌────▼─────┐
                   │ 执行引擎  │  按 Workflow 步骤 or 单次 Agent 调用
                   └────┬─────┘
                        │
                   ┌────▼──────┐
                   │ 结果组装   │  格式化、渲染模板、生成图表
                   └────┬──────┘
                        │
                   ┌────▼──────┐
                   │ 流式返回   │  通过 WebSocket/SSE 推送给前端
                   └───────────┘
```

### 4.2 意图识别与路由

```java
@Service
public class IntentRecognizer {

    @Autowired private SkillRegistry skillRegistry;
    @Autowired private IntentClassifier classifier;

    public RoutingDecision recognize(String input, ConversationContext context) {
        // 1. 显式指令解析
        if (input.startsWith("@")) {
            String agentId = parseAgentMention(input);
            return RoutingDecision.directAgent(agentId, stripMention(input));
        }
        if (input.startsWith("/")) {
            String skillId = parseSkillCommand(input);
            Map<String, Object> params = parseParams(input);
            return RoutingDecision.skill(skillId, params);
        }

        // 2. 智能路由：LLM 分类 + 关键词匹配
        ClassificationResult classification = classifier.classify(input,
            skillRegistry.list(),
            context.getRecentMessages(5)
        );

        if (classification.getMatchedSkill() != null && classification.getConfidence() > 0.8) {
            return RoutingDecision.skill(
                classification.getMatchedSkill(),
                classification.getExtractedParams()
            );
        }

        return RoutingDecision.agent(classification.getBestAgent(), input);
    }
}

@Data
@Builder
public class RoutingDecision {
    private RoutingMode mode;           // DIRECT_AGENT, SKILL, AGENT
    private String agentId;
    private String skillId;
    private String query;
    private Map<String, Object> params;

    public static RoutingDecision directAgent(String agentId, String query) {
        return builder().mode(RoutingMode.DIRECT_AGENT).agentId(agentId).query(query).build();
    }
    public static RoutingDecision skill(String skillId, Map<String, Object> params) {
        return builder().mode(RoutingMode.SKILL).skillId(skillId).params(params).build();
    }
    public static RoutingDecision agent(String agentId, String query) {
        return builder().mode(RoutingMode.AGENT).agentId(agentId).query(query).build();
    }
}
```

### 4.3 Skill 执行引擎

```java
@Service
@Slf4j
public class SkillExecutor {

    @Autowired private AgentRegistry agentRegistry;
    @Autowired private ToolRunner toolRunner;
    @Autowired private TemplateEngine templateEngine;
    @Autowired private SseEmitterManager sseManager;

    public SkillResult execute(Skill skill, Map<String, Object> params, SharedContext context) {
        AgentAdapter agent = agentRegistry.getAdapter(skill.getAgentId());
        Map<String, Object> results = new LinkedHashMap<>();

        for (WorkflowStep step : skill.getWorkflow().getSteps()) {
            executeStep(step, params, results, context, agent);
            // 流式推送中间进度
            sseManager.emitProgress(context.getSessionId(), skill.getId(), step.getId(), results.get(step.getOutputKey()));
        }

        return new SkillResult(skill.getId(), results, skill.getDisplay().getResultTemplate());
    }

    private void executeStep(WorkflowStep step, Map<String, Object> params,
                             Map<String, Object> results, SharedContext context, AgentAdapter agent) {
        switch (step.getType()) {
            case TOOL_CALL -> {
                Map<String, Object> toolInput = new HashMap<>(params);
                toolInput.putAll(results);
                results.put(step.getOutputKey(), toolRunner.run(step.getToolName(), toolInput));
            }
            case LLM_CALL -> {
                String prompt = templateEngine.render(step.getPrompt(), Map.of(
                    "params", params, "results", results, "context", context
                ));
                results.put(step.getOutputKey(), agent.invoke(UnifiedRequest.of(prompt, context)));
            }
            case CONDITION -> {
                step.getBranches().stream()
                    .filter(b -> evaluateCondition(b.getWhen(), results))
                    .findFirst()
                    .ifPresent(branch -> branch.getThen().forEach(
                        sub -> executeStep(sub, params, results, context, agent)
                    ));
            }
            case PARALLEL -> {
                List<CompletableFuture<Object>> futures = step.getChildren().stream()
                    .map(child -> CompletableFuture.supplyAsync(() -> {
                        Map<String, Object> childResults = new HashMap<>(results);
                        executeStep(child, params, childResults, context, agent);
                        return childResults.get(child.getOutputKey());
                    }))
                    .toList();
                results.put(step.getOutputKey(),
                    futures.stream().map(CompletableFuture::join).toList());
            }
            case HUMAN_CONFIRM -> {
                results.put(step.getOutputKey(), waitForUserConfirm(step, results));
            }
        }
    }
}
```

---

## 5. Agent Adapter Layer

统一不同类型 Agent 的调用协议：

```java
public abstract class AgentAdapter {
    public abstract UnifiedResponse invoke(UnifiedRequest request);
    public abstract Flux<UnifiedChunk> stream(UnifiedRequest request);  // Reactor 响应式流
    public abstract void cancel(String taskId);
    protected abstract Object toNativeFormat(UnifiedContext ctx);
    protected abstract UnifiedResponse fromNativeFormat(Object resp);
}

// LLM Agent — 调用大模型
@Component
public class LLMAgentAdapter extends AgentAdapter {
    @Autowired private LLMClient llmClient;
    private AgentConfig config;

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        var nativeReq = toNativeFormat(request.getContext());
        var response = llmClient.chat(ChatRequest.builder()
            .model(config.getModel())
            .messages(((NativeRequest) nativeReq).getMessages())
            .tools(mapTools(request.getTools()))
            .temperature(config.getTemperature())
            .build());
        return fromNativeFormat(response);
    }

    @Override
    public Flux<UnifiedChunk> stream(UnifiedRequest request) {
        var nativeReq = toNativeFormat(request.getContext());
        return llmClient.streamChat(ChatRequest.builder()
            .model(config.getModel())
            .messages(((NativeRequest) nativeReq).getMessages())
            .build())
            .map(this::toUnifiedChunk);
    }
}

// Rule Agent — 基于规则引擎，不调用 LLM
@Component
public class RuleAgentAdapter extends AgentAdapter {
    @Autowired private RuleEngine ruleEngine;

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        RuleResult result = ruleEngine.evaluate(request.getContext().getInput(), rules);
        return UnifiedResponse.of(result.getMessage(), result.getData());
    }
}

// API Agent — 直接调用外部 API 并格式化结果
@Component
public class APIAgentAdapter extends AgentAdapter {
    @Autowired private RestTemplate restTemplate;

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        ResponseEntity<String> apiResult = restTemplate.exchange(
            buildRequest(request), String.class);
        return UnifiedResponse.of(formatResult(apiResult.getBody()), apiResult.getBody());
    }
}
```

---

## 6. Skill 注册中心

### 6.1 Skill 注册与发现

```java
@Service
public class SkillRegistry {

    private final ConcurrentHashMap<String, Skill> skills = new ConcurrentHashMap<>();
    @Autowired private EmbeddingService embedder;
    @Autowired private VectorStore vectorIndex;
    @Autowired private SseEmitterManager sseManager;

    public void register(Skill skill) {
        validateSkill(skill);
        skills.put(skill.getId(), skill);
        indexForSearch(skill);          // 建立搜索索引，支持模糊匹配
    }

    // 根据用户输入匹配最相关的 Skill
    public List<ScoredSkill> match(String query, int topK) {
        float[] embedding = embedder.embed(query);
        return vectorIndex.search(embedding, topK);
    }

    // 按分类获取 Skill 列表（用于前端卡片展示）
    public List<Skill> listByCategory(SkillCategory category) {
        return skills.values().stream()
            .filter(s -> category == null || s.getCategory() == category)
            .toList();
    }

    // 热加载：运行时动态注册新 Skill（配合 Nacos 配置变更监听）
    @NacosConfigListener(dataId = "skills-config", groupId = "agent-platform")
    public void hotReload(String configContent) {
        Skill skill = yamlParser.parse(configContent, Skill.class);
        register(skill);
        sseManager.broadcast("skill_added", skill);
    }
}
```

### 6.2 Skill 配置示例（YAML）

```yaml
# skills/disk-space-diagnose.yaml
id: disk-space-diagnose
name: 磁盘空间分析
description: 分析指定实例或地域的磁盘空间使用情况，给出优化建议
icon: disk
category: diagnose
agentId: rds-diagnose-agent

inputParams:
  - name: instanceId
    type: instance_selector
    required: false
    description: 目标实例（不填则分析全部）
  - name: region
    type: enum
    required: false
    enumValues: [cn-hangzhou, cn-shanghai, cn-beijing]

workflow:
  steps:
    - id: collect_metrics
      type: tool_call
      toolName: rds-disk-metrics
      outputKey: diskMetrics

    - id: analyze
      type: llm_call
      prompt: |
        根据以下磁盘使用数据，分析空间使用趋势并给出优化建议：
        {{diskMetrics}}
        要求：1. 列出 Top 5 占用最高的实例 2. 预测未来 7 天趋势 3. 给出具体优化建议
      outputKey: analysis

    - id: check_critical
      type: condition
      condition: diskMetrics.maxUsagePercent > 90
      branches:
        - when: "true"
          then:
            - id: alert
              type: tool_call
              toolName: send-alert
              outputKey: alertResult
        - when: "false"
          then: []

display:
  cardStyle: default
  resultTemplate: disk-analysis-template
```

---

## 7. 上下文管理

```java
@Service
public class ContextManager {

    @Autowired private VectorStore vectorStore;
    @Autowired private Summarizer summarizer;

    // 构建发送给 Agent 的上下文
    public SharedContext buildContext(Conversation conversation, RoutingDecision routing, AgentManifest agent) {
        // 1. 截取最近 N 轮对话（避免 token 超限）
        List<Message> recentMessages = truncate(conversation.getMessages(), agent.getConfig().getMaxContextTokens());

        // 2. 如果是 Skill 模式，注入 Skill 的 system prompt
        String systemPrompt = routing.getSkillId() != null
            ? buildSkillPrompt(routing.getSkillId(), agent)
            : agent.getConfig().getSystemPrompt();

        // 3. 从向量库检索相关历史（跨会话记忆）
        List<MemoryFragment> longTermMemory = vectorStore.search(routing.getQuery(),
            SearchFilter.builder().userId(conversation.getUserId()).topK(5).build());

        return SharedContext.builder()
            .sessionId(conversation.getId())
            .systemPrompt(systemPrompt)
            .messages(recentMessages)
            .longTermMemory(longTermMemory)
            .currentParams(routing.getParams() != null ? routing.getParams() : Map.of())
            .build();
    }

    // 对话结束后，将关键信息存入向量库
    public void persist(Conversation conversation, Object result) {
        String summary = summarizer.summarize(conversation.getMessages());
        vectorStore.upsert(VectorDocument.builder()
            .id(conversation.getId())
            .content(summary)
            .metadata(Map.of(
                "userId", conversation.getUserId(),
                "skills", conversation.getSkillsUsedJson(),
                "timestamp", LocalDateTime.now().toString()
            ))
            .build());
    }
}
```

---

## 8. 前端交互设计（Vue 3 + Element Plus）

### 8.1 组件结构

```
App.vue
├── AppSidebar.vue
│   ├── el-button (新建对话)
│   └── ConversationList.vue
│       └── ConversationItem.vue (title, timestamp, preview)
│
├── MainPanel.vue
│   ├── WelcomeScreen.vue (首次进入时展示)
│   │   ├── SuggestedQuestions.vue (预置问题推荐，el-card 列表)
│   │   └── SkillCardGrid.vue
│   │       └── SkillCard.vue (el-card: icon, name, id, arrow →)
│   │
│   ├── ChatMessageList.vue
│   │   ├── UserMessage.vue
│   │   ├── AssistantMessage.vue
│   │   │   ├── MarkdownRenderer.vue (v-html + highlight.js)
│   │   │   ├── ChartWidget.vue (ECharts 图表)
│   │   │   ├── TableWidget.vue (el-table)
│   │   │   └── SkillResultCard.vue (技能执行结果)
│   │   └── ProgressIndicator.vue (el-steps 技能执行中间状态)
│   │
│   └── InputArea.vue
│       ├── AgentSelector.vue (el-popover + el-tag，@触发)
│       ├── SkillTrigger.vue (el-autocomplete，/触发)
│       ├── el-input (文本输入)
│       └── el-button (发送)
```

### 8.2 关键 Vue 组合式 API 示例

```vue
<!-- composables/useChat.ts -->
<script setup lang="ts">
import { ref, reactive } from 'vue'

// 对话状态管理（Pinia Store）
export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<string | null>(null)
  const messages = ref<Message[]>([])
  const isStreaming = ref(false)

  // WebSocket 连接
  const ws = ref<WebSocket | null>(null)

  function connectWebSocket(sessionId: string) {
    ws.value = new WebSocket(`${import.meta.env.VITE_WS_URL}/chat/${sessionId}`)
    ws.value.onmessage = (event) => {
      const chunk = JSON.parse(event.data)
      if (chunk.type === 'content') {
        appendToLastMessage(chunk.content)
      } else if (chunk.type === 'progress') {
        updateSkillProgress(chunk.skillId, chunk.stepId, chunk.data)
      } else if (chunk.type === 'done') {
        isStreaming.value = false
      }
    }
  }

  async function sendMessage(input: string, agentId?: string, skillId?: string) {
    messages.value.push({ role: 'user', content: input, timestamp: new Date() })
    isStreaming.value = true
    ws.value?.send(JSON.stringify({ input, agentId, skillId }))
  }

  return { conversations, currentConversationId, messages, isStreaming, connectWebSocket, sendMessage }
})
</script>
```

```vue
<!-- components/InputArea.vue -->
<template>
  <div class="input-area">
    <el-tag v-if="selectedAgent" closable @close="selectedAgent = null" type="primary" class="agent-tag">
      @{{ selectedAgent.name }}
    </el-tag>
    <el-autocomplete
      v-model="inputText"
      :fetch-suggestions="handleAutoComplete"
      placeholder="输入问题，输入 @ 选择专属Agent，输入 / 触发技能"
      @keyup.enter="handleSend"
      @input="handleInput"
      class="chat-input"
    />
    <el-button type="primary" :icon="Promotion" :loading="chatStore.isStreaming" @click="handleSend" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import { useChatStore } from '@/composables/useChat'

const chatStore = useChatStore()
const inputText = ref('')
const selectedAgent = ref(null)
const showAgentPopover = ref(false)
const showSkillPopover = ref(false)

function handleInput(value: string) {
  if (value.endsWith('@')) showAgentPopover.value = true
  if (value.endsWith('/')) showSkillPopover.value = true
}

function handleSend() {
  if (!inputText.value.trim()) return
  chatStore.sendMessage(inputText.value, selectedAgent.value?.id)
  inputText.value = ''
}
</script>
```

### 8.3 关键交互流程

```
1. 技能卡片点击：
   SkillCard @click → 如果 Skill 有必填参数 → el-dialog 弹出参数表单 → 提交
                     → 如果无必填参数 → 直接调用 chatStore.sendMessage(null, null, skillId)

2. @Agent 选择：
   输入 "@" → el-popover 弹出 Agent 列表 → 选择后 el-tag 显示在输入框 → 后续消息路由到该 Agent

3. /Skill 触发：
   输入 "/" → el-autocomplete 弹出 Skill 列表 → 选择后自动填充 → 回车执行

4. 流式响应：
   WebSocket onmessage → 逐字追加到 messages 最后一条 → v-html 实时渲染 Markdown
```

---

## 9. 容错与可观测性

### 9.1 Fallback 策略

```java
@Service
@Slf4j
public class FallbackExecutor {

    @Autowired private AgentRegistry agentRegistry;
    @Autowired private AdapterFactory adapterFactory;

    public UnifiedResponse execute(RoutingDecision routing, SharedContext context) {
        AgentManifest agent = agentRegistry.get(routing.getAgentId());
        List<AgentManifest> chain = buildFallbackChain(agent);  // [primary, fallback1, fallback2, generic]

        for (AgentManifest candidate : chain) {
            try {
                AgentAdapter adapter = adapterFactory.create(candidate);
                UnifiedResponse result = executeWithTimeout(
                    () -> adapter.invoke(UnifiedRequest.of(context)),
                    Duration.ofMillis(candidate.getConfig().getTimeoutMs())
                );
                if (qualityGate(result)) return result;
            } catch (Exception e) {
                log.warn("Agent {} failed, trying next", candidate.getId(), e);
            }
        }

        // 所有 Agent 都失败，返回兜底回复
        return UnifiedResponse.of("抱歉，当前服务繁忙，请稍后重试或联系管理员。", null);
    }

    private <T> T executeWithTimeout(Supplier<T> task, Duration timeout) {
        return CompletableFuture.supplyAsync(task::get)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .join();
    }
}
```

### 9.2 可观测性

```java
@Data
@Builder
public class RequestTrace {
    private String traceId;
    private String userId;
    private String conversationId;
    private String input;
    private RoutingDecision routing;
    private List<AgentCallTrace> agentCalls;
    private List<ToolCallTrace> toolCalls;
    private long totalLatencyMs;
    private String finalResponse;
}

@Data @Builder
public class AgentCallTrace {
    private String agentId;
    private long latencyMs;
    private int inputTokens;
    private int outputTokens;
    private BigDecimal cost;
    private boolean success;
    private String error;
}

@Data @Builder
public class ToolCallTrace {
    private String toolName;
    private long latencyMs;
    private boolean success;
}
```

监控看板关键指标：
- 请求量 / QPS（按 Skill、Agent 维度）
- 平均响应时间（P50 / P95 / P99）
- Agent 成功率 / Fallback 触发率
- Token 消耗与成本
- Skill 使用热力图

---

## 10. 技术选型

| 层级 | 推荐方案 | 说明 |
|------|---------|------|
| 前端 | Vue 3 + Element Plus | 对话 UI + 技能卡片 + Markdown 渲染 |
| 实时通信 | WebSocket (STOMP over SockJS) | 流式响应推送，兼容 Spring 生态 |
| 后端框架 | Spring Boot 3 + Spring WebFlux | 响应式编程，支持 SSE/WebSocket |
| 编排引擎 | Java 自研 + Temporal | 工作流编排与状态管理 |
| LLM 网关 | Spring AI / 自研统一网关 | 屏蔽多模型差异，统一调用协议 |
| 向量存储 | Milvus / Qdrant | Skill 匹配 + 长期记忆 |
| 消息队列 | RocketMQ / Kafka | 异步任务与事件驱动 |
| 存储 | MySQL + Redis | 会话持久化 + 缓存 |
| 可观测性 | SkyWalking / OpenTelemetry + Grafana | 全链路追踪 + 监控 |
| Skill 配置 | YAML + Git 管理 + Nacos | 版本化、可审计、动态下发 |
| 权限认证 | Spring Security + JWT | 统一认证鉴权 |

---

## 11. PAAL 循环引擎（Plan-Act-Assess-Learn）

参考中国信通院《运维智能体（SRE Agent）能力要求》中的核心引擎模型，每次 Skill 执行或 Agent 对话都遵循 PAAL 闭环：

```
数据输入（监控/日志/用户问题）
        │
        ▼
┌───────────────┐
│ Plan（规划）   │  分解任务，制定处置步骤，生成执行 DAG
└───────┬───────┘
        │
┌───────▼───────┐
│ Act（执行）    │  调用工具、Agent、API 执行操作
└───────┬───────┘
        │
┌───────▼───────┐
│ Assess（评估） │  验证执行效果，判断是否达标或需要继续
└───────┬───────┘
        │
┌───────▼───────┐
│ Learn（学习）  │  记录本次处置过程，更新知识库，优化后续决策
└───────────────┘
```

```java
@Service
@Slf4j
public class PAALEngine {

    @Autowired private TaskPlanner planner;
    @Autowired private SkillExecutor executor;
    @Autowired private ResultAssessor assessor;
    @Autowired private KnowledgeBaseService knowledgeBase;

    /**
     * PAAL 循环主入口，最多执行 maxIterations 轮
     */
    public PAALResult run(PAALInput input, SharedContext context, int maxIterations) {
        PAALResult result = null;

        for (int i = 0; i < maxIterations; i++) {
            // Plan：分解任务
            ExecutionPlan plan = planner.plan(input, context);
            log.info("[PAAL] iteration={}, plan steps={}", i, plan.getSteps().size());

            // Act：执行计划
            ExecutionOutput output = executor.executePlan(plan, context);

            // Assess：评估结果
            AssessmentResult assessment = assessor.assess(output, input.getExpectation());

            if (assessment.isPassed()) {
                result = PAALResult.success(output, assessment);
                break;
            }

            // 未通过评估，将反馈注入下一轮 Plan 的上下文
            context.addFeedback(assessment.getFeedback());
            log.info("[PAAL] iteration={}, assessment failed, reason={}", i, assessment.getReason());
        }

        // Learn：无论成功失败，都记录到知识库
        knowledgeBase.record(KnowledgeEntry.builder()
            .input(input.getDescription())
            .plan(planner.getLastPlan())
            .result(result)
            .timestamp(LocalDateTime.now())
            .build());

        return result != null ? result : PAALResult.failed("超过最大迭代次数仍未通过评估");
    }
}
```

PAAL 循环的关键价值：
- Plan 阶段引入 RAG 检索历史相似案例，避免从零推理
- Assess 阶段独立于执行 Agent，避免"自我评估偏差"
- Learn 阶段自动沉淀经验，Agent 越用越聪明

---

## 12. Multi-Agent 协同：主管 + 专家模式

参考 Agentic Ops 实践中最成熟的 Multi-Agent 架构——"主管 Agent + 专业 Agent"模式：

```
                  ┌──────────────────────┐
                  │   Supervisor Agent   │
                  │   （主管：任务分解、   │
                  │    分发、整合结果）    │
                  └──────────┬───────────┘
                             │
          ┌──────────┬───────┴───────┬──────────┐
          ▼          ▼               ▼          ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
   │ 日志 Agent │ │ 指标 Agent │ │ K8s Agent  │ │ 知识库Agent│
   │ (语义分析) │ │ (异常检测) │ │ (集群操作) │ │ (案例检索) │
   └────────────┘ └────────────┘ └────────────┘ └────────────┘
```

```java
@Service
@Slf4j
public class SupervisorAgent {

    @Autowired private AgentRegistry agentRegistry;
    @Autowired private TaskDecomposer decomposer;
    @Autowired private ResultMerger merger;

    /**
     * 主管 Agent 接收任务，分解后分发给专家 Agent，汇总结果
     */
    public SupervisorResult orchestrate(String task, SharedContext context) {
        // 1. 任务分解：拆成多个子任务
        List<SubTask> subTasks = decomposer.decompose(task, context);

        // 2. 判断哪些子任务可以并行
        Map<Boolean, List<SubTask>> grouped = subTasks.stream()
            .collect(Collectors.partitioningBy(SubTask::isParallelizable));

        List<SubTaskResult> allResults = new ArrayList<>();

        // 3. 并行执行独立子任务
        List<SubTask> parallelTasks = grouped.get(true);
        if (!parallelTasks.isEmpty()) {
            List<CompletableFuture<SubTaskResult>> futures = parallelTasks.stream()
                .map(sub -> CompletableFuture.supplyAsync(() -> dispatchToExpert(sub, context)))
                .toList();
            allResults.addAll(futures.stream().map(CompletableFuture::join).toList());
        }

        // 4. 顺序执行有依赖的子任务
        for (SubTask sub : grouped.get(false)) {
            SubTaskResult prev = allResults.isEmpty() ? null : allResults.get(allResults.size() - 1);
            if (prev != null) context.addIntermediateResult(prev);
            allResults.add(dispatchToExpert(sub, context));
        }

        // 5. 主管 Agent 综合推理，整合所有结果
        return merger.merge(task, allResults, context);
    }

    private SubTaskResult dispatchToExpert(SubTask subTask, SharedContext context) {
        AgentManifest expert = agentRegistry.findBestMatch(subTask.getRequiredCapability());
        AgentAdapter adapter = agentRegistry.getAdapter(expert.getId());
        UnifiedResponse response = adapter.invoke(UnifiedRequest.of(subTask.getPrompt(), context));
        return new SubTaskResult(subTask.getId(), expert.getId(), response);
    }
}
```

典型场景示例（P0 故障处置，全程约 3 分钟）：
1. 主管 Agent 接收告警，分析影响面
2. 并行派发任务给日志 Agent 和指标 Agent
3. 两路结果汇聚，主管 Agent 综合推理定位根因
4. 发现内存泄漏，指令 K8s Agent 回滚 Deployment
5. 验证 Agent 确认服务恢复，通知 Agent 更新工单
6. 整个过程自动记录到知识库（PAAL Learn 阶段）

---

## 13. Harness 框架：双 Agent 博弈与三代理协作

参考 Anthropic Harness 框架理念，解决长程 Agent 任务的两大核心痛点。

### 13.1 痛点与解法

| 痛点 | 表现 | 解法 |
|------|------|------|
| 上下文焦虑（Context Anxiety） | Token 逼近上限时，Agent 草率结束任务，输出质量下降 | 上下文重置机制：定期归档 + 干净重启 |
| 自我评估偏差（Self-Evaluation Bias） | Agent 评价自身产出时盲目乐观，给出虚高评分 | 双 Agent 博弈：独立 Evaluator 制衡 |

### 13.2 上下文重置机制

```java
@Service
public class ContextResetManager {

    @Autowired private ContextArchiver archiver;
    @Autowired private ContextManager contextManager;

    private static final int TOKEN_THRESHOLD = 100_000;  // 触发重置的 token 阈值

    /**
     * 检查是否需要上下文重置，如需要则归档当前上下文并创建干净的新上下文
     */
    public SharedContext checkAndReset(SharedContext current) {
        if (current.estimateTokenCount() < TOKEN_THRESHOLD) {
            return current;  // 未达阈值，继续使用
        }

        // 1. 将当前任务进度写入外部归档
        ContextArchive archive = archiver.archive(current);

        // 2. 创建干净的新上下文
        SharedContext fresh = SharedContext.builder()
            .sessionId(current.getSessionId())
            .systemPrompt(current.getSystemPrompt())
            .messages(List.of())  // 清空对话历史
            .currentParams(current.getCurrentParams())
            .build();

        // 3. 注入归档摘要，让新 Agent 继承关键上下文
        fresh.addSystemMessage(String.format(
            "你正在继续一个进行中的任务。以下是之前的进度摘要：\n%s\n\n请基于此继续工作。",
            archive.getSummary()
        ));

        return fresh;
    }
}
```

### 13.3 双 Agent 博弈（Generator + Evaluator）

受 GAN（生成对抗网络）启发，引入独立的 Evaluator Agent 制衡 Generator Agent：

```java
@Service
@Slf4j
public class DualAgentExecutor {

    @Autowired private AgentRegistry agentRegistry;

    /**
     * 双 Agent 博弈执行：Generator 生成 → Evaluator 评估 → 迭代优化
     */
    public DualAgentResult execute(String task, SharedContext context, int maxRounds) {
        AgentAdapter generator = agentRegistry.getAdapter("generator-agent");
        AgentAdapter evaluator = agentRegistry.getAdapter("evaluator-agent");

        UnifiedResponse lastOutput = null;

        for (int round = 0; round < maxRounds; round++) {
            // Generator 执行任务
            String genPrompt = round == 0
                ? task
                : task + "\n\n上一轮评估反馈：\n" + context.getLastFeedback();
            lastOutput = generator.invoke(UnifiedRequest.of(genPrompt, context));

            // Evaluator 独立评估（扮演"职业差评师"）
            String evalPrompt = String.format(
                "你是一个严格的质量评估者。请评估以下输出：\n\n%s\n\n" +
                "原始任务要求：%s\n\n" +
                "请从正确性、完整性、质量三个维度打分（1-10），并列出具体问题。" +
                "如果所有维度均 >= 8 分，输出 PASS；否则输出 FAIL 并给出改进建议。",
                lastOutput.getContent(), task
            );
            UnifiedResponse evaluation = evaluator.invoke(UnifiedRequest.of(evalPrompt, context));

            log.info("[DualAgent] round={}, evaluation={}", round, evaluation.getContent());

            if (evaluation.getContent().contains("PASS")) {
                return DualAgentResult.passed(lastOutput, evaluation, round + 1);
            }

            // 将评估反馈注入下一轮上下文
            context.addFeedback(evaluation.getContent());
        }

        return DualAgentResult.maxRoundsReached(lastOutput, maxRounds);
    }
}
```

### 13.4 Full Harness 三代理协作（Planner-Generator-Evaluator）

将双 Agent 升级为完整的三代理协作系统，适用于复杂的长程任务：

```
┌──────────────┐     Sprint 契约      ┌──────────────┐
│   Planner    │ ──────────────────→  │  Generator   │
│  （规划者）   │                      │  （生成器）   │
│  需求 → 规格书│                      │  逐步开发    │
└──────────────┘                      └──────┬───────┘
                                             │ 产出
                                      ┌──────▼───────┐
                                      │  Evaluator   │
                                      │  （评估器）   │
                                      │  测试 + 打分  │
                                      └──────┬───────┘
                                             │ 反馈
                                             ▼
                                      不通过 → 回到 Generator 继续迭代
                                      通过   → 输出最终结果
```

```java
@Service
@Slf4j
public class FullHarnessExecutor {

    @Autowired private AgentRegistry agentRegistry;
    @Autowired private ContextResetManager contextResetManager;

    /**
     * Full Harness 三代理协作
     * 1. Planner 将需求扩充为完整规格书
     * 2. Generator 和 Evaluator 协商 Sprint 契约
     * 3. Generator 按 Sprint 逐步执行，Evaluator 实时验证
     */
    public HarnessResult execute(String requirement, SharedContext context) {
        AgentAdapter planner = agentRegistry.getAdapter("planner-agent");
        AgentAdapter generator = agentRegistry.getAdapter("generator-agent");
        AgentAdapter evaluator = agentRegistry.getAdapter("evaluator-agent");

        // Phase 1: Planner 生成完整规格书
        UnifiedResponse spec = planner.invoke(UnifiedRequest.of(
            "将以下需求扩充为完整的产品规格书，包含功能列表、验收标准、技术约束：\n" + requirement,
            context
        ));

        // Phase 2: Generator 和 Evaluator 协商 Sprint 契约
        UnifiedResponse contract = generator.invoke(UnifiedRequest.of(
            "基于以下规格书，制定 Sprint 计划。每个 Sprint 明确：\n" +
            "1. 要完成的功能点\n2. 完成标准\n3. 测试方式\n\n规格书：\n" + spec.getContent(),
            context
        ));
        // Evaluator 确认契约
        UnifiedResponse contractReview = evaluator.invoke(UnifiedRequest.of(
            "审查以下 Sprint 契约，确认验收标准是否明确可测：\n" + contract.getContent(),
            context
        ));

        // Phase 3: 按 Sprint 迭代执行
        List<SprintResult> sprintResults = new ArrayList<>();
        List<Sprint> sprints = parseSprints(contract.getContent());

        for (Sprint sprint : sprints) {
            // 检查上下文是否需要重置
            context = contextResetManager.checkAndReset(context);

            SprintResult result = executeSprint(sprint, generator, evaluator, context);
            sprintResults.add(result);
            context.addIntermediateResult(result);
        }

        return new HarnessResult(spec.getContent(), contract.getContent(), sprintResults);
    }

    private SprintResult executeSprint(Sprint sprint, AgentAdapter generator,
                                       AgentAdapter evaluator, SharedContext context) {
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generator 执行
            UnifiedResponse output = generator.invoke(UnifiedRequest.of(
                "执行以下 Sprint：\n" + sprint.getDescription() +
                "\n完成标准：\n" + sprint.getAcceptanceCriteria(),
                context
            ));

            // Evaluator 验证
            UnifiedResponse eval = evaluator.invoke(UnifiedRequest.of(
                "根据 Sprint 契约验证以下产出：\n" + output.getContent() +
                "\n\n验收标准：\n" + sprint.getAcceptanceCriteria() +
                "\n\n输出 PASS 或 FAIL + 具体问题。",
                context
            ));

            if (eval.getContent().contains("PASS")) {
                return SprintResult.passed(sprint.getId(), output, attempt + 1);
            }
            context.addFeedback(eval.getContent());
        }
        return SprintResult.failed(sprint.getId(), maxAttempts);
    }
}
```

核心设计要点（来自 Anthropic 实践）：
- Planner 专注产品逻辑，避免过早陷入技术细节
- 开始编码前，Generator 和 Evaluator 先协商"Sprint 契约"——明确完成标准
- Evaluator 用实际测试（而非主观判断）验证产出，形成闭环反馈
- 上下文重置确保长程任务不因 token 限制而质量下降

---

## 14. RAG 知识库增强

Agent 不是"凭空推理"，而是先检索最相似的历史案例，再结合当前上下文决策，极大降低 LLM 幻觉风险。

### 14.1 知识库内容结构

| 知识类型 | 来源 | 更新频率 | 示例 |
|---------|------|---------|------|
| 历史故障报告 | PAAL Learn 阶段自动沉淀 | 每次故障后 | CPU 飙升根因：连接池泄漏 |
| Runbook / SOP | 人工编写 + Agent 辅助结构化 | 按需更新 | MySQL 主从切换操作手册 |
| 架构与依赖关系 | CMDB 自动同步 | 每日 | 服务 A → 服务 B → MySQL 集群 |
| 近期变更记录 | CI/CD 系统自动采集 | 实时 | 2026-04-13 服务X v2.3.1 上线 |
| Skill 执行记录 | 系统自动记录 | 实时 | disk-diagnose 执行结果与建议 |

### 14.2 RAG 检索服务

```java
@Service
public class RAGKnowledgeService {

    @Autowired private VectorStore vectorStore;
    @Autowired private EmbeddingService embedder;
    @Autowired private ReRanker reRanker;

    /**
     * 检索与当前问题最相关的知识片段，注入 Agent 上下文
     */
    public List<KnowledgeFragment> retrieve(String query, RAGConfig config) {
        // 1. 向量检索：粗筛 Top-N
        float[] queryEmbedding = embedder.embed(query);
        List<KnowledgeFragment> candidates = vectorStore.search(queryEmbedding, config.getTopK() * 3);

        // 2. 时间衰减：近期知识权重更高
        candidates.forEach(c -> c.setScore(
            c.getScore() * timeDecayFactor(c.getTimestamp(), config.getDecayDays())
        ));

        // 3. ReRank：用交叉编码器精排
        List<KnowledgeFragment> reRanked = reRanker.reRank(query, candidates, config.getTopK());

        // 4. 去重 + 截断（控制注入 token 量）
        return deduplicate(reRanked).stream()
            .limit(config.getMaxFragments())
            .toList();
    }

    /**
     * PAAL Learn 阶段调用：将本次处置经验写入知识库
     */
    public void ingest(KnowledgeEntry entry) {
        float[] embedding = embedder.embed(entry.getContent());
        vectorStore.upsert(VectorDocument.builder()
            .id(entry.getId())
            .content(entry.getContent())
            .embedding(embedding)
            .metadata(Map.of(
                "type", entry.getType().name(),
                "source", entry.getSource(),
                "timestamp", entry.getTimestamp().toString(),
                "tags", String.join(",", entry.getTags())
            ))
            .build());
    }

    private double timeDecayFactor(LocalDateTime timestamp, int decayDays) {
        long daysSince = ChronoUnit.DAYS.between(timestamp, LocalDateTime.now());
        return Math.exp(-0.1 * daysSince / decayDays);
    }
}
```

---

## 15. 安全防护：分级授权与幻觉治理

### 15.1 操作分级授权矩阵

| 风险等级 | 操作示例 | 授权策略 | 审批流 |
|---------|---------|---------|--------|
| L1 只读 | 查询指标、查看日志、检索知识库 | 自动执行 | 无需审批 |
| L2 低危 | 重启 Pod、清理缓存、扩容副本 | 自动执行 + 事后通知 | 自动 + 钉钉通知 |
| L3 中危 | 数据库主从切换、修改配置 | 需人工确认 | 工单审批（1人） |
| L4 高危 | 删除集群、DROP TABLE、回滚版本 | 沙箱预演 + 人工审批 | 工单审批（2人+） |

```java
@Service
public class AuthorizationGate {

    @Autowired private OperationClassifier classifier;
    @Autowired private ApprovalService approvalService;
    @Autowired private SandboxExecutor sandbox;

    /**
     * 在 Agent 执行操作前，根据风险等级进行授权检查
     */
    public AuthResult authorize(ToolCallRequest request, SharedContext context) {
        RiskLevel level = classifier.classify(request);

        return switch (level) {
            case L1_READ_ONLY -> AuthResult.approved("只读操作，自动放行");

            case L2_LOW_RISK -> {
                // 自动执行，但记录审计日志并通知
                auditLog(request, context, "AUTO_APPROVED");
                notifyOps(request, context);
                yield AuthResult.approved("低危操作，自动执行");
            }

            case L3_MEDIUM_RISK -> {
                // 暂停执行，等待人工确认
                ApprovalTicket ticket = approvalService.createAndWait(request, context, 1);
                yield ticket.isApproved()
                    ? AuthResult.approved("人工审批通过: " + ticket.getApprover())
                    : AuthResult.denied("人工审批拒绝: " + ticket.getReason());
            }

            case L4_HIGH_RISK -> {
                // 先在沙箱预演
                SandboxResult sandboxResult = sandbox.dryRun(request);
                if (!sandboxResult.isSafe()) {
                    yield AuthResult.denied("沙箱预演失败: " + sandboxResult.getReason());
                }
                // 沙箱通过后，提交多人审批
                ApprovalTicket ticket = approvalService.createAndWait(request, context, 2);
                yield ticket.isApproved()
                    ? AuthResult.approved("沙箱通过 + 多人审批通过")
                    : AuthResult.denied("审批拒绝: " + ticket.getReason());
            }
        };
    }
}
```

### 15.2 幻觉治理三层防护

| 防护层 | 措施 | 实现方式 |
|--------|------|---------|
| 生成层 | RAG 知识注入，减少凭空推理 | 每次调用前检索相关知识片段 |
| 验证层 | 独立 Evaluator Agent 交叉验证 | 双 Agent 博弈机制（第 13 章） |
| 执行层 | 思维链透明 + 沙箱预演 | Agent 必须输出推理过程，高危操作先预演 |

```java
@Service
public class HallucinationGuard {

    @Autowired private RAGKnowledgeService ragService;
    @Autowired private DualAgentExecutor dualAgent;

    /**
     * 对 Agent 输出进行幻觉检测
     */
    public GuardResult check(UnifiedResponse agentOutput, String originalQuery, SharedContext context) {
        // 1. 事实核查：将输出中的关键断言与知识库比对
        List<KnowledgeFragment> evidence = ragService.retrieve(agentOutput.getContent(),
            RAGConfig.builder().topK(5).build());

        // 2. 一致性检查：输出是否与已知事实矛盾
        boolean hasContradiction = evidence.stream()
            .anyMatch(e -> contradicts(agentOutput.getContent(), e.getContent()));

        if (hasContradiction) {
            return GuardResult.flagged("输出与知识库已知事实存在矛盾", evidence);
        }

        // 3. 置信度检查：如果 Agent 输出中包含不确定表述，标记为需人工复核
        if (containsUncertainty(agentOutput.getContent())) {
            return GuardResult.needsReview("Agent 输出包含不确定表述，建议人工复核");
        }

        return GuardResult.passed();
    }
}
```

---

## 16. 落地路线图

参考 Agentic Ops 行动路线图，分三阶段推进：

### 第一阶段：夯实基础（0-3 个月）

- 统一数据采集：OpenTelemetry 接入 Traces/Metrics/Logs
- 知识库建设：Runbook 结构化，历史故障报告录入向量库
- 基础平台搭建：Vue 3 + Element Plus 前端 + Spring Boot 后端骨架
- 单 Agent 试点：选择低风险只读场景（告警降噪、日志分类）

### 第二阶段：能力建设（3-6 个月）

- Skill 体系上线：完成 Skill Registry + YAML 配置 + 前端卡片展示
- PAAL 循环引擎：Plan-Act-Assess-Learn 闭环跑通
- 双 Agent 博弈：Generator + Evaluator 机制上线，提升输出质量
- 分级授权矩阵：L1-L4 操作分级 + 审批流 + 沙箱预演
- RAG 知识库：向量检索 + ReRank + 时间衰减

### 第三阶段：规模化推广（6-12 个月）

- Multi-Agent 体系：主管 + 专家架构，支持复杂故障自动处置
- Full Harness 三代理：Planner-Generator-Evaluator 协作上线
- 上下文重置机制：支持长程任务不降质
- 核心指标跟踪：自动处置率、准确率、MTTR 改善率
- 知识库自进化：每次处置后自动更新，Agent 越用越聪明

---

## 17. 设计原则

1. **Skill 即产品** — 每个技能是独立的、可配置的产品单元，通过 YAML 声明式定义，支持热加载
2. **Agent 可插拔** — 通过 Adapter 抽象，LLM/规则/API 三种 Agent 类型自由组合
3. **PAAL 闭环驱动** — 每次执行都经历 Plan-Act-Assess-Learn，持续积累经验
4. **博弈出质量** — 独立 Evaluator 制衡 Generator，避免自我评估偏差
5. **上下文可重置** — 长程任务通过归档 + 重启保持输出质量
6. **分级授权** — 按操作风险等级设定审批流，高危操作沙箱预演
7. **RAG 增强** — 先检索再推理，用知识库约束 LLM 幻觉
8. **全链路可观测** — 每个请求从输入到输出全程 trace，支持回溯和成本分析

---

> 核心思路：以 Skill 为中心组织能力，以 Agent 为执行单元，以 PAAL 循环为驱动引擎，以 Harness 框架保障长程任务质量，以 RAG 知识库约束幻觉风险，以分级授权确保生产安全。用户既可以通过自然语言自由交互，也可以通过技能卡片一键触发标准化诊断流程。
