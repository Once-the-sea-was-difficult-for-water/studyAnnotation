package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentType;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * API Agent 适配器 — 使用 Spring WebClient 调用外部 API 并格式化结果为 UnifiedResponse。
 * <p>
 * invoke() 执行阻塞式 HTTP 调用，stream() 执行响应式 HTTP 调用。
 */
public class APIAgentAdapter extends AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(APIAgentAdapter.class);

    private final String agentId;
    private final String baseUrl;
    private final WebClient webClient;
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    public APIAgentAdapter(String agentId, String baseUrl, WebClient webClient) {
        this.agentId = agentId;
        this.baseUrl = baseUrl;
        this.webClient = webClient;
    }

    public APIAgentAdapter(String agentId, String baseUrl) {
        this(agentId, baseUrl, WebClient.builder().baseUrl(baseUrl).build());
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.API;
    }

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nativeReq = (Map<String, Object>) toNativeFormat(request.getContext());
        String body = webClient.post()
                .uri("/invoke")
                .bodyValue(nativeReq)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return fromNativeFormat(body);
    }

    @Override
    public Flux<UnifiedChunk> stream(UnifiedRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nativeReq = (Map<String, Object>) toNativeFormat(request.getContext());
        String taskId = request.getContext() != null ? request.getContext().getSessionId() : "default";
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancellationFlags.put(taskId, cancelled);

        return webClient.post()
                .uri("/stream")
                .bodyValue(nativeReq)
                .retrieve()
                .bodyToFlux(String.class)
                .takeWhile(chunk -> !cancelled.get())
                .map(chunk -> UnifiedChunk.builder().type("content").content(chunk).build())
                .concatWith(Flux.just(UnifiedChunk.builder().type("done").build()))
                .doFinally(signal -> cancellationFlags.remove(taskId));
    }

    @Override
    public void cancel(String taskId) {
        AtomicBoolean flag = cancellationFlags.get(taskId);
        if (flag != null) {
            flag.set(true);
            log.info("APIAgentAdapter [{}]: cancelled task {}", agentId, taskId);
        }
    }

    @Override
    protected Object toNativeFormat(SharedContext context) {
        Map<String, Object> payload = new java.util.HashMap<>();
        if (context != null) {
            payload.put("sessionId", context.getSessionId());
            payload.put("systemPrompt", context.getSystemPrompt());
            payload.put("params", context.getCurrentParams());
        }
        return payload;
    }

    @Override
    protected UnifiedResponse fromNativeFormat(Object nativeResponse) {
        if (nativeResponse instanceof String content) {
            return UnifiedResponse.builder().content(content).build();
        }
        return UnifiedResponse.builder().content("").build();
    }
}
