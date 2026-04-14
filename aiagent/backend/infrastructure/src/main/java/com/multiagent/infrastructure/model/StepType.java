package com.multiagent.infrastructure.model;

/**
 * Skill Workflow 步骤类型枚举。
 */
public enum StepType {
    /** 工具调用 */
    TOOL_CALL,
    /** LLM 调用 */
    LLM_CALL,
    /** 条件分支 */
    CONDITION,
    /** 并行执行 */
    PARALLEL,
    /** 人工确认 */
    HUMAN_CONFIRM
}
