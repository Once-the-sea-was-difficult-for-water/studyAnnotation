package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 注册信息 — 描述一个 Agent 的元数据和能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentManifest {

    /** Agent ID */
    private String id;

    /** 展示名，如 "RDS 诊断专家" */
    private String name;

    /** 头像 */
    private String avatar;

    /** Agent 类型 */
    private AgentType type;

    /** 平台标识，如 openai, claude, qwen, custom */
    private String platform;

    /** Agent 能力列表 */
    private List<AgentCapability> capabilities;

    /** Agent 配置 */
    private AgentConfig config;

    /** 降级 Agent ID */
    private String fallbackAgentId;
}
