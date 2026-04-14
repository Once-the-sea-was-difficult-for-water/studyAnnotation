package com.multiagent.service.context;

import com.multiagent.infrastructure.entity.Conversation;
import com.multiagent.infrastructure.entity.Message;
import com.multiagent.infrastructure.model.*;
import com.multiagent.service.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 默认上下文管理器实现。
 * <ul>
 *   <li>buildContext：截取最近 N 轮对话（token 不超过 maxContextTokens），注入 system prompt，检索长期记忆</li>
 *   <li>persist：生成对话摘要并存入向量库</li>
 * </ul>
 */
@Service
public class DefaultContextManager implements ContextManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextManager.class);

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 4096;
    private static final int LONG_TERM_MEMORY_TOP_K = 5;

    private final SkillRegistry skillRegistry;
    private final VectorStoreClient vectorStoreClient;

    public DefaultContextManager(SkillRegistry skillRegistry, VectorStoreClient vectorStoreClient) {
        this.skillRegistry = skillRegistry;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public SharedContext buildContext(Conversation conversation, RoutingDecision routing, AgentManifest agent) {
        Objects.requireNonNull(conversation, "conversation must not be null");
        Objects.requireNonNull(routing, "routing must not be null");
        Objects.requireNonNull(agent, "agent must not be null");

        int maxTokens = resolveMaxContextTokens(agent);

        // 1. Determine system prompt
        String systemPrompt = resolveSystemPrompt(routing, agent);

        // 2. Truncate messages to fit within token budget
        List<Message> truncatedMessages = truncateMessages(conversation.getMessages(), maxTokens);

        // 3. Retrieve long-term memory from vector store
        List<MemoryFragment> longTermMemory = retrieveLongTermMemory(routing.getQuery());

        // 4. Build SharedContext
        return SharedContext.builder()
                .sessionId(conversation.getId())
                .systemPrompt(systemPrompt)
                .messages(truncatedMessages)
                .longTermMemory(longTermMemory)
                .currentParams(routing.getParams() != null ? routing.getParams() : Collections.emptyMap())
                .feedbacks(new ArrayList<>())
                .intermediateResults(new ArrayList<>())
                .build();
    }

    @Override
    public void persist(Conversation conversation, Object result) {
        Objects.requireNonNull(conversation, "conversation must not be null");

        String summary = generateSummary(conversation, result);
        if (summary != null && !summary.isBlank()) {
            try {
                vectorStoreClient.store(conversation.getId(), summary);
                log.info("Persisted conversation summary for session {}", conversation.getId());
            } catch (Exception e) {
                log.warn("Failed to persist conversation summary for session {}: {}",
                        conversation.getId(), e.getMessage());
            }
        }
    }

    // --- internal helpers ---

    /**
     * Resolve maxContextTokens from agent config, falling back to default.
     */
    int resolveMaxContextTokens(AgentManifest agent) {
        if (agent.getConfig() != null && agent.getConfig().getMaxContextTokens() > 0) {
            return agent.getConfig().getMaxContextTokens();
        }
        return DEFAULT_MAX_CONTEXT_TOKENS;
    }

    /**
     * If routing targets a Skill, use the Skill's description as system prompt;
     * otherwise use the Agent's default system prompt.
     */
    String resolveSystemPrompt(RoutingDecision routing, AgentManifest agent) {
        if (routing.getSkillId() != null && !routing.getSkillId().isBlank()) {
            Skill skill = skillRegistry.getById(routing.getSkillId());
            if (skill != null && skill.getDescription() != null && !skill.getDescription().isBlank()) {
                return skill.getDescription();
            }
        }
        // Fallback to agent default system prompt
        if (agent.getConfig() != null && agent.getConfig().getSystemPrompt() != null) {
            return agent.getConfig().getSystemPrompt();
        }
        return "";
    }

    /**
     * Truncate messages from the most recent, keeping total estimated tokens ≤ maxTokens.
     * Uses a simple character-based approximation: chars / 4 ≈ tokens.
     */
    List<Message> truncateMessages(List<Message> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> result = new ArrayList<>();
        int tokenBudget = maxTokens;

        // Iterate from most recent to oldest
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            int msgTokens = estimateTokens(msg.getContent());
            if (msgTokens > tokenBudget) {
                break;
            }
            result.add(msg);
            tokenBudget -= msgTokens;
        }

        // Reverse to restore chronological order
        Collections.reverse(result);
        return result;
    }

    /**
     * Estimate token count using character-based approximation (chars / 4).
     */
    int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    /**
     * Retrieve Top-5 related history fragments from vector store.
     */
    List<MemoryFragment> retrieveLongTermMemory(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return vectorStoreClient.search(query, LONG_TERM_MEMORY_TOP_K);
        } catch (Exception e) {
            log.warn("Failed to retrieve long-term memory: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generate a simple summary from conversation messages and result.
     */
    String generateSummary(Conversation conversation, Object result) {
        List<Message> messages = conversation.getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Session: ").append(conversation.getId()).append("\n");

        // Include last few messages as summary context
        int start = Math.max(0, messages.size() - 3);
        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            sb.append(msg.getRole()).append(": ").append(truncateForSummary(msg.getContent())).append("\n");
        }

        if (result != null) {
            sb.append("Result: ").append(truncateForSummary(result.toString()));
        }

        return sb.toString();
    }

    private String truncateForSummary(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
