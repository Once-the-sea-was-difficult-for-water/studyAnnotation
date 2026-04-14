package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentCapability;
import com.multiagent.infrastructure.model.AgentConfig;

import java.util.List;

/**
 * Agent 配置验证器 — 校验 AgentConfig 和 AgentCapability 的合法性。
 */
public final class AgentConfigValidator {

    private AgentConfigValidator() {
        // utility class
    }

    /**
     * 验证 AgentConfig 配置。
     *
     * @param config 待验证的配置
     * @throws IllegalArgumentException 若 config 为 null 或 timeoutMs <= 0
     */
    public static void validate(AgentConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AgentConfig must not be null");
        }
        if (config.getTimeoutMs() <= 0) {
            throw new IllegalArgumentException("AgentConfig.timeoutMs must be > 0, got: " + config.getTimeoutMs());
        }
    }

    /**
     * 验证 AgentCapability 列表中每个 capability 的 confidence 值在 [0.0, 1.0] 范围内。
     *
     * @param capabilities 待验证的能力列表
     * @throws IllegalArgumentException 若任一 confidence 不在 [0.0, 1.0] 范围内
     */
    public static void validateCapabilities(List<AgentCapability> capabilities) {
        if (capabilities == null) {
            return;
        }
        for (AgentCapability cap : capabilities) {
            double confidence = cap.getConfidence();
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException(
                        "AgentCapability.confidence must be in [0.0, 1.0], got: " + confidence
                                + " for domain: " + cap.getDomain());
            }
        }
    }
}
