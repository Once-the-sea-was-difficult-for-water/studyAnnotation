package com.multiagent.service.paal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PAAL 循环执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PAALResult {

    /** 是否成功 */
    private boolean success;

    /** 执行输出 */
    private Object output;

    /** 评估结果 */
    private Assessment assessment;

    /** 实际迭代次数 */
    private int iterationCount;

    /** 失败原因（仅失败时有值） */
    private String failureReason;

    public static PAALResult success(Object output, Assessment assessment, int iterationCount) {
        return PAALResult.builder()
                .success(true)
                .output(output)
                .assessment(assessment)
                .iterationCount(iterationCount)
                .build();
    }

    public static PAALResult failed(String reason, int iterationCount) {
        return PAALResult.builder()
                .success(false)
                .failureReason(reason)
                .iterationCount(iterationCount)
                .build();
    }
}
