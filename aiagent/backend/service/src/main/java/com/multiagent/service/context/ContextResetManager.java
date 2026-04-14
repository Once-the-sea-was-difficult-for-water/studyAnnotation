package com.multiagent.service.context;

import com.multiagent.infrastructure.entity.Message;
import com.multiagent.infrastructure.model.MessageRole;
import com.multiagent.infrastructure.model.SharedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 上下文重置管理器 — 当 token 超过阈值时归档当前上下文并创建包含摘要的干净新上下文。
 * <p>
 * 算法：
 * <ol>
 *   <li>估算当前上下文 token 数（所有 message content 长度之和 / 4）</li>
 *   <li>若未超过 TOKEN_THRESHOLD (100K)，直接返回当前上下文</li>
 *   <li>否则：生成归档摘要，创建新上下文（保留 sessionId 和 systemPrompt），注入摘要作为 system message</li>
 * </ol>
 */
@Component
public class ContextResetManager {

    private static final Logger log = LoggerFactory.getLogger(ContextResetManager.class);

    static final int TOKEN_THRESHOLD = 100_000;

    /**
     * 检查上下文 token 数，超过阈值时执行重置。
     *
     * @param current 当前共享上下文
     * @return 原上下文（未超阈值）或重置后的新上下文
     */
    public SharedContext checkAndResetIfNeeded(SharedContext current) {
        if (current == null) {
            return current;
        }

        int tokenCount = estimateTokenCount(current);
        if (tokenCount < TOKEN_THRESHOLD) {
            return current;
        }

        log.info("Context token count ({}) exceeds threshold ({}), resetting context for session {}",
                tokenCount, TOKEN_THRESHOLD, current.getSessionId());

        String summary = generateArchiveSummary(current);

        // Build fresh context preserving sessionId and systemPrompt
        Message summaryMessage = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(current.getSessionId())
                .role(MessageRole.SYSTEM)
                .content("你正在继续一个进行中的任务。以下是之前的进度摘要：\n" + summary + "\n\n请基于此继续工作。")
                .timestamp(LocalDateTime.now())
                .build();

        List<Message> freshMessages = new ArrayList<>();
        freshMessages.add(summaryMessage);

        return SharedContext.builder()
                .sessionId(current.getSessionId())
                .systemPrompt(current.getSystemPrompt())
                .messages(freshMessages)
                .longTermMemory(Collections.emptyList())
                .currentParams(current.getCurrentParams())
                .feedbacks(new ArrayList<>())
                .intermediateResults(new ArrayList<>())
                .build();
    }

    /**
     * 估算 SharedContext 中所有消息的 token 数。
     * 使用简单的字符数 / 4 近似。
     *
     * @param context 共享上下文
     * @return 估算的 token 数
     */
    public int estimateTokenCount(SharedContext context) {
        if (context == null || context.getMessages() == null || context.getMessages().isEmpty()) {
            return 0;
        }
        int totalChars = 0;
        for (Message msg : context.getMessages()) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
        return totalChars / 4;
    }

    /**
     * 从当前上下文生成归档摘要。
     * 取最后几条消息的内容作为摘要。
     */
    String generateArchiveSummary(SharedContext context) {
        List<Message> messages = context.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "无历史消息。";
        }

        StringBuilder sb = new StringBuilder();
        // Take last 5 messages as summary
        int start = Math.max(0, messages.size() - 5);
        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            sb.append(msg.getRole()).append(": ");
            String content = msg.getContent();
            if (content != null && content.length() > 300) {
                sb.append(content, 0, 300).append("...");
            } else {
                sb.append(content != null ? content : "");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
