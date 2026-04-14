package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用追踪 — 记录单次工具调用的性能信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallTrace {

    /** 工具名称 */
    private String toolName;

    /** 调用延迟（毫秒） */
    private long latencyMs;

    /** 是否成功 */
    private boolean success;
}
