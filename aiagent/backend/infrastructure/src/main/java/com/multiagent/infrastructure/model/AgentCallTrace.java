package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Agent 调用追踪 — 记录单次 Agent 调用的性能和结果信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCallTrace {

    /** Agent ID */
    private String agentId;

    /** 调用延迟（毫秒） */
    private long latencyMs;

    /** 输入 token 数 */
    private int inputTokens;

    /** 输出 token 数 */
    private int outputTokens;

    /** 调用费用 */
    private BigDecimal cost;

    /** 是否成功 */
    private boolean success;

    /** 错误信息（失败时） */
    private String error;
}
