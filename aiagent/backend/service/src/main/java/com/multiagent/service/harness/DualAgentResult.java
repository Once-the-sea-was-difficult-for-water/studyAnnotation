package com.multiagent.service.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 双 Agent 博弈执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DualAgentResult {

    /** 是否通过评估 */
    private boolean passed;

    /** 是否因达到最大轮数而终止 */
    private boolean maxRoundsReached;

    /** Generator 最终输出 */
    private String output;

    /** Evaluator 最终评估内容 */
    private String evaluation;

    /** 实际执行轮数 */
    private int rounds;

    /**
     * 创建评估通过的结果。
     */
    public static DualAgentResult passed(String output, String evaluation, int rounds) {
        return DualAgentResult.builder()
                .passed(true)
                .maxRoundsReached(false)
                .output(output)
                .evaluation(evaluation)
                .rounds(rounds)
                .build();
    }

    /**
     * 创建达到最大轮数仍未通过的结果。
     */
    public static DualAgentResult maxRoundsReached(String lastOutput, int maxRounds) {
        return DualAgentResult.builder()
                .passed(false)
                .maxRoundsReached(true)
                .output(lastOutput)
                .rounds(maxRounds)
                .build();
    }
}
