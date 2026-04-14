package com.multiagent.service.rag;

import com.multiagent.infrastructure.model.MemoryFragment;
import com.multiagent.service.context.VectorStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识库服务默认实现。
 * <p>
 * 检索流程：向量粗筛 Top-N → 时间衰减 → ReRank 精排 → 去重 → 截断至 maxFragments。
 * 写入流程：PAAL Learn 阶段通过 ingest() 自动写入处置经验。
 */
@Service
public class DefaultRAGKnowledgeService implements RAGKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRAGKnowledgeService.class);

    private final VectorStoreClient vectorStoreClient;

    public DefaultRAGKnowledgeService(VectorStoreClient vectorStoreClient) {
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public List<KnowledgeFragment> retrieve(String query, RAGConfig config) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // Step 1: 向量检索粗筛 Top-N 候选
        List<MemoryFragment> candidates;
        try {
            candidates = vectorStoreClient.search(query, config.getTopK());
        } catch (Exception e) {
            log.warn("向量库检索异常，降级为空结果: {}", e.getMessage());
            return Collections.emptyList();
        }

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: 转换为 KnowledgeFragment 并应用时间衰减因子
        List<KnowledgeFragment> fragments = candidates.stream()
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .map(m -> KnowledgeFragment.builder()
                        .id(m.getSourceSessionId())
                        .content(m.getContent())
                        .score(applyTimeDecay(m.getScore()))
                        .build())
                .collect(Collectors.toList());

        // Step 3: ReRank 精排（stub：按 score 降序排列）
        fragments = rerank(fragments);

        // Step 4: 去重（按 content 去重，保留 score 最高的）
        fragments = deduplicate(fragments);

        // Step 5: 截断至 maxFragments，按 score 降序返回
        return fragments.stream()
                .sorted(Comparator.comparingDouble(KnowledgeFragment::getScore).reversed())
                .limit(config.getMaxFragments())
                .collect(Collectors.toList());
    }

    @Override
    public void ingest(KnowledgeEntry entry) {
        if (entry == null || entry.getDescription() == null || entry.getDescription().isBlank()) {
            log.warn("知识条目为空或缺少描述，跳过写入");
            return;
        }

        String summary = buildSummary(entry);
        String sessionId = UUID.randomUUID().toString();

        try {
            vectorStoreClient.store(sessionId, summary);
            log.info("知识条目已写入向量库: {}", entry.getDescription());
        } catch (Exception e) {
            log.warn("知识条目写入向量库失败: {}", e.getMessage());
        }
    }

    /**
     * 时间衰减因子 — stub 实现，直接返回原始 score。
     * 后续可根据知识条目的时间戳计算衰减权重。
     */
    double applyTimeDecay(double score) {
        // TODO: 集成时间戳后实现真正的衰减公式，如 score * exp(-lambda * daysSinceCreation)
        return score;
    }

    /**
     * ReRank 精排 — stub 实现，按 score 降序排列。
     * 后续可集成交叉编码器模型进行精排。
     */
    List<KnowledgeFragment> rerank(List<KnowledgeFragment> fragments) {
        // TODO: 集成交叉编码器（如 bge-reranker）进行精排
        fragments.sort(Comparator.comparingDouble(KnowledgeFragment::getScore).reversed());
        return fragments;
    }

    /**
     * 按 content 去重，保留 score 最高的片段。
     */
    List<KnowledgeFragment> deduplicate(List<KnowledgeFragment> fragments) {
        Map<String, KnowledgeFragment> seen = new LinkedHashMap<>();
        for (KnowledgeFragment f : fragments) {
            seen.merge(f.getContent(), f, (existing, incoming) ->
                    incoming.getScore() > existing.getScore() ? incoming : existing);
        }
        return new ArrayList<>(seen.values());
    }

    private String buildSummary(KnowledgeEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务: ").append(entry.getDescription());
        if (entry.getPlan() != null) {
            sb.append("\n计划: ").append(entry.getPlan());
        }
        if (entry.getResult() != null) {
            sb.append("\n结果: ").append(entry.getResult());
        }
        sb.append("\n状态: ").append(entry.isSuccess() ? "成功" : "失败");
        return sb.toString();
    }
}
