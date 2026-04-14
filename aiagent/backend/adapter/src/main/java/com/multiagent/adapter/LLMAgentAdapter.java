package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentType;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import com.multiagent.infrastructure.model.UsageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM Agent 适配器 — 集成 Spring AI ChatModel 调用 OpenAI/Claude/Qwen。
 * <p>
 * 支持同步调用（invoke）和流式调用（stream），通过 cancel 方法设置取消标志终止任务。
 */
public class LLMAgentAdapter extends AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(LLMAgentAdapter.class);

    private final String agentId;
    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    public LLMAgentAdapter(String agentId, ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.agentId = agentId;
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.LLM;
    }

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        Prompt prompt = (Prompt) toNativeFormat(request.getContext());
        ChatResponse response = chatClient.call(prompt);
        return fromNativeFormat(response);
    }

    @Override
    public Flux<UnifiedChunk> stream(UnifiedRequest request) {
        Prompt prompt = (Prompt) toNativeFormat(request.getContext());
        String taskId = request.getContext() != null ? request.getContext().getSessionId() : "default";
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancellationFlags.put(taskId, cancelled);

        return streamingChatClient.stream(prompt)
                .takeWhile(chunk -> !cancelled.get())
                .map(chatResponse -> {
                    String content = "";
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        content = chatResponse.getResult().getOutput().getContent();
                    }
                    return UnifiedChunk.builder()
                            .type("content")
                            .content(content != null ? content : "")
                            .build();
                })
                .concatWith(Flux.just(UnifiedChunk.builder().type("done").build()))
                .doFinally(signal -> cancellationFlags.remove(taskId));
    }

    @Override
    public void cancel(String taskId) {
        AtomicBoolean flag = cancellationFlags.get(taskId);
        if (flag != null) {
            flag.set(true);
            log.info("LLMAgentAdapter [{}]: cancelled task {}", agentId, taskId);
        }
    }

    @Override
    protected Object toNativeFormat(SharedContext context) {
        List<Message> messages = new ArrayList<>();
        if (context != null) {
            if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
                messages.add(new SystemMessage(context.getSystemPrompt()));
            }
            if (context.getMessages() != null) {
                for (com.multiagent.infrastructure.entity.Message msg : context.getMessages()) {
                    switch (msg.getRole()) {
                        case USER -> messages.add(new UserMessage(msg.getContent()));
                        case ASSISTANT -> messages.add(new AssistantMessage(msg.getContent()));
                        case SYSTEM -> messages.add(new SystemMessage(msg.getContent()));
                        default -> messages.add(new UserMessage(msg.getContent()));
                    }
                }
            }
        }
        if (messages.isEmpty()) {
            messages.add(new UserMessage(""));
        }
        return new Prompt(messages);
    }

    @Override
    protected UnifiedResponse fromNativeFormat(Object nativeResponse) {
        if (nativeResponse instanceof ChatResponse chatResponse) {
            String content = "";
            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                content = chatResponse.getResult().getOutput().getContent();
            }
            UsageInfo usage = null;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var u = chatResponse.getMetadata().getUsage();
                usage = UsageInfo.builder()
                        .inputTokens(u.getPromptTokens().intValue())
                        .outputTokens(u.getGenerationTokens().intValue())
                        .totalTokens(u.getTotalTokens().intValue())
                        .build();
            }
            return UnifiedResponse.builder()
                    .content(content != null ? content : "")
                    .usage(usage)
                    .build();
        }
        return UnifiedResponse.builder().content("").build();
    }
}
