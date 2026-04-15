package com.multiagent.api.websocket;

import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.service.orchestration.OrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

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

    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload Map<String, Object> payload,
                                   SimpMessageHeaderAccessor headerAccessor,
                                   Principal principal) {
        log.info("收到 STOMP 消息, payload={}", payload);

        String input = toString(payload.get("content"));
        if (input == null || input.isBlank()) {
            input = toString(payload.get("input"));
        }
        String conversationId = toString(payload.get("conversationId"));
        String userId = principal != null ? principal.getName() : "anonymous";

        log.info("处理消息: userId={}, conversationId={}, input={}",
                userId, conversationId, truncate(input, 80));

        AtomicLong sequence = new AtomicLong(0);
        String destination = "/topic/chat/" + conversationId;

        final String finalInput = input;
        CompletableFuture.runAsync(() -> {
            try {
                orchestrationService.processRequest(finalInput, conversationId, userId,
                        chunk -> sendChunk(destination, chunk, sequence));
                sendChunk(destination,
                        UnifiedChunk.builder().type("done").content("").build(), sequence);
            } catch (Exception e) {
                log.error("编排处理异常: conversationId={}", conversationId, e);
                sendChunk(destination,
                        UnifiedChunk.builder()
                                .type("error")
                                .content("处理失败: " + e.getMessage())
                                .build(),
                        sequence);
            }
        });
    }

    @MessageExceptionHandler
    public void handleException(Exception e) {
        log.error("STOMP 消息处理异常", e);
    }

    private void sendChunk(String destination, UnifiedChunk chunk, AtomicLong sequence) {
        Map<String, Object> wrapper = Map.of(
                "seq", sequence.getAndIncrement(),
                "type", chunk.getType() != null ? chunk.getType() : "content",
                "content", chunk.getContent() != null ? chunk.getContent() : "",
                "data", chunk.getData() != null ? chunk.getData() : Map.of()
        );
        messagingTemplate.convertAndSend(destination, wrapper);
    }

    private static String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
