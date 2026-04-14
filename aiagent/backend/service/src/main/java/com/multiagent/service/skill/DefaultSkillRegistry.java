package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.Skill;
import com.multiagent.infrastructure.model.SkillCategory;
import com.multiagent.infrastructure.validation.SkillValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SkillRegistry 默认实现 — 基于 ConcurrentHashMap 的内存注册中心。
 * <p>
 * 提供 Skill 注册（含验证）、按分类查询、按 ID 查询和简单关键词匹配功能。
 * 向量语义匹配将在 Task 3.2 中增强。
 */
@Service
public class DefaultSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillRegistry.class);

    private final ConcurrentHashMap<String, Skill> skills = new ConcurrentHashMap<>();
    private final SkillValidator skillValidator;

    /**
     * 已注册的 Agent ID 集合，用于验证 Skill 的 agentId 引用。
     * 可通过 {@link #registerAgentId(String)} 添加。
     */
    private final Set<String> registeredAgentIds = ConcurrentHashMap.newKeySet();

    public DefaultSkillRegistry() {
        this.skillValidator = new SkillValidator();
    }

    public DefaultSkillRegistry(SkillValidator skillValidator) {
        this.skillValidator = skillValidator;
    }

    /**
     * 注册一个已知的 Agent ID（供 Skill 验证引用）。
     */
    public void registerAgentId(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            registeredAgentIds.add(agentId);
        }
    }

    @Override
    public void register(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill must not be null");
        }

        List<String> errors = skillValidator.validate(skill, skills.keySet(), registeredAgentIds);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Skill validation failed: " + String.join("; ", errors));
        }

        skills.put(skill.getId(), skill);
        log.info("Registered skill: {} ({})", skill.getId(), skill.getName());
    }

    @Override
    public List<ScoredSkill> match(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return Collections.emptyList();
        }

        String lowerQuery = query.toLowerCase();
        String[] queryTokens = lowerQuery.split("\\s+");

        return skills.values().stream()
                .map(skill -> new ScoredSkill(skill, computeKeywordScore(skill, queryTokens)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredSkill::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> listByCategory(SkillCategory category) {
        if (category == null) {
            return Collections.emptyList();
        }
        return skills.values().stream()
                .filter(skill -> category.equals(skill.getCategory()))
                .collect(Collectors.toList());
    }

    @Override
    public Skill getById(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return skills.get(skillId);
    }

    /**
     * 简单关键词匹配评分。
     * 在 name 和 description 中搜索查询 token，计算命中比例作为分数。
     * 向量语义匹配将在 Task 3.2 中替换此实现。
     */
    private double computeKeywordScore(Skill skill, String[] queryTokens) {
        String name = skill.getName() != null ? skill.getName().toLowerCase() : "";
        String description = skill.getDescription() != null ? skill.getDescription().toLowerCase() : "";
        String searchText = name + " " + description;

        int hits = 0;
        for (String token : queryTokens) {
            if (searchText.contains(token)) {
                hits++;
            }
        }

        // name 命中额外加权
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
     * 获取当前注册的 Skill 数量（用于测试和监控）。
     */
    public int size() {
        return skills.size();
    }
}
