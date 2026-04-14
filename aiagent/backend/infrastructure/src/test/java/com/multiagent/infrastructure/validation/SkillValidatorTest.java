package com.multiagent.infrastructure.validation;

import com.multiagent.infrastructure.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkillValidatorTest {

    private SkillValidator validator;
    private Set<String> existingSkillIds;
    private Set<String> registeredAgentIds;

    @BeforeEach
    void setUp() {
        validator = new SkillValidator();
        existingSkillIds = new HashSet<>();
        registeredAgentIds = new HashSet<>(Set.of("rds-diagnose-agent", "log-agent"));
    }

    private Skill.SkillBuilder validSkillBuilder() {
        return Skill.builder()
                .id("disk-space-diagnose")
                .name("磁盘空间分析")
                .agentId("rds-diagnose-agent")
                .category(SkillCategory.DIAGNOSE)
                .workflow(SkillWorkflow.builder()
                        .steps(List.of(WorkflowStep.builder()
                                .id("step1")
                                .type(StepType.TOOL_CALL)
                                .toolName("rds-disk-metrics")
                                .outputKey("diskMetrics")
                                .build()))
                        .build());
    }

    @Test
    void validSkill_shouldReturnNoErrors() {
        Skill skill = validSkillBuilder().build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.isEmpty());
    }

    @Test
    void nullId_shouldReturnError() {
        Skill skill = validSkillBuilder().id(null).build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("id must not be empty")));
    }

    @Test
    void blankId_shouldReturnError() {
        Skill skill = validSkillBuilder().id("  ").build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("id must not be empty")));
    }

    @Test
    void nonKebabCaseId_shouldReturnError() {
        Skill skill = validSkillBuilder().id("DiskSpace").build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("kebab-case")));
    }

    @Test
    void duplicateId_shouldReturnError() {
        existingSkillIds.add("disk-space-diagnose");
        Skill skill = validSkillBuilder().build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("already exists")));
    }

    @Test
    void nullAgentId_shouldReturnError() {
        Skill skill = validSkillBuilder().agentId(null).build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("agentId must not be empty")));
    }

    @Test
    void unregisteredAgentId_shouldReturnError() {
        Skill skill = validSkillBuilder().agentId("unknown-agent").build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unregistered agent")));
    }

    @Test
    void nullWorkflow_shouldReturnError() {
        Skill skill = validSkillBuilder().workflow(null).build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("at least one step")));
    }

    @Test
    void emptyWorkflowSteps_shouldReturnError() {
        Skill skill = validSkillBuilder()
                .workflow(SkillWorkflow.builder().steps(Collections.emptyList()).build())
                .build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("at least one step")));
    }

    @Test
    void conditionStepWithoutBranches_shouldReturnError() {
        Skill skill = validSkillBuilder()
                .workflow(SkillWorkflow.builder()
                        .steps(List.of(WorkflowStep.builder()
                                .id("cond1")
                                .type(StepType.CONDITION)
                                .condition("x > 0")
                                .build()))
                        .build())
                .build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("CONDITION step must contain branches")));
    }

    @Test
    void conditionStepWithBranches_shouldPass() {
        Skill skill = validSkillBuilder()
                .workflow(SkillWorkflow.builder()
                        .steps(List.of(WorkflowStep.builder()
                                .id("cond1")
                                .type(StepType.CONDITION)
                                .condition("x > 0")
                                .branches(List.of(Branch.builder()
                                        .when("true")
                                        .then(List.of(WorkflowStep.builder()
                                                .id("inner")
                                                .type(StepType.TOOL_CALL)
                                                .build()))
                                        .build()))
                                .build()))
                        .build())
                .build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.isEmpty());
    }

    @Test
    void parallelStepWithoutChildren_shouldReturnError() {
        Skill skill = validSkillBuilder()
                .workflow(SkillWorkflow.builder()
                        .steps(List.of(WorkflowStep.builder()
                                .id("par1")
                                .type(StepType.PARALLEL)
                                .build()))
                        .build())
                .build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.stream().anyMatch(e -> e.contains("PARALLEL step must contain children")));
    }

    @Test
    void parallelStepWithChildren_shouldPass() {
        Skill skill = validSkillBuilder()
                .workflow(SkillWorkflow.builder()
                        .steps(List.of(WorkflowStep.builder()
                                .id("par1")
                                .type(StepType.PARALLEL)
                                .children(List.of(
                                        WorkflowStep.builder().id("c1").type(StepType.TOOL_CALL).build(),
                                        WorkflowStep.builder().id("c2").type(StepType.TOOL_CALL).build()))
                                .build()))
                        .build())
                .build();
        List<String> errors = validator.validate(skill, existingSkillIds, registeredAgentIds);
        assertTrue(errors.isEmpty());
    }
}
