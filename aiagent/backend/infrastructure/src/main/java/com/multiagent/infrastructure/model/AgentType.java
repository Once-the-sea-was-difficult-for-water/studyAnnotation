package com.multiagent.infrastructure.model;

/**
 * 智能体类型枚举。
 */
public enum AgentType {
    /** 大模型 Agent */
    LLM,
    /** 规则引擎 Agent */
    RULE,
    /** API 调用 Agent */
    API,
    /** 混合型 Agent */
    HYBRID
}
