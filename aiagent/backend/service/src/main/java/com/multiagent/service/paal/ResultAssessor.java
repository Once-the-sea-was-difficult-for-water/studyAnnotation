package com.multiagent.service.paal;

/**
 * Assess 阶段 — 独立评估执行效果，判断是否达标。
 */
public interface ResultAssessor {

    /**
     * 评估执行结果是否满足期望。
     *
     * @param output      Act 阶段的执行输出
     * @param expectation 期望结果描述
     * @return 评估结果，包含是否通过和反馈
     */
    Assessment assess(Object output, String expectation);
}
