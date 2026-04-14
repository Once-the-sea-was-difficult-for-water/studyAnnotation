package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SkillSemanticMatcher 默认实现 — 基于关键词匹配的 stub。
 * <p>
 * 委托 {@link DefaultSkillRegistry} 的关键词匹配逻辑作为语义匹配的降级方案。
 * 当 Milvus/Qdrant 向量库可用时，可替换为真正的 Embedding + VectorStore 实现。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>维护内部 Skill 索引（ConcurrentHashMap），支持增删</li>
 *   <li>match() 基于 name + description 关键词评分，name 命中额外加权</li>
 *   <li>index()/removeIndex() 管理索引生命周期</li>
 * </ul>
 */
@Component
public class DefaultSkillSemanticMatcher implements SkillSemanticMatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillSemanticMatcher.class);

    /** 内部 Skill 索引，模拟向量库存储 */
    private final ConcurrentHashMap<String, Skill> indexedSkills = new ConcurrentHashMap<>();

    @Override
    public List<SkillRegistry.ScoredSkill> match(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return Collections.emptyList();
        }

        String lowerQuery = query.toLowerCase();
        String[] queryTokens = lowerQuery.split("\\s+");

        return indexedSkills.values().stream()
                .map(skill -> new SkillRegistry.ScoredSkill(skill, computeScore(skill, queryTokens)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(SkillRegistry.ScoredSkill::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void index(Skill skill) {
        if (skill == null || skill.getId() == null) {
            return;
        }
        indexedSkills.put(skill.getId(), skill);
        log.debug("Indexed skill for semantic matching: {}", skill.getId());
    }

    @Override
    public void removeIndex(String skillId) {
        if (skillId == null) {
            return;
        }
        indexedSkills.remove(skillId);
        log.debug("Removed skill from semantic index: {}", skillId);
    }

    /**
     * 计算查询与 Skill 的匹配分数。
     * <p>
     * 当前使用关键词命中率作为分数，name 命中额外加权 0.3。
     * 向量实现时替换为 cosine similarity。
     */
    private double computeScore(Skill skill, String[] queryTokens) {
        String name = skill.getName() != null ? skill.getName().toLowerCase() : "";
        String description = skill.getDescription() != null ? skill.getDescription().toLowerCase() : "";
        String searchText = name + " " + description;

        int hits = 0;
        for (String token : queryTokens) {
            if (searchText.contains(token)) {
                hits++;
            }
        }

        int nameHits = 0;
        for (String token : queryTokens) {
            if (name.contains(token)) {
                nameHits++;
            }
        }

        if (queryTokens.length == 0) {
            return 0.0;
        }

        double baseScore = (double) hits / queryTokens.length;
        double nameBonus = (double) nameHits / queryTokens.length * 0.3;
        return Math.min(1.0, baseScore + nameBonus);
    }

    /**
     * 获取当前索引的 Skill 数量（用于测试和监控）。
     */
    public int indexSize() {
        return indexedSkills.size();
    }
}
