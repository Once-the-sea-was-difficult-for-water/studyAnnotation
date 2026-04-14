package com.multiagent.service.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiagent.infrastructure.model.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Skill 配置 Redis 缓存管理器。
 * <p>
 * 使用 Redis 缓存 Skill 配置，减少数据库查询。
 * 在 Skill 注册时写入缓存，更新/删除时失效缓存。
 * <p>
 * 缓存 key 格式: {@code skill:config:{skillId}}
 * 默认 TTL: 1 小时
 */
@Component
public class SkillCacheManager {

    private static final Logger log = LoggerFactory.getLogger(SkillCacheManager.class);

    private static final String CACHE_KEY_PREFIX = "skill:config:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SkillCacheManager(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 缓存 Skill 配置到 Redis。
     *
     * @param skill 待缓存的 Skill
     */
    public void cacheSkill(Skill skill) {
        if (skill == null || skill.getId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(skill);
            redisTemplate.opsForValue().set(buildKey(skill.getId()), json, DEFAULT_TTL);
            log.debug("Cached skill config: {}", skill.getId());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize skill for caching: {}", skill.getId(), e);
        }
    }

    /**
     * 从 Redis 获取缓存的 Skill 配置。
     *
     * @param skillId Skill ID
     * @return 缓存的 Skill，不存在时返回 empty
     */
    public Optional<Skill> getCachedSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(buildKey(skillId));
            if (json == null) {
                return Optional.empty();
            }
            Skill skill = objectMapper.readValue(json, Skill.class);
            log.debug("Cache hit for skill: {}", skillId);
            return Optional.of(skill);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached skill: {}", skillId, e);
            return Optional.empty();
        }
    }

    /**
     * 失效指定 Skill 的缓存。
     *
     * @param skillId Skill ID
     */
    public void evict(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return;
        }
        Boolean deleted = redisTemplate.delete(buildKey(skillId));
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Evicted skill cache: {}", skillId);
        }
    }

    /**
     * 失效所有 Skill 缓存（通过 key 前缀扫描删除）。
     */
    public void evictAll() {
        var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Evicted all skill caches, count: {}", keys.size());
        }
    }

    private String buildKey(String skillId) {
        return CACHE_KEY_PREFIX + skillId;
    }
}
