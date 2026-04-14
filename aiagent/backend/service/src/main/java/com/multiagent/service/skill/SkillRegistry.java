package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.Skill;
import com.multiagent.infrastructure.model.SkillCategory;

import java.util.List;

/**
 * 技能注册中心接口 — 管理 Skill 的注册、发现和匹配。
 * 完整实现将在 Task 3.1 中提供。
 */
public interface SkillRegistry {

    /**
     * 注册一个 Skill。
     */
    void register(Skill skill);

    /**
     * 语义匹配查询，返回按相关度排序的 Top-K 结果。
     *
     * @param query 查询文本
     * @param topK  返回数量上限
     * @return 匹配的 Skill 列表（含置信度分数），按分数降序
     */
    List<ScoredSkill> match(String query, int topK);

    /**
     * 按分类获取 Skill 列表。
     */
    List<Skill> listByCategory(SkillCategory category);

    /**
     * 根据 ID 获取 Skill，不存在时返回 null。
     */
    Skill getById(String skillId);

    /**
     * 匹配结果，包含 Skill 和置信度分数。
     */
    record ScoredSkill(Skill skill, double score) {}
}
