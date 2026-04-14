package com.multiagent.service.sse;

import com.multiagent.infrastructure.model.UnifiedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 进度推送管理器 — 管理每个会话的 SSE 事件流。
 * <p>
 * 使用 Reactor {@link Sinks.Many} 为每个 sessionId 维护一个事件流，
 * 支持步骤进度推送、HUMAN_CONFIRM 确认请求推送和通用 chunk 推送。
 * <p>
 * API 层可通过 {@link #subscribe(String)} 获取 {@link Flux} 并转为 SSE 端点。
 */
@Service
public class SSEManager {

    private static final Logger log = LoggerFactory.getLogger(SSEManager.class);

    /** 每个 session 对应一个 Sink，用于推送事件 */
    private final ConcurrentHashMap<String, Sinks.Many<UnifiedChunk>> emitters = new ConcurrentHashMap<>();

    /**
     * 注册一个新的 SSE 事件流。如果 sessionId 已存在，先移除旧的。
     *
     * @param sessionId 会话 ID
     * @return 可订阅的事件流
     */
    public Flux<UnifiedChunk> register(String sessionId) {
        Sinks.Many<UnifiedChunk> sink = Sinks.many().multicast().onBackpressureBuffer();
        Sinks.Many<UnifiedChunk> previous = emitters.put(sessionId, sink);
        if (previous != null) {
            previous.tryEmitComplete();
            log.debug("替换已有 SSE emitter: sessionId={}", sessionId);
        }
        log.debug("注册 SSE emitter: sessionId={}", sessionId);
        return sink.asFlux();
    }

    /**
     * 获取指定 session 的事件流（用于 API 层订阅）。
     * 如果 session 尚未注册，自动注册一个新的。
     *
     * @param sessionId 会话 ID
     * @return 可订阅的事件流
     */
    public Flux<UnifiedChunk> subscribe(String sessionId) {
        Sinks.Many<UnifiedChunk> sink = emitters.get(sessionId);
        if (sink == null) {
            return register(sessionId);
        }
        return sink.asFlux();
    }

    /**
     * 移除指定 session 的 SSE emitter 并完成事件流。
     *
     * @param sessionId 会话 ID
     */
    public void remove(String sessionId) {
        Sinks.Many<UnifiedChunk> sink = emitters.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.debug("移除 SSE emitter: sessionId={}", sessionId);
        }
    }

    /**
     * 推送步骤执行进度事件。
     *
     * @param sessionId 会话 ID
     * @param stepId    步骤 ID
     * @param result    步骤执行结果
     * @param status    步骤状态（如 "running", "completed", "failed"）
     */
    public void emitProgress(String sessionId, String stepId, Object result, String status) {
        Map<String, Object> data = Map.of(
                "stepId", stepId,
                "result", result != null ? result : "",
                "status", status
        );
        UnifiedChunk chunk = UnifiedChunk.builder()
                .type("progress")
                .content(stepId + ": " + status)
                .data(data)
                .build();
        emit(sessionId, chunk);
    }

    /**
     * 推送 HUMAN_CONFIRM 确认请求事件。
     *
     * @param sessionId 会话 ID
     * @param stepId    步骤 ID
     * @param message   确认提示消息
     */
    public void emitConfirmRequest(String sessionId, String stepId, String message) {
        Map<String, Object> data = Map.of(
                "stepId", stepId,
                "message", message != null ? message : "",
                "requiresConfirm", true
        );
        UnifiedChunk chunk = UnifiedChunk.builder()
                .type("progress")
                .content(message)
                .data(data)
                .build();
        emit(sessionId, chunk);
    }

    /**
     * 推送通用 UnifiedChunk 事件。
     *
     * @param sessionId 会话 ID
     * @param chunk     要推送的 chunk
     */
    public void emitChunk(String sessionId, UnifiedChunk chunk) {
        emit(sessionId, chunk);
    }

    /**
     * 检查指定 session 是否有活跃的 emitter。
     */
    public boolean hasEmitter(String sessionId) {
        return emitters.containsKey(sessionId);
    }

    /**
     * 获取当前活跃的 emitter 数量（用于监控）。
     */
    public int activeEmitterCount() {
        return emitters.size();
    }

    /**
     * 内部方法：向指定 session 的 sink 推送事件。
     */
    private void emit(String sessionId, UnifiedChunk chunk) {
        Sinks.Many<UnifiedChunk> sink = emitters.get(sessionId);
        if (sink == null) {
            log.warn("SSE emitter 不存在: sessionId={}, 事件被丢弃", sessionId);
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(chunk);
        if (result.isFailure()) {
            log.warn("SSE 推送失败: sessionId={}, result={}", sessionId, result);
            if (result == Sinks.EmitResult.FAIL_TERMINATED || result == Sinks.EmitResult.FAIL_CANCELLED) {
                emitters.remove(sessionId);
            }
        }
    }
}
