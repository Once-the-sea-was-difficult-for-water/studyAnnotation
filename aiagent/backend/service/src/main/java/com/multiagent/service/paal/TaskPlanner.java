package com.multiagent.service.paal;

import com.multiagent.infrastructure.model.SharedContext;

/**
 * Plan 阶段 — 分解任务，制定执行计划（DAG）。
 */
public interface TaskPlanner {

    /**
     * 根据输入和上下文制定执行计划。
     *
     * @param input   PAAL 输入（任务描述 + 期望）
     * @param context 共享上下文（含历史案例、反馈等）
     * @return 执行计划
     */
    Object plan(PAALInput input, SharedContext context);
}
