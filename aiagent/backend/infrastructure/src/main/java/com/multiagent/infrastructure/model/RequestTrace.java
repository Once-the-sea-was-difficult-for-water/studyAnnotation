package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 请求追踪 — 记录单次请求的全链路追踪信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestTrace {

    /** 追踪 ID */
    private String traceId;

    /** 用户 ID */
    private String userId;

    /** 对话 ID */
    private String conversationId;

    /** 用户输入 */
    private String input;

    /** 路由决策 */
    private RoutingDecision routing;

    /** Agent 调用追踪列表 */
    private List<AgentCallTrace> agentCalls;

    /** 工具调用追踪列表 */
    private List<ToolCallTrace> toolCalls;

    /** 总延迟（毫秒） */
    private long totalLatencyMs;

    /** 最终响应内容 */
    private String finalResponse;
}
