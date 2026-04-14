package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentCapability;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心 — 管理已注册的 {@link AgentAdapter} 实例。
 * <p>
 * 启动时通过 Java SPI ({@link ServiceLoader}) 自动发现并注册适配器，
 * 也支持运行时手动注册。提供按 id 查找和按 capability domain 匹配。
 */
@Service
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final ConcurrentHashMap<String, AgentAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 启动时通过 SPI 加载所有 AgentAdapter 实现。
     */
    @PostConstruct
    void loadFromSpi() {
        ServiceLoader<AgentAdapter> loader = ServiceLoader.load(AgentAdapter.class);
        for (AgentAdapter adapter : loader) {
            register(adapter);
        }
        log.info("AgentRegistry initialized with {} adapter(s) via SPI", adapters.size());
    }

    /**
     * 注册一个 AgentAdapter。若 id 已存在则覆盖并打印警告。
     */
    public void register(AgentAdapter adapter) {
        String id = adapter.getAgentId();
        AgentAdapter prev = adapters.put(id, adapter);
        if (prev != null) {
            log.warn("AgentAdapter with id '{}' was replaced", id);
        }
    }

    /**
     * 按 id 查找 AgentAdapter，未找到返回 {@code null}。
     */
    public AgentAdapter getById(String agentId) {
        return adapters.get(agentId);
    }

    /**
     * 按 capability domain 匹配所有适配器。
     * <p>
     * 遍历已注册适配器，检查其 manifest 中是否声明了匹配的 domain。
     * 由于 AgentAdapter 本身不持有 manifest，此方法依赖子类重写
     * {@link #getCapabilities(AgentAdapter)} 提供能力列表。
     * 默认实现返回空列表（即不匹配任何 domain），子类可按需扩展。
     */
    public List<AgentAdapter> matchByCapability(String domain) {
        List<AgentAdapter> matched = new ArrayList<>();
        for (AgentAdapter adapter : adapters.values()) {
            for (AgentCapability cap : getCapabilities(adapter)) {
                if (domain.equals(cap.getDomain())) {
                    matched.add(adapter);
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * 返回所有已注册的 AgentAdapter。
     */
    public Collection<AgentAdapter> getAll() {
        return adapters.values();
    }

    // ------------------------------------------------------------------
    // Extension point: override in subclass or provide a richer adapter
    // ------------------------------------------------------------------

    /**
     * 获取适配器声明的能力列表。默认返回空列表。
     * 具体适配器实现可通过 AgentManifest 提供能力信息。
     */
    protected List<AgentCapability> getCapabilities(AgentAdapter adapter) {
        return List.of();
    }
}
