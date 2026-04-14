package com.multiagent.api.websocket;

import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.service.orchestration.OrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 消息处理器 — 接收用户消息并委托给 OrchestrationService 处理。
 * <p>
 * 客户端发送到 /app/chat.send，服务端通过 /queue/chat.stream 推送流式 chunk。
 * chunk 类型: content（内容）、progress（进度）、done（完成）、error（错误）。
 * 使用 AtomicLong 序列号确保推送顺序与服务端生成顺序一致。
 */
@Controller
public class ChatMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageHandler.class);

    private final OrchestrationService orchestrationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageHandler(OrchestrationService orchestrationService,
                              SimpMessagingTemplate messagingTemplate) {
        this.orchestrationService = orchestrationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 接收用户聊天消息，异步处理并流式推送结果。
     *
     * @param payload 消息体，包含 input（用户输入）和 conversationId
     * @param headerAccessor STOMP 消息头
     * @param principal 当前认证用户
     */
    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload Map<String, String> payload,
                                   SimpMessageHeaderAccessor headerAccessor,
                                   Principal principal) {
        String input = payload.get("input");
        String conversationId = payload.get("conversationId");
        String userId = principal != null ? principal.getName() : "anonymous";
        String sessionId = headerAccessor.getSessionId();

        log.info("收到 WebSocket 消息: userId={}, conversationId={}, input={}",
                userId, conversationId, truncate(input, 80));

        // 用于保证推送顺序的序列号
        AtomicLong sequence = new AtomicLong(0);
        String destination = "/queue/chat.stream";

        // 异步执行编排流程，逐 chunk 推送
        CompletableFuture.runAsync(() -> {
            try {
                orchestrationService.processRequest(input, conversationId, userId,
                        chunk -> sendChunk(userId, destination, chunk, sequence));
                // 发送 done chunk
                sendChunk(userId, destination,
                        UnifiedChunk.builder().type("done").content("").build(), sequence);
            } catch (Exception e) {
                log.error("编排处理异常: userId={}, conversationId={}", userId, conversationId, e);
                sendChunk(userId, destination,
                        UnifiedChunk.builder()
                                .type("error")
                                .content("处理失败: " + e.getMessage())
                                .build(),
                        sequence);
            }
        });
    }

    /**
     * 向指定用户推送带序列号的 chunk，确保顺序一致性。
     */
    private void sendChunk(String userId, String destination, UnifiedChunk chunk, AtomicLong sequence) {
        Map<String, Object> wrapper = Map.of(
                "seq", sequence.getAndIncrement(),
                "type", chunk.getType() != null ? chunk.getType() : "content",
                "content", chunk.getContent() != null ? chunk.getContent() : "",
                "data", chunk.getData() != null ? chunk.getData() : Map.of()
        );
        messagingTemplate.convertAndSendToUser(userId, destination, wrapper);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
