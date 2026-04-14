package com.multiagent.service.orchestration;

import com.multiagent.adapter.AgentRegistry;
import com.multiagent.infrastructure.entity.Conversation;
import com.multiagent.infrastructure.model.*;
import com.multiagent.service.context.ContextManager;
import com.multiagent.service.fallback.FallbackExecutor;
import com.multiagent.service.intent.IntentRecognizer;
import com.multiagent.service.skill.MissingParameterException;
import com.multiagent.service.skill.SkillExecutor;
import com.multiagent.service.skill.SkillRegistry;
import com.multiagent.service.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 编排服务 — 请求处理主流程。
 * <p>
 * 算法: 意图识别 → 上下文构建 → 路由执行 → 持久化 → 流式返回。
 * <ul>
 *   <li>DIRECT_AGENT: 通过 FallbackExecutor 执行</li>
 *   <li>SKILL: 通过 SkillExecutor 执行（含参数校验）</li>
 *   <li>AGENT: 通过 FallbackExecutor 执行</li>
 * </ul>
 * 每次请求生成 {@link RequestTrace} 记录全链路追踪。
 */
@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final IntentRecognizer intentRecognizer;
    private final ContextManager contextManager;
    private final SkillRegistry skillRegistry;
    private final SkillExecutor skillExecutor;
    private final FallbackExecutor fallbackExecutor;
    private final AgentRegistry agentRegistry;

    public OrchestrationService(IntentRecognizer intentRecognizer,
                                ContextManager contextManager,
                                SkillRegistry skillRegistry,
                                SkillExecutor skillExecutor,
                                FallbackExecutor fallbackExecutor,
                                AgentRegistry agentRegistry) {
        this.intentRecognizer = intentRecognizer;
        this.contextManager = contextManager;
        this.skillRegistry = skillRegistry;
        this.skillExecutor = skillExecutor;
        this.fallbackExecutor = fallbackExecutor;
        this.agentRegistry = agentRegistry;
    }

    /**
     * 处理用户请求的主流程。
     *
     * @param input          用户输入文本
     * @param conversationId 对话 ID
     * @param userId         用户 ID
     * @param chunkConsumer  流式 chunk 回调（content/progress 类型）
     * @return 请求追踪记录
     */
    public RequestTrace processRequest(String input,
                                       String conversationId,
                                       String userId,
                                       Consumer<UnifiedChunk> chunkConsumer) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();

        log.info("[Trace:{}] 开始处理请求: userId={}, conversationId={}", traceId, userId, conversationId);

        // 构建会话上下文
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .build();
        ConversationContext conversationContext = ConversationContext.builder()
                .conversationId(conversationId)
                .conversation(conversation)
                .build();

        // Step 1: 意图识别与路由
        RoutingDecision routing = intentRecognizer.recognize(input, conversationContext);
        log.info("[Trace:{}] 路由决策: mode={}, agentId={}, skillId={}",
                traceId, routing.getMode(), routing.getAgentId(), routing.getSkillId());

        // Step 2: 获取 Agent 信息并构建上下文
        AgentManifest agent = resolveAgent(routing);
        SharedContext context = contextManager.buildContext(conversation, routing, agent);

        // Step 3: 根据路由模式执行
        UnifiedResponse response;
        try {
            response = executeByMode(routing, context, chunkConsumer);
        } catch (MissingParameterException e) {
            log.warn("[Trace:{}] 参数缺失: {}", traceId, e.getMessage());
            chunkConsumer.accept(UnifiedChunk.builder()
                    .type("error")
                    .content(e.getMessage())
                    .build());
            response = UnifiedResponse.builder().content(e.getMessage()).build();
        }

        // Step 4: 持久化对话
        try {
            contextManager.persist(conversation, response);
        } catch (Exception e) {
            log.warn("[Trace:{}] 持久化失败: {}", traceId, e.getMessage());
        }

        // Step 5: 推送最终内容 chunk
        if (response != null && response.getContent() != null) {
            chunkConsumer.accept(UnifiedChunk.builder()
                    .type("content")
                    .content(response.getContent())
                    .data(response.getData())
                    .build());
        }

        // 构建 RequestTrace
        long totalLatency = System.currentTimeMillis() - startTime;
        RequestTrace trace = RequestTrace.builder()
                .traceId(traceId)
                .userId(userId)
                .conversationId(conversationId)
                .input(input)
                .routing(routing)
                .agentCalls(new ArrayList<>())
                .toolCalls(new ArrayList<>())
                .totalLatencyMs(totalLatency)
                .finalResponse(response != null ? response.getContent() : null)
                .build();

        log.info("[Trace:{}] 请求处理完成, 耗时 {}ms", traceId, totalLatency);
        return trace;
    }

    /**
     * 根据路由模式执行对应逻辑。
     */
    private UnifiedResponse executeByMode(RoutingDecision routing,
                                          SharedContext context,
                                          Consumer<UnifiedChunk> chunkConsumer) {
        return switch (routing.getMode()) {
            case DIRECT_AGENT -> {
                log.debug("DIRECT_AGENT 模式: agentId={}", routing.getAgentId());
                yield fallbackExecutor.execute(routing, context);
            }
            case SKILL -> {
                log.debug("SKILL 模式: skillId={}", routing.getSkillId());
                Skill skill = skillRegistry.getById(routing.getSkillId());
                if (skill == null) {
                    throw new IllegalArgumentException("Skill 不存在: " + routing.getSkillId());
                }
                // 验证必填参数
                validateRequiredParams(skill, routing.getParams());
                // 推送进度 chunk
                chunkConsumer.accept(UnifiedChunk.builder()
                        .type("progress")
                        .content("开始执行技能: " + skill.getName())
                        .build());
                SkillResult skillResult = skillExecutor.execute(skill, routing.getParams(), context);
                yield UnifiedResponse.builder()
                        .content(buildSkillResponseContent(skillResult))
                        .data(skillResult.getResults())
                        .build();
            }
            case AGENT -> {
                log.debug("AGENT 模式: agentId={}", routing.getAgentId());
                yield fallbackExecutor.execute(routing, context);
            }
        };
    }

    /**
     * 解析路由决策中的 Agent 信息。
     */
    private AgentManifest resolveAgent(RoutingDecision routing) {
        String agentId = routing.getAgentId();
        if (agentId == null && routing.getSkillId() != null) {
            Skill skill = skillRegistry.getById(routing.getSkillId());
            if (skill != null) {
                agentId = skill.getAgentId();
            }
        }
        if (agentId == null) {
            agentId = "default-agent";
        }
        // 返回 manifest（如果 AgentRegistry 中有对应适配器）
        return AgentManifest.builder().id(agentId).build();
    }

    /**
     * 校验 Skill 必填参数。
     */
    private void validateRequiredParams(Skill skill, Map<String, Object> params) {
        if (skill.getInputParams() == null) return;
        for (SkillParam param : skill.getInputParams()) {
            if (param.isRequired() && (params == null || !params.containsKey(param.getName()))) {
                throw new MissingParameterException(skill.getId(), param.getName());
            }
        }
    }

    /**
     * 将 SkillResult 转为可读的响应内容。
     */
    private String buildSkillResponseContent(SkillResult result) {
        if (!result.isSuccess()) {
            return "技能执行失败: " + result.getErrorMessage();
        }
        StringBuilder sb = new StringBuilder();
        if (result.getResults() != null) {
            result.getResults().forEach((key, value) ->
                    sb.append(key).append(": ").append(value).append("\n"));
        }
        return sb.length() > 0 ? sb.toString().trim() : "技能执行完成";
    }
}
