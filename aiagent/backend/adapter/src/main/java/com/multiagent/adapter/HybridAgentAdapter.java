package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentType;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Hybrid Agent 适配器 — 组合多种 AgentAdapter 能力的混合型 Agent。
 * <p>
 * invoke() 按委托列表顺序尝试每个 adapter，返回第一个成功的结果。
 * stream() 委托给第一个可用的 adapter。
 */
public class HybridAgentAdapter extends AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(HybridAgentAdapter.class);

    private final String agentId;
    private final List<AgentAdapter> delegates;

    public HybridAgentAdapter(String agentId, List<AgentAdapter> delegates) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("HybridAgentAdapter requires at least one delegate adapter");
        }
        this.agentId = agentId;
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.HYBRID;
    }

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        for (AgentAdapter delegate : delegates) {
            try {
                UnifiedResponse response = delegate.invoke(request);
                if (response != null && response.getContent() != null && !response.getContent().isBlank()) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("HybridAgentAdapter [{}]: delegate {} failed, trying next. Error: {}",
                        agentId, delegate.getAgentId(), e.getMessage());
            }
        }
        return UnifiedResponse.builder()
                .content("All delegate adapters failed")
                .build();
    }

    @Override
    public Flux<UnifiedChunk> stream(UnifiedRequest request) {
        // Delegate to the first available adapter
        for (AgentAdapter delegate : delegates) {
            try {
                return delegate.stream(request);
            } catch (Exception e) {
                log.warn("HybridAgentAdapter [{}]: delegate {} stream failed, trying next. Error: {}",
                        agentId, delegate.getAgentId(), e.getMessage());
            }
        }
        return Flux.just(
                UnifiedChunk.builder().type("error").content("All delegate adapters failed").build(),
                UnifiedChunk.builder().type("done").build()
        );
    }

    @Override
    public void cancel(String taskId) {
        for (AgentAdapter delegate : delegates) {
            try {
                delegate.cancel(taskId);
            } catch (Exception e) {
                log.warn("HybridAgentAdapter [{}]: cancel failed for delegate {}",
                        agentId, delegate.getAgentId(), e);
            }
        }
    }

    @Override
    protected Object toNativeFormat(SharedContext context) {
        // Hybrid adapter delegates format conversion to individual adapters
        return context;
    }

    @Override
    protected UnifiedResponse fromNativeFormat(Object nativeResponse) {
        if (nativeResponse instanceof UnifiedResponse response) {
            return response;
        }
        return UnifiedResponse.builder().content("").build();
    }
}
