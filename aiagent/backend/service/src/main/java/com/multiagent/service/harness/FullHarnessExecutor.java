package com.multiagent.service.harness;

import com.multiagent.infrastructure.model.SharedContext;

/**
 * 三代理协作执行器接口 — Planner 生成规格书 → Generator+Evaluator 协商 Sprint 契约 → 逐 Sprint 迭代。
 * <p>
 * 集成上下文重置：token 超 100K 阈值时归档当前上下文并创建包含摘要的干净新上下文。
 */
public interface FullHarnessExecutor {

    /**
     * 执行三代理协作流程。
     *
     * @param requirement 原始需求描述
     * @param context     共享上下文
     * @return 协作执行结果
     */
    HarnessResult execute(String requirement, SharedContext context);
}
