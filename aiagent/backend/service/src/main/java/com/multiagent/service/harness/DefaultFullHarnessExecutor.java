package com.multiagent.service.harness;

import com.multiagent.adapter.AgentAdapter;
import com.multiagent.adapter.AgentRegistry;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import com.multiagent.service.context.ContextResetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 三代理协作执行器默认实现。
 * <p>
 * 流程：
 * <ol>
 *   <li>Planner 根据需求生成完整规格书</li>
 *   <li>将规格书拆分为多个 Sprint</li>
 *   <li>逐 Sprint 使用 DualAgentExecutor（Generator+Evaluator）迭代执行</li>
 *   <li>每个 Sprint 之间检查上下文 token 数，超过 100K 阈值时重置</li>
 * </ol>
 */
@Service
public class DefaultFullHarnessExecutor implements FullHarnessExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultFullHarnessExecutor.class);

    static final String PLANNER_AGENT_ID = "planner-agent";
    static final int MAX_ROUNDS_PER_SPRINT = 3;

    private final AgentRegistry agentRegistry;
    private final DualAgentExecutor dualAgentExecutor;
    private final ContextResetManager contextResetManager;

    public DefaultFullHarnessExecutor(AgentRegistry agentRegistry,
                                      DualAgentExecutor dualAgentExecutor,
                                      ContextResetManager contextResetManager) {
        this.agentRegistry = agentRegistry;
        this.dualAgentExecutor = dualAgentExecutor;
        this.contextResetManager = contextResetManager;
    }

    @Override
    public HarnessResult execute(String requirement, SharedContext context) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("requirement must not be null or blank");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        // Step 1: Planner generates spec
        String spec = generateSpec(requirement, context);
        log.info("Planner 生成规格书完成，长度: {}", spec.length());

        // Step 2: Split spec into sprints
        List<String> sprints = splitIntoSprints(spec);
        log.info("规格书拆分为 {} 个 Sprint", sprints.size());

        // Step 3: Iterate through each sprint using DualAgentExecutor
        List<String> results = new ArrayList<>();
        SharedContext currentContext = context;

        for (int i = 0; i < sprints.size(); i++) {
            int sprintNumber = i + 1;
            log.info("开始执行 Sprint {}/{}", sprintNumber, sprints.size());

            // Check context reset between sprints
            currentContext = contextResetManager.checkAndResetIfNeeded(currentContext);

            String sprintTask = sprints.get(i);
            DualAgentResult sprintResult = dualAgentExecutor.execute(
                    sprintTask, currentContext, MAX_ROUNDS_PER_SPRINT);

            results.add(sprintResult.getOutput());

            if (!sprintResult.isPassed()) {
                log.warn("Sprint {}/{} 未通过评估，终止协作", sprintNumber, sprints.size());
                return HarnessResult.failed(results, sprintNumber);
            }

            log.info("Sprint {}/{} 完成", sprintNumber, sprints.size());
        }

        return HarnessResult.success(results, sprints.size());
    }

    /**
     * 使用 Planner Agent 根据需求生成完整规格书。
     */
    String generateSpec(String requirement, SharedContext context) {
        AgentAdapter planner = agentRegistry.getById(PLANNER_AGENT_ID);
        if (planner == null) {
            throw new IllegalStateException("planner-agent not registered in AgentRegistry");
        }

        String prompt = "请根据以下需求生成完整的规格书，并将任务拆分为多个 Sprint（用 '---SPRINT---' 分隔）：\n" + requirement;
        UnifiedRequest request = UnifiedRequest.builder()
                .input(prompt)
                .context(context)
                .build();
        UnifiedResponse response = planner.invoke(request);
        return response.getContent();
    }

    /**
     * 将规格书按 '---SPRINT---' 分隔符拆分为多个 Sprint 任务。
     * 若无分隔符，整个规格书作为单个 Sprint。
     */
    List<String> splitIntoSprints(String spec) {
        if (spec == null || spec.isBlank()) {
            return List.of(spec != null ? spec : "");
        }
        String[] parts = spec.split("---SPRINT---");
        List<String> sprints = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sprints.add(trimmed);
            }
        }
        return sprints.isEmpty() ? List.of(spec) : sprints;
    }
}
