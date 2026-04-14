package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentType;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import reactor.core.publisher.Flux;

/**
 * Agent 适配器抽象类 — 通过 Java SPI 机制实现 Agent 可插拔。
 * <p>
 * 子类需实现具体的调用协议（LLM / Rule / API / Hybrid），
 * 并通过 {@code META-INF/services/com.multiagent.adapter.AgentAdapter} 注册。
 */
public abstract class AgentAdapter {

    /**
     * 返回此 Agent 的唯一标识。
     */
    public abstract String getAgentId();

    /**
     * 返回此 Agent 的类型。
     */
    public abstract AgentType getAgentType();

    /**
     * 同步调用 Agent。
     */
    public abstract UnifiedResponse invoke(UnifiedRequest request);

    /**
     * 流式调用 Agent，返回响应块流。
     */
    public abstract Flux<UnifiedChunk> stream(UnifiedRequest request);

    /**
     * 取消正在执行的任务。
     */
    public abstract void cancel(String taskId);

    /**
     * 将共享上下文转换为 Agent 原生请求格式。
     */
    protected abstract Object toNativeFormat(SharedContext context);

    /**
     * 将 Agent 原生响应转换为统一响应。
     */
    protected abstract UnifiedResponse fromNativeFormat(Object nativeResponse);
}
