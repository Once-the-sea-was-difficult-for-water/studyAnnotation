package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 能力描述。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCapability {

    /** 能力领域，如 "database-diagnose" */
    private String domain;

    /** 该 Agent 支持的 Skill ID 列表 */
    private List<String> skills;

    /** 置信度 [0.0, 1.0] */
    private double confidence;
}
