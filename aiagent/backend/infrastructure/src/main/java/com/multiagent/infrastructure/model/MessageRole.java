package com.multiagent.infrastructure.model;

/**
 * 消息角色枚举。
 */
public enum MessageRole {
    /** 用户消息 */
    USER,
    /** 助手回复 */
    ASSISTANT,
    /** 系统消息 */
    SYSTEM,
    /** 工具调用结果 */
    TOOL
}
