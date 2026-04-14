package com.multiagent.service.fallback;

import com.multiagent.adapter.AgentAdapter;
import com.multiagent.adapter.AgentRegistry;
import com.multiagent.infrastructure.model.AgentManifest;
import com.multiagent.infrastructure.model.RoutingDecision;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 默认容错执行器 — 按降级链 [primary, fallback1, fallback2, ..., generic] 顺序尝试。
 * <p>
 * 每个 Agent 调用通过 {@link CompletableFuture#orTimeout} 控制超时，
 * 结果经过 {@link QualityGate} 检查，不达标则切换到下一个 Agent。
 * 所有 Agent 失败时返回兜底回复。
 */
@Service
public class DefaultFallbackExecutor implements FallbackExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultFallbackExecutor.class);

    static final String FALLBACK_MESSAGE = "抱歉，当前服务繁忙，请稍后重试或联系管理员。";
    private static final String GENERIC_AGENT_ID = "generic-agent";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private final AgentRegistry agentRegistry;
    private final QualityGate qualityGate;

    /**
     * AgentManifest 缓存，用于查找 fallbackAgentId 和 timeoutMs。
     * 实际项目中应由配置中心提供，此处简化为内存 Map。
     */
    private final Map<String, AgentManifest> manifestCache = new ConcurrentHashMap<>();

    public DefaultFallbackExecutor(AgentRegistry agentRegistry, QualityGate qualityGate) {
        this.agentRegistry = agentRegistry;
        this.qualityGate = qualityGate;
    }

    /**
     * 注册 AgentManifest（供外部配置加载时调用）。
     */
    public void registerManifest(AgentManifest manifest) {
        if (manifest != null && manifest.getId() != null) {
            manifestCache.put(manifest.getId(), manifest);
        }
    }

    @Override
    public UnifiedResponse execute(RoutingDecision routing, SharedContext context) {
        List<String> chain = buildFallbackChain(routing.getAgentId());

        for (String agentId : chain) {
            AgentAdapter adapter = agentRegistry.getById(agentId);
            if (adapter == null) {
                log.warn("Agent '{}' not found in registry, skipping", agentId);
                continue;
            }

            try {
                UnifiedResponse response = invokeWithTimeout(adapter, context, getTimeoutMs(agentId));

                if (qualityGate.check(response)) {
                    return response;
                }
                log.warn("Agent '{}' response did not pass quality gate, trying next in chain", agentId);
            } catch (TimeoutException e) {
                log.warn("Agent '{}' timed out, trying next in chain", agentId);
            } catch (Exception e) {
                log.warn("Agent '{}' failed: {}, trying next in chain", agentId, e.getMessage());
            }
        }

        log.warn("All agents in fallback chain failed, returning fallback message");
        return UnifiedResponse.builder().content(FALLBACK_MESSAGE).build();
    }

    /**
     * 构建降级链：[primary, fallback1, fallback2, ..., generic]。
     * 通过 AgentManifest.fallbackAgentId 链式追踪，最后确保包含 generic Agent。
     */
    List<String> buildFallbackChain(String primaryAgentId) {
        List<String> chain = new ArrayList<>();
        String currentId = primaryAgentId;

        while (currentId != null && !chain.contains(currentId)) {
            chain.add(currentId);
            AgentManifest manifest = manifestCache.get(currentId);
            currentId = (manifest != null) ? manifest.getFallbackAgentId() : null;
        }

        // 确保降级链至少包含一个 generic Agent
        if (!chain.contains(GENERIC_AGENT_ID)) {
            chain.add(GENERIC_AGENT_ID);
        }

        return chain;
    }

    /**
     * 带超时控制的 Agent 调用。
     */
    private UnifiedResponse invokeWithTimeout(AgentAdapter adapter, SharedContext context, int timeoutMs)
            throws Exception {
        UnifiedRequest request = UnifiedRequest.builder()
                .input(context.getSystemPrompt())
                .context(context)
                .build();

        return CompletableFuture.supplyAsync(() -> adapter.invoke(request))
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .join();
    }

    private int getTimeoutMs(String agentId) {
        AgentManifest manifest = manifestCache.get(agentId);
        if (manifest != null && manifest.getConfig() != null && manifest.getConfig().getTimeoutMs() > 0) {
            return manifest.getConfig().getTimeoutMs();
        }
        return DEFAULT_TIMEOUT_MS;
    }
}
