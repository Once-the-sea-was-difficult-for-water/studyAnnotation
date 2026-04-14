package com.multiagent.service.paal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PAAL 循环输入 — 包含任务描述和期望结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PAALInput {

    /** 任务描述，如 "CPU 使用率持续 > 90%，需要定位根因" */
    private String description;

    /** 期望结果，如 "找到根因并给出修复建议" */
    private String expectation;
}
