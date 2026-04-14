package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultSkillSemanticMatcher 单元测试。
 */
class SkillSemanticMatcherTest {

    private DefaultSkillSemanticMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new DefaultSkillSemanticMatcher();
    }

    @Test
    void match_returnsEmptyForNullQuery() {
        matcher.index(buildSkill("disk-diagnose", "磁盘诊断", "分析磁盘空间"));
        List<SkillRegistry.ScoredSkill> results = matcher.match(null, 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void match_returnsEmptyForBlankQuery() {
        matcher.index(buildSkill("disk-diagnose", "磁盘诊断", "分析磁盘空间"));
        List<SkillRegistry.ScoredSkill> results = matcher.match("  ", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void match_returnsEmptyWhenNoSkillsIndexed() {
        List<SkillRegistry.ScoredSkill> results = matcher.match("磁盘", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void match_returnsMatchingSkills() {
        matcher.index(buildSkill("disk-diagnose", "磁盘诊断", "分析磁盘空间使用情况"));
        matcher.index(buildSkill("cpu-monitor", "CPU监控", "监控CPU使用率"));

        List<SkillRegistry.ScoredSkill> results = matcher.match("磁盘", 5);
        assertFalse(results.isEmpty());
        assertEquals("disk-diagnose", results.get(0).skill().getId());
    }

    @Test
    void match_respectsTopKLimit() {
        matcher.index(buildSkill("skill-a", "分析A", "分析功能A"));
        matcher.index(buildSkill("skill-b", "分析B", "分析功能B"));
        matcher.index(buildSkill("skill-c", "分析C", "分析功能C"));

        List<SkillRegistry.ScoredSkill> results = matcher.match("分析", 2);
        assertEquals(2, results.size());
    }

    @Test
    void match_resultsSortedByScoreDescending() {
        matcher.index(buildSkill("disk-diagnose", "磁盘诊断", "分析磁盘空间"));
        matcher.index(buildSkill("disk-optimize", "磁盘优化", "优化磁盘空间使用"));

        List<SkillRegistry.ScoredSkill> results = matcher.match("磁盘 诊断", 5);
        assertTrue(results.size() >= 1);
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).score() >= results.get(i).score());
        }
    }

    @Test
    void index_addsSkillToIndex() {
        assertEquals(0, matcher.indexSize());
        matcher.index(buildSkill("test-skill", "测试", "测试技能"));
        assertEquals(1, matcher.indexSize());
    }

    @Test
    void index_ignoresNullSkill() {
        matcher.index(null);
        assertEquals(0, matcher.indexSize());
    }

    @Test
    void removeIndex_removesSkillFromIndex() {
        matcher.index(buildSkill("test-skill", "测试", "测试技能"));
        assertEquals(1, matcher.indexSize());

        matcher.removeIndex("test-skill");
        assertEquals(0, matcher.indexSize());
    }

    @Test
    void removeIndex_ignoresNullId() {
        matcher.index(buildSkill("test-skill", "测试", "测试技能"));
        matcher.removeIndex(null);
        assertEquals(1, matcher.indexSize());
    }

    @Test
    void match_returnsZeroTopK() {
        matcher.index(buildSkill("disk-diagnose", "磁盘诊断", "分析磁盘空间"));
        List<SkillRegistry.ScoredSkill> results = matcher.match("磁盘", 0);
        assertTrue(results.isEmpty());
    }

    private Skill buildSkill(String id, String name, String description) {
        return Skill.builder()
                .id(id)
                .name(name)
                .description(description)
                .agentId("test-agent")
                .category(SkillCategory.DIAGNOSE)
                .workflow(SkillWorkflow.builder()
                        .steps(List.of(WorkflowStep.builder()
                                .id("step-1")
                                .type(StepType.TOOL_CALL)
                                .toolName("test-tool")
                                .outputKey("result")
                                .build()))
                        .build())
                .build();
    }
}
