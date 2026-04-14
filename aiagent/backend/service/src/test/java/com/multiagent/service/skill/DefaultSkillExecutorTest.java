package com.multiagent.service.skill;

import com.multiagent.adapter.AgentAdapter;
import com.multiagent.adapter.AgentRegistry;
import com.multiagent.infrastructure.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultSkillExecutorTest {

    @Mock
    private ToolRunner toolRunner;

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private AgentAdapter agentAdapter;

    private TemplateRenderer templateRenderer;
    private DefaultSkillExecutor executor;

    @BeforeEach
    void setUp() {
        templateRenderer = new TemplateRenderer();
        executor = new DefaultSkillExecutor(toolRunner, templateRenderer, agentRegistry);
    }

    // --- Required parameter validation ---

    @Test
    void shouldThrowMissingParameterExceptionWhenRequiredParamMissing() {
        Skill skill = buildSkill("test-skill", List.of(
                SkillParam.builder().name("instanceId").required(true).build()
        ), List.of());

        Map<String, Object> params = new HashMap<>();

        MissingParameterException ex = assertThrows(MissingParameterException.class,
                () -> executor.execute(skill, params, buildContext()));

        assertEquals("instanceId", ex.getParameterName());
        assertEquals("test-skill", ex.getSkillId());
    }

    @Test
    void shouldPassValidationWhenAllRequiredParamsProvided() {
        Skill skill = buildSkill("test-skill", List.of(
                SkillParam.builder().name("instanceId").required(true).build()
        ), List.of());

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", "rm-bp1234");

        // No steps, so it should succeed without error
        SkillResult result = executor.execute(skill, params, buildContext());
        assertTrue(result.isSuccess());
    }

    // --- TOOL_CALL step ---

    @Test
    void shouldExecuteToolCallStepAndStoreResult() {
        WorkflowStep step = WorkflowStep.builder()
                .id("step1")
                .type(StepType.TOOL_CALL)
                .toolName("rds-disk-metrics")
                .outputKey("diskMetrics")
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(step));
        when(toolRunner.run(eq("rds-disk-metrics"), any())).thenReturn(Map.of("usage", 85));

        SkillResult result = executor.execute(skill, new HashMap<>(), buildContext());

        assertTrue(result.isSuccess());
        assertEquals(Map.of("usage", 85), result.getResults().get("diskMetrics"));
        verify(toolRunner).run(eq("rds-disk-metrics"), any());
    }

    // --- LLM_CALL step ---

    @Test
    void shouldExecuteLlmCallStepWithTemplateRendering() {
        WorkflowStep step = WorkflowStep.builder()
                .id("analyze")
                .type(StepType.LLM_CALL)
                .prompt("分析以下数据: {{diskMetrics}}")
                .outputKey("analysis")
                .build();

        Skill skill = buildSkill("test-skill", "rds-agent", List.of(), List.of(step));

        when(agentRegistry.getById("rds-agent")).thenReturn(agentAdapter);
        when(agentAdapter.invoke(any(UnifiedRequest.class)))
                .thenReturn(UnifiedResponse.builder().content("磁盘使用率正常").build());

        Map<String, Object> params = new HashMap<>();
        params.put("diskMetrics", "usage=85%");

        SkillResult result = executor.execute(skill, params, buildContext());

        assertTrue(result.isSuccess());
        assertEquals("磁盘使用率正常", result.getResults().get("analysis"));
        verify(agentAdapter).invoke(argThat(req ->
                req.getInput().contains("usage=85%")));
    }

    @Test
    void shouldFailLlmCallWhenAgentNotRegistered() {
        WorkflowStep step = WorkflowStep.builder()
                .id("analyze")
                .type(StepType.LLM_CALL)
                .prompt("test prompt")
                .outputKey("result")
                .build();

        Skill skill = buildSkill("test-skill", "missing-agent", List.of(), List.of(step));
        when(agentRegistry.getById("missing-agent")).thenReturn(null);

        SkillResult result = executor.execute(skill, new HashMap<>(), buildContext());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未注册"));
    }

    // --- CONDITION step ---

    @Test
    void shouldExecuteMatchingBranchInConditionStep() {
        WorkflowStep alertStep = WorkflowStep.builder()
                .id("alert")
                .type(StepType.TOOL_CALL)
                .toolName("send-alert")
                .outputKey("alertResult")
                .build();

        WorkflowStep conditionStep = WorkflowStep.builder()
                .id("check")
                .type(StepType.CONDITION)
                .condition("usage > 90")
                .branches(List.of(
                        Branch.builder().when("true").then(List.of(alertStep)).build(),
                        Branch.builder().when("false").then(List.of()).build()
                ))
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(conditionStep));
        when(toolRunner.run(eq("send-alert"), any())).thenReturn("alert sent");

        Map<String, Object> params = new HashMap<>();
        params.put("usage", 95);

        SkillResult result = executor.execute(skill, params, buildContext());

        assertTrue(result.isSuccess());
        assertEquals("alert sent", result.getResults().get("alertResult"));
    }

    @Test
    void shouldNotExecuteAnyBranchWhenConditionDoesNotMatch() {
        WorkflowStep conditionStep = WorkflowStep.builder()
                .id("check")
                .type(StepType.CONDITION)
                .condition("usage > 90")
                .branches(List.of(
                        Branch.builder().when("true").then(List.of()).build()
                ))
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(conditionStep));

        Map<String, Object> params = new HashMap<>();
        params.put("usage", 50);

        SkillResult result = executor.execute(skill, params, buildContext());
        assertTrue(result.isSuccess());
        verifyNoInteractions(toolRunner);
    }

    // --- PARALLEL step ---

    @Test
    void shouldExecuteParallelChildrenAndCollectResults() {
        WorkflowStep child1 = WorkflowStep.builder()
                .id("child1")
                .type(StepType.TOOL_CALL)
                .toolName("tool-a")
                .outputKey("resultA")
                .build();

        WorkflowStep child2 = WorkflowStep.builder()
                .id("child2")
                .type(StepType.TOOL_CALL)
                .toolName("tool-b")
                .outputKey("resultB")
                .build();

        WorkflowStep parallelStep = WorkflowStep.builder()
                .id("parallel")
                .type(StepType.PARALLEL)
                .children(List.of(child1, child2))
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(parallelStep));
        when(toolRunner.run(eq("tool-a"), any())).thenReturn("A done");
        when(toolRunner.run(eq("tool-b"), any())).thenReturn("B done");

        SkillResult result = executor.execute(skill, new HashMap<>(), buildContext());

        assertTrue(result.isSuccess());
        assertEquals("A done", result.getResults().get("resultA"));
        assertEquals("B done", result.getResults().get("resultB"));
    }

    // --- HUMAN_CONFIRM step ---

    @Test
    void shouldWaitForHumanConfirmationAndProceed() throws Exception {
        WorkflowStep confirmStep = WorkflowStep.builder()
                .id("confirm-delete")
                .type(StepType.HUMAN_CONFIRM)
                .outputKey("confirmed")
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(confirmStep));

        // Simulate user confirming after a short delay
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                .execute(() -> executor.confirmStep("confirm-delete", true));

        SkillResult result = executor.execute(skill, new HashMap<>(), buildContext());

        assertTrue(result.isSuccess());
        assertEquals(true, result.getResults().get("confirmed"));
    }

    @Test
    void shouldFailWhenHumanRejectsConfirmation() throws Exception {
        WorkflowStep confirmStep = WorkflowStep.builder()
                .id("confirm-delete")
                .type(StepType.HUMAN_CONFIRM)
                .outputKey("confirmed")
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(confirmStep));

        // Simulate user rejecting after a short delay
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                .execute(() -> executor.confirmStep("confirm-delete", false));

        SkillResult result = executor.execute(skill, new HashMap<>(), buildContext());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("拒绝"));
    }

    // --- Results map accumulation ---

    @Test
    void shouldAccumulateResultsAcrossSteps() {
        WorkflowStep step1 = WorkflowStep.builder()
                .id("step1")
                .type(StepType.TOOL_CALL)
                .toolName("tool-a")
                .outputKey("resultA")
                .build();

        WorkflowStep step2 = WorkflowStep.builder()
                .id("step2")
                .type(StepType.TOOL_CALL)
                .toolName("tool-b")
                .outputKey("resultB")
                .build();

        Skill skill = buildSkill("test-skill", List.of(), List.of(step1, step2));
        when(toolRunner.run(eq("tool-a"), any())).thenReturn("A");
        when(toolRunner.run(eq("tool-b"), any())).thenReturn("B");

        SkillResult result = executor.execute(skill, new HashMap<>(), buildContext());

        assertTrue(result.isSuccess());
        assertEquals("A", result.getResults().get("resultA"));
        assertEquals("B", result.getResults().get("resultB"));
    }

    // --- Helper methods ---

    private Skill buildSkill(String id, List<SkillParam> params, List<WorkflowStep> steps) {
        return buildSkill(id, "default-agent", params, steps);
    }

    private Skill buildSkill(String id, String agentId, List<SkillParam> params, List<WorkflowStep> steps) {
        return Skill.builder()
                .id(id)
                .agentId(agentId)
                .inputParams(params)
                .workflow(SkillWorkflow.builder().steps(steps).build())
                .build();
    }

    private SharedContext buildContext() {
        return SharedContext.builder()
                .sessionId("test-session")
                .build();
    }
}
