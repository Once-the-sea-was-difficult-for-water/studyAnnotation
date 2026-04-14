package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.Skill;

import java.util.List;

/**
 * 技能语义匹配接口 — 基于向量索引的语义匹配（Embedding + VectorStore）。
 * <p>
 * 当前提供基于关键词的默认实现，可替换为 Milvus/Qdrant 向量检索实现。
 *
 * @see DefaultSkillSemanticMatcher
 */
public interface SkillSemanticMatcher {

    /**
     * 语义匹配查询，返回按相关度排序的 Top-K 结果。
     *
     * @param query 用户查询文本
     * @param topK  返回数量上限
     * @return 匹配的 Skill 列表（含置信度分数），按分数降序
     */
    List<SkillRegistry.ScoredSkill> match(String query, int topK);

    /**
     * 索引一个 Skill（生成 Embedding 并存入向量库）。
     * 在 Skill 注册或更新时调用。
     *
     * @param skill 待索引的 Skill
     */
    void index(Skill skill);

    /**
     * 从索引中移除一个 Skill。
     *
     * @param skillId 待移除的 Skill ID
     */
    void removeIndex(String skillId);
}
