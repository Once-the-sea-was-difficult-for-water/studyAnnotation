package com.multiagent.service.paal;

import com.multiagent.infrastructure.model.SharedContext;

/**
 * PAAL 循环引擎接口 — 实现 Plan-Act-Assess-Learn 闭环。
 * <p>
 * 每次 Skill 执行或 Agent 对话都可通过此引擎驱动，
 * 迭代优化直到 Assess 通过或达到最大迭代次数。
 */
public interface PAALEngine {

    /**
     * 执行 PAAL 循环。
     *
     * @param input         任务输入（描述 + 期望）
     * @param context       共享上下文
     * @param maxIterations 最大迭代次数（必须 > 0）
     * @return 循环执行结果
     */
    PAALResult run(PAALInput input, SharedContext context, int maxIterations);
}
