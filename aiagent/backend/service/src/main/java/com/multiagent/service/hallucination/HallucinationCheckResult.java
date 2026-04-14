package com.multiagent.service.hallucination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 幻觉检测结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallucinationCheckResult {

    /**
     * 检测状态。
     */
    public enum Status {
        /** 输出与知识库无矛盾 */
        CLEAN,
        /** 输出与知识库已知事实矛盾 */
        FLAGGED
    }

    /** 检测状态 */
    private Status status;

    /** 矛盾证据列表（FLAGGED 时非空） */
    private List<String> evidence;

    /** Agent 原始输出 */
    private String originalOutput;

    /**
     * 创建 CLEAN 结果。
     */
    public static HallucinationCheckResult clean(String originalOutput) {
        return HallucinationCheckResult.builder()
                .status(Status.CLEAN)
                .evidence(List.of())
                .originalOutput(originalOutput)
                .build();
    }

    /**
     * 创建 FLAGGED 结果。
     */
    public static HallucinationCheckResult flagged(String originalOutput, List<String> evidence) {
        return HallucinationCheckResult.builder()
                .status(Status.FLAGGED)
                .evidence(evidence)
                .originalOutput(originalOutput)
                .build();
    }
}
