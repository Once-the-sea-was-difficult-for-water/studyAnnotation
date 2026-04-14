package com.multiagent.service.hallucination;

import com.multiagent.service.rag.KnowledgeFragment;
import com.multiagent.service.rag.RAGConfig;
import com.multiagent.service.rag.RAGKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 幻觉治理守卫 — 三层防护中的验证层核心组件。
 * <p>
 * 生成层：RAG 注入相关知识减少幻觉（由调用方在生成前完成）。
 * 验证层：本组件检测 Agent 输出与知识库已知事实的矛盾。
 * 恢复层：检测到幻觉时，调用方可触发 {@code DualAgentExecutor} 重新生成或标记需人工复核。
 * <p>
 * 当前使用简单关键词矛盾检测（stub），后续可替换为 NLI 模型。
 */
@Service
public class HallucinationGuard {

    private static final Logger log = LoggerFactory.getLogger(HallucinationGuard.class);

    /** 检索知识片段时的默认配置 */
    private static final RAGConfig DEFAULT_RAG_CONFIG = RAGConfig.builder()
            .topK(10)
            .maxFragments(5)
            .build();

    private final RAGKnowledgeService ragKnowledgeService;

    public HallucinationGuard(RAGKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    /**
     * 检测 Agent 输出是否与知识库已知事实矛盾。
     *
     * @param agentOutput Agent 的输出文本
     * @param query       用户原始查询（用于检索相关知识）
     * @return 检测结果，包含状态和矛盾证据
     */
    public HallucinationCheckResult check(String agentOutput, String query) {
        if (agentOutput == null || agentOutput.isBlank()) {
            return HallucinationCheckResult.clean(agentOutput);
        }
        if (query == null || query.isBlank()) {
            return HallucinationCheckResult.clean(agentOutput);
        }

        // Step 1: 通过 RAG 检索与查询相关的知识片段
        List<KnowledgeFragment> fragments;
        try {
            fragments = ragKnowledgeService.retrieve(query, DEFAULT_RAG_CONFIG);
        } catch (Exception e) {
            log.warn("幻觉检测时知识库检索失败，降级为 CLEAN: {}", e.getMessage());
            return HallucinationCheckResult.clean(agentOutput);
        }

        if (fragments == null || fragments.isEmpty()) {
            // 无相关知识可比对，视为 CLEAN
            return HallucinationCheckResult.clean(agentOutput);
        }

        // Step 2: 检测输出与知识片段之间的矛盾
        List<String> evidence = detectContradictions(agentOutput, fragments);

        if (evidence.isEmpty()) {
            log.debug("幻觉检测通过，未发现矛盾");
            return HallucinationCheckResult.clean(agentOutput);
        }

        log.warn("幻觉检测发现 {} 条矛盾证据", evidence.size());
        return HallucinationCheckResult.flagged(agentOutput, evidence);
    }

    /**
     * 简单关键词矛盾检测 — stub 实现。
     * <p>
     * 检测逻辑：扫描 Agent 输出中的否定模式，若知识片段中包含被否定的事实则视为矛盾。
     * 后续可替换为 NLI（Natural Language Inference）模型进行语义级矛盾检测。
     */
    List<String> detectContradictions(String agentOutput, List<KnowledgeFragment> fragments) {
        List<String> contradictions = new ArrayList<>();
        String outputLower = agentOutput.toLowerCase();

        for (KnowledgeFragment fragment : fragments) {
            String knowledgeLower = fragment.getContent().toLowerCase();

            // 提取知识片段中的关键短语
            String[] knowledgePhrases = extractKeyPhrases(knowledgeLower);

            for (String phrase : knowledgePhrases) {
                if (phrase.isBlank()) {
                    continue;
                }
                // 检测否定矛盾：输出中否定了知识库中的已知事实
                if (containsNegation(outputLower, phrase)) {
                    contradictions.add("输出否定了已知事实: \"" + phrase.trim()
                            + "\" (来源: " + truncate(fragment.getContent(), 80) + ")");
                }
            }
        }

        return contradictions;
    }

    /**
     * 提取关键短语 — 按句号/换行分割。
     */
    private String[] extractKeyPhrases(String text) {
        return text.split("[。.\\n]+");
    }

    /**
     * 检测输出是否否定了给定短语中的关键词。
     * <p>
     * 简单策略：若输出中同时包含否定词和短语中的关键词，视为潜在矛盾。
     */
    private boolean containsNegation(String output, String phrase) {
        String[] negationPatterns = {"不是", "并非", "没有", "不存在", "不支持", "无法", "不能",
                "is not", "does not", "cannot", "never", "no longer"};

        // 提取短语中长度 >= 2 的关键词
        String[] words = phrase.trim().split("\\s+");
        for (String negation : negationPatterns) {
            if (!output.contains(negation)) {
                continue;
            }
            for (String word : words) {
                if (word.length() >= 2 && output.contains(word) && phrase.contains(word)
                        && !phrase.contains(negation)) {
                    // 输出中用否定词修饰了知识库中的肯定事实
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 截断文本用于日志/证据展示。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
