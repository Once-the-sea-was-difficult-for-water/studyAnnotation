package com.multiagent.service.harness;

import com.multiagent.infrastructure.model.SharedContext;

/**
 * 双 Agent 博弈执行器接口 — Generator 生成 → Evaluator 独立评估循环。
 * <p>
 * Evaluator 从正确性、完整性、质量三个维度评分（1-10），
 * 所有维度 ≥ 8 分时输出 PASS。未通过时将评估反馈注入下一轮 context。
 */
public interface DualAgentExecutor {

    /**
     * 执行双 Agent 博弈循环。
     *
     * @param task      原始任务描述
     * @param context   共享上下文
     * @param maxRounds 最大迭代轮数（必须 &gt; 0）
     * @return 博弈执行结果
     */
    DualAgentResult execute(String task, SharedContext context, int maxRounds);
}
