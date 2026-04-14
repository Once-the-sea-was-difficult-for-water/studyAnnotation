package com.multiagent.service.fallback;

import com.multiagent.infrastructure.model.UnifiedResponse;

/**
 * 质量门禁 — 检查 Agent 返回结果是否达标。
 */
public interface QualityGate {

    /**
     * 检查响应质量是否达标。
     *
     * @param response Agent 返回的统一响应
     * @return true 表示达标，false 表示不达标需降级
     */
    boolean check(UnifiedResponse response);
}
