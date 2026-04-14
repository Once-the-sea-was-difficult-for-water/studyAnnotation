package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能定义 — 以 YAML 声明式配置定义的可执行能力单元。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    /** 技能 ID，kebab-case 格式，如 "disk-space-diagnose" */
    private String id;

    /** 技能名称，如 "磁盘空间分析" */
    private String name;

    /** 技能描述 */
    private String description;

    /** 图标 */
    private String icon;

    /** 技能分类 */
    private SkillCategory category;

    /** 绑定的 Agent ID */
    private String agentId;

    /** 输入参数列表 */
    private List<SkillParam> inputParams;

    /** 执行工作流 */
    private SkillWorkflow workflow;

    /** 展示配置 */
    private DisplayConfig display;

    /** 所需权限列表 */
    private List<String> permissions;

    /** 限流配置 */
    private RateLimitConfig rateLimit;
}
