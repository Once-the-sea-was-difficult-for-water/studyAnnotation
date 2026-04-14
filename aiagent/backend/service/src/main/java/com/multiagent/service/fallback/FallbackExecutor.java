package com.multiagent.service.fallback;

import com.multiagent.infrastructure.model.RoutingDecision;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedResponse;

/**
 * 容错执行器 — 构建 Agent 降级链，当主 Agent 失败时自动切换到备选 Agent。
 */
public interface FallbackExecutor {

    /**
     * 按降级链顺序执行 Agent，返回第一个通过 qualityGate 的结果。
     * 若所有 Agent 失败，返回兜底回复。
     *
     * @param routing 路由决策（包含主 Agent ID）
     * @param context 共享上下文
     * @return 统一响应
     */
    UnifiedResponse execute(RoutingDecision routing, SharedContext context);
}
