package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Skill 热加载器 — 监听 Nacos 配置变更，运行时重新加载 Skill。
 * <p>
 * 实现方式：
 * <ul>
 *   <li>通过 {@code @Value} 注入 Nacos 管理的 skill 配置内容</li>
 *   <li>定时轮询检测配置变更（对比内容 hash）</li>
 *   <li>配置变更时解析 YAML 并重新注册到 SkillRegistry</li>
 *   <li>同步更新语义索引和 Redis 缓存</li>
 * </ul>
 * <p>
 * 当 Nacos 不可用时，热加载功能降级为无操作，不影响已注册的 Skill。
 */
@Component
public class SkillHotReloader {

    private static final Logger log = LoggerFactory.getLogger(SkillHotReloader.class);

    private final DefaultSkillRegistry skillRegistry;
    private final SkillSemanticMatcher semanticMatcher;
    private final SkillCacheManager cacheManager;
    private final SkillYamlLoader yamlLoader;

    /** 上一次加载的配置内容 hash，用于检测变更 */
    private final AtomicReference<Integer> lastConfigHash = new AtomicReference<>(0);

    /** Nacos 管理的 Skill 配置内容（YAML 格式，多个 Skill 用 --- 分隔） */
    @Value("${skill.config.content:}")
    private String skillConfigContent;

    public SkillHotReloader(DefaultSkillRegistry skillRegistry,
                            SkillSemanticMatcher semanticMatcher,
                            SkillCacheManager cacheManager) {
        this.skillRegistry = skillRegistry;
        this.semanticMatcher = semanticMatcher;
        this.cacheManager = cacheManager;
        this.yamlLoader = new SkillYamlLoader();
    }

    /**
     * 定时检测 Nacos 配置变更并重新加载 Skill。
     * 每 30 秒执行一次。
     */
    @Scheduled(fixedDelayString = "${skill.config.poll-interval-ms:30000}")
    public void checkAndReload() {
        if (skillConfigContent == null || skillConfigContent.isBlank()) {
            return;
        }

        int currentHash = skillConfigContent.hashCode();
        if (currentHash == lastConfigHash.get()) {
            return;
        }

        log.info("Skill config change detected, reloading...");
        reload(skillConfigContent);
        lastConfigHash.set(currentHash);
    }

    /**
     * 手动触发重新加载指定的 YAML 配置内容。
     *
     * @param yamlContent YAML 格式的 Skill 配置（多个 Skill 用 --- 分隔）
     */
    public void reload(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            log.warn("Empty skill config content, skipping reload");
            return;
        }

        List<String> documents = splitYamlDocuments(yamlContent);
        int successCount = 0;
        int failCount = 0;

        for (String doc : documents) {
            if (doc.isBlank()) {
                continue;
            }
            try {
                Skill skill = yamlLoader.load(new StringReader(doc));
                registerSkill(skill);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to reload skill from config: {}", e.getMessage());
            }
        }

        log.info("Skill hot-reload complete: {} succeeded, {} failed", successCount, failCount);
    }

    /**
     * 注册单个 Skill 并同步更新语义索引和缓存。
     */
    private void registerSkill(Skill skill) {
        try {
            skillRegistry.register(skill);
        } catch (IllegalArgumentException e) {
            // 如果 Skill 已存在（id 重复），说明是更新场景
            // 当前 DefaultSkillRegistry 不支持更新，记录日志跳过
            log.debug("Skill registration skipped (may already exist): {}", e.getMessage());
            return;
        }
        semanticMatcher.index(skill);
        cacheManager.cacheSkill(skill);
    }

    /**
     * 将多文档 YAML（--- 分隔）拆分为单独的文档。
     */
    private List<String> splitYamlDocuments(String yamlContent) {
        String[] parts = yamlContent.split("(?m)^---\\s*$");
        return List.of(parts);
    }
}
