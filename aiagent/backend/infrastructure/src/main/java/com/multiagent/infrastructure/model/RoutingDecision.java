package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 路由决策 — 意图识别器的输出结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingDecision {

    /** 路由模式 */
    private RoutingMode mode;

    /** 目标 Agent ID（DIRECT_AGENT / AGENT 模式） */
    private String agentId;

    /** 目标 Skill ID（SKILL 模式） */
    private String skillId;

    /** 用户查询内容 */
    private String query;

    /** 附加参数 */
    private Map<String, Object> params;
}
