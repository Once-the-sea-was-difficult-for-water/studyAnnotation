package com.multiagent.infrastructure.model;

/**
 * 路由模式枚举 — 意图识别器返回的路由决策类型。
 */
public enum RoutingMode {
    /** @agent-name 指定路由 */
    DIRECT_AGENT,
    /** /skill-name 触发 */
    SKILL,
    /** 自由对话智能路由 */
    AGENT
}
