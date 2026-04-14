package com.multiagent.service.harness;

import com.multiagent.adapter.AgentAdapter;
import com.multiagent.adapter.AgentRegistry;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * 双 Agent 博弈执行器默认实现。
 * <p>
 * 循环逻辑：
 * <ol>
 *   <li>Generator 根据任务（首轮）或任务+反馈（后续轮）生成输出</li>
 *   <li>Evaluator 仅基于 Generator 输出和原始任务要求独立评估</li>
 *   <li>评估从正确性、完整性、质量三个维度打分（1-10），所有维度 ≥ 8 输出 PASS</li>
 *   <li>未通过时将评估反馈注入下一轮 context</li>
 *   <li>达到 maxRounds 返回 maxRoundsReached 结果</li>
 * </ol>
 */
@Service
public class DefaultDualAgentExecutor implements DualAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultDualAgentExecutor.class);

    static final String GENERATOR_AGENT_ID = "generator-agent";
    static final String EVALUATOR_AGENT_ID = "evaluator-agent";
    private static final String PASS_KEYWORD = "PASS";

    private final AgentRegistry agentRegistry;

    public DefaultDualAgentExecutor(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Override
    public DualAgentResult execute(String task, SharedContext context, int maxRounds) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be null or blank");
        }
        if (maxRounds <= 0) {
            throw new IllegalArgumentException("maxRounds must be > 0");
        }

        AgentAdapter generator = agentRegistry.getById(GENERATOR_AGENT_ID);
        AgentAdapter evaluator = agentRegistry.getById(EVALUATOR_AGENT_ID);

        if (generator == null) {
            throw new IllegalStateException("generator-agent not registered in AgentRegistry");
        }
        if (evaluator == null) {
            throw new IllegalStateException("evaluator-agent not registered in AgentRegistry");
        }

        if (context.getFeedbacks() == null) {
            context.setFeedbacks(new ArrayList<>());
        }

        String lastOutput = null;

        for (int round = 0; round < maxRounds; round++) {
            int roundNumber = round + 1;
            log.info("双 Agent 博弈第 {} 轮开始 (maxRounds={})", roundNumber, maxRounds);

            // --- Generator 执行 ---
            String genPrompt = buildGeneratorPrompt(task, context, round);
            UnifiedRequest genRequest = UnifiedRequest.builder()
                    .input(genPrompt)
                    .context(context)
                    .build();
            UnifiedResponse genResponse = generator.invoke(genRequest);
            lastOutput = genResponse.getContent();
            log.debug("Generator 第 {} 轮输出长度: {}", roundNumber,
                    lastOutput != null ? lastOutput.length() : 0);

            // --- Evaluator 独立评估（仅基于 Generator 输出 + 原始任务） ---
            String evalPrompt = buildEvalPrompt(lastOutput, task);
            UnifiedRequest evalRequest = UnifiedRequest.builder()
                    .input(evalPrompt)
                    .context(context)
                    .build();
            UnifiedResponse evalResponse = evaluator.invoke(evalRequest);
            String evaluation = evalResponse.getContent();

            if (evaluation != null && evaluation.contains(PASS_KEYWORD)) {
                log.info("双 Agent 博弈第 {} 轮 Evaluator 评估 PASS", roundNumber);
                return DualAgentResult.passed(lastOutput, evaluation, roundNumber);
            }

            // 未通过 — 将评估反馈注入下一轮 context
            log.info("双 Agent 博弈第 {} 轮 Evaluator 评估未通过", roundNumber);
            context.getFeedbacks().add(evaluation);
        }

        log.warn("双 Agent 博弈达到最大轮数 {} 仍未通过", maxRounds);
        return DualAgentResult.maxRoundsReached(lastOutput, maxRounds);
    }

    /**
     * 构建 Generator 的 prompt。首轮仅包含任务，后续轮包含上一轮评估反馈。
     */
    String buildGeneratorPrompt(String task, SharedContext context, int round) {
        if (round == 0) {
            return task;
        }
        String lastFeedback = context.getFeedbacks().get(context.getFeedbacks().size() - 1);
        return task + "\n上一轮评估反馈：\n" + lastFeedback;
    }

    /**
     * 构建 Evaluator 的 prompt — 仅包含 Generator 输出和原始任务要求，
     * 不包含 Generator 内部状态，确保独立评估。
     */
    String buildEvalPrompt(String generatorOutput, String originalTask) {
        return "请从以下三个维度对输出进行评分（1-10 分），所有维度 ≥ 8 分时输出 PASS：\n"
                + "1. 正确性\n2. 完整性\n3. 质量\n\n"
                + "原始任务要求：\n" + originalTask + "\n\n"
                + "Generator 输出：\n" + generatorOutput;
    }
}
