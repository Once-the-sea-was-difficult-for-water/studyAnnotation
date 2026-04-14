package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SkillHotReloader 单元测试。
 */
class SkillHotReloaderTest {

    private DefaultSkillRegistry skillRegistry;
    private SkillSemanticMatcher semanticMatcher;
    private SkillCacheManager cacheManager;
    private SkillHotReloader reloader;

    @BeforeEach
    void setUp() {
        skillRegistry = new DefaultSkillRegistry();
        skillRegistry.registerAgentId("rds-diagnose-agent");
        semanticMatcher = mock(SkillSemanticMatcher.class);
        cacheManager = mock(SkillCacheManager.class);
        reloader = new SkillHotReloader(skillRegistry, semanticMatcher, cacheManager);
    }

    @Test
    void reload_parsesAndRegistersSkillFromYaml() {
        String yaml = """
                id: disk-space-diagnose
                name: 磁盘空间分析
                description: 分析磁盘空间使用情况
                category: diagnose
                agentId: rds-diagnose-agent
                workflow:
                  steps:
                    - id: collect
                      type: tool_call
                      toolName: rds-disk-metrics
                      outputKey: metrics
                """;

        reloader.reload(yaml);

        Skill registered = skillRegistry.getById("disk-space-diagnose");
        assertNotNull(registered);
        assertEquals("磁盘空间分析", registered.getName());
        verify(semanticMatcher).index(any(Skill.class));
        verify(cacheManager).cacheSkill(any(Skill.class));
    }

    @Test
    void reload_handlesMultipleDocuments() {
        skillRegistry.registerAgentId("cpu-agent");
        String yaml = """
                id: skill-one
                name: 技能一
                description: 第一个技能
                category: diagnose
                agentId: rds-diagnose-agent
                workflow:
                  steps:
                    - id: step1
                      type: tool_call
                      toolName: tool1
                      outputKey: out1
                ---
                id: skill-two
                name: 技能二
                description: 第二个技能
                category: monitor
                agentId: cpu-agent
                workflow:
                  steps:
                    - id: step1
                      type: tool_call
                      toolName: tool2
                      outputKey: out2
                """;

        reloader.reload(yaml);

        assertNotNull(skillRegistry.getById("skill-one"));
        assertNotNull(skillRegistry.getById("skill-two"));
        assertEquals(2, skillRegistry.size());
    }

    @Test
    void reload_skipsEmptyContent() {
        reloader.reload("");
        assertEquals(0, skillRegistry.size());
    }

    @Test
    void reload_skipsNullContent() {
        reloader.reload(null);
        assertEquals(0, skillRegistry.size());
    }

    @Test
    void reload_continuesOnInvalidDocument() {
        String yaml = """
                id: invalid skill id!
                name: Bad Skill
                ---
                id: valid-skill
                name: 有效技能
                description: 有效的技能
                category: diagnose
                agentId: rds-diagnose-agent
                workflow:
                  steps:
                    - id: step1
                      type: tool_call
                      toolName: tool1
                      outputKey: out1
                """;

        reloader.reload(yaml);

        // Invalid skill should be skipped, valid one registered
        assertNull(skillRegistry.getById("invalid skill id!"));
        assertNotNull(skillRegistry.getById("valid-skill"));
    }
}
