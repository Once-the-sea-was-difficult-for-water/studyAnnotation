package com.multiagent.service.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Skill 执行结果 — 包含所有步骤的输出和执行状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResult {

    /** 所有步骤的输出结果，key 为 outputKey */
    private Map<String, Object> results;

    /** 执行是否成功 */
    private boolean success;

    /** 失败时的错误信息 */
    private String errorMessage;

    public static SkillResult success(Map<String, Object> results) {
        return SkillResult.builder()
                .results(results)
                .success(true)
                .build();
    }

    public static SkillResult failure(String errorMessage, Map<String, Object> results) {
        return SkillResult.builder()
                .results(results)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
