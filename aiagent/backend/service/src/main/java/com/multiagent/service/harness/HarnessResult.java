package com.multiagent.service.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 三代理协作执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessResult {

    /** 各 Sprint 的产出列表 */
    private List<String> results;

    /** 完成的 Sprint 数量 */
    private int sprintCount;

    /** 是否全部 Sprint 成功 */
    private boolean success;

    /**
     * 创建成功结果。
     */
    public static HarnessResult success(List<String> results, int sprintCount) {
        return HarnessResult.builder()
                .results(results)
                .sprintCount(sprintCount)
                .success(true)
                .build();
    }

    /**
     * 创建失败结果。
     */
    public static HarnessResult failed(List<String> results, int sprintCount) {
        return HarnessResult.builder()
                .results(results)
                .sprintCount(sprintCount)
                .success(false)
                .build();
    }
}
