package com.multiagent.service.paal;

import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.service.rag.KnowledgeEntry;
import com.multiagent.service.rag.KnowledgeFragment;
import com.multiagent.service.rag.RAGConfig;
import com.multiagent.service.rag.RAGKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PAAL 循环引擎默认实现 — 严格按 Plan → Act → Assess → Learn 顺序执行。
 * <p>
 * 循环逻辑：
 * <ol>
 *   <li>Plan: 通过 RAG 检索历史案例，分解任务制定执行计划</li>
 *   <li>Act: 调用 SkillExecutor/AgentAdapter 执行操作</li>
 *   <li>Assess: 独立评估执行效果，判断是否达标</li>
 *   <li>若 Assess 未通过，将反馈注入下一轮 context，继续循环</li>
 *   <li>Learn: 无论成功失败，将处置过程记录到知识库</li>
 * </ol>
 */
@Service
public class DefaultPAALEngine implements PAALEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultPAALEngine.class);

    private final RAGKnowledgeService ragKnowledgeService;
    private final TaskPlanner taskPlanner;
    private final ResultAssessor resultAssessor;

    /** RAG 检索默认配置 */
    private static final RAGConfig DEFAULT_RAG_CONFIG = RAGConfig.builder()
            .topK(10)
            .maxFragments(5)
            .build();

    public DefaultPAALEngine(RAGKnowledgeService ragKnowledgeService,
                             TaskPlanner taskPlanner,
                             ResultAssessor resultAssessor) {
        this.ragKnowledgeService = ragKnowledgeService;
        this.taskPlanner = taskPlanner;
        this.resultAssessor = resultAssessor;
    }

    @Override
    public PAALResult run(PAALInput input, SharedContext context, int maxIterations) {
        if (input == null) {
            throw new IllegalArgumentException("PAALInput must not be null");
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be > 0");
        }

        // 确保 feedbacks 列表已初始化
        if (context.getFeedbacks() == null) {
            context.setFeedbacks(new ArrayList<>());
        }

        PAALResult result = null;
        Object lastPlan = null;

        for (int i = 0; i < maxIterations; i++) {
            int iteration = i + 1;
            log.info("PAAL 循环第 {} 轮开始 (maxIterations={})", iteration, maxIterations);

            // --- Plan 阶段 ---
            log.debug("Plan 阶段: 检索历史案例并制定执行计划");
            List<KnowledgeFragment> historyCases = ragKnowledgeService.retrieve(
                    input.getDescription(), DEFAULT_RAG_CONFIG);
            injectKnowledge(context, historyCases);
            lastPlan = taskPlanner.plan(input, context);

            // --- Act 阶段 ---
            log.debug("Act 阶段: 执行计划");
            Object output = executePlan(lastPlan, context);

            // --- Assess 阶段 ---
            log.debug("Assess 阶段: 评估执行效果");
            Assessment assessment = resultAssessor.assess(output, input.getExpectation());

            if (assessment.isPassed()) {
                log.info("PAAL 循环第 {} 轮 Assess 通过", iteration);
                result = PAALResult.success(output, assessment, iteration);
                break;
            }

            // 未通过 — 将反馈注入下一轮 context
            log.info("PAAL 循环第 {} 轮 Assess 未通过, feedback: {}", iteration, assessment.getFeedback());
            context.getFeedbacks().add(assessment.getFeedback());
        }

        // --- Learn 阶段 — 无论成功失败都执行 ---
        log.debug("Learn 阶段: 记录处置过程到知识库");
        learn(input, lastPlan, result);

        if (result == null) {
            log.warn("PAAL 循环达到最大迭代次数 {} 仍未通过评估", maxIterations);
            return PAALResult.failed("超过最大迭代次数仍未通过评估", maxIterations);
        }

        return result;
    }

    /**
     * 将 RAG 检索到的历史案例注入上下文。
     */
    private void injectKnowledge(SharedContext context, List<KnowledgeFragment> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return;
        }
        if (context.getIntermediateResults() == null) {
            context.setIntermediateResults(new ArrayList<>());
        }
        for (KnowledgeFragment fragment : fragments) {
            context.getIntermediateResults().add(fragment);
        }
    }

    /**
     * Act 阶段 — 执行计划。
     * <p>
     * 当前为简单委托实现，后续可集成 SkillExecutor / AgentAdapter 执行 DAG。
     * plan 对象本身即为执行结果的占位（由 TaskPlanner 实现决定具体执行逻辑）。
     */
    private Object executePlan(Object plan, SharedContext context) {
        // TaskPlanner.plan() 返回的 plan 对象包含执行逻辑，
        // 实际执行由 TaskPlanner 实现内部完成（如调用 SkillExecutor/AgentAdapter）。
        // 此处返回 plan 作为 Act 阶段的输出。
        return plan;
    }

    /**
     * Learn 阶段 — 将处置过程记录到知识库。
     */
    private void learn(PAALInput input, Object plan, PAALResult result) {
        try {
            KnowledgeEntry entry = KnowledgeEntry.builder()
                    .description(input.getDescription())
                    .plan(plan)
                    .result(result)
                    .success(result != null && result.isSuccess())
                    .build();
            ragKnowledgeService.ingest(entry);
        } catch (Exception e) {
            log.error("Learn 阶段写入知识库失败: {}", e.getMessage(), e);
        }
    }
}
