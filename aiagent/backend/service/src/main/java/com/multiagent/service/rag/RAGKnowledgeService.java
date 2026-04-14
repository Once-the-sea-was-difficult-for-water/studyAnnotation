package com.multiagent.service.rag;

import java.util.List;

/**
 * RAG 知识库服务接口 — 提供知识检索和写入能力。
 * <p>
 * 支持向量检索 + ReRank + 时间衰减。
 * PAAL Learn 阶段通过 {@link #ingest(KnowledgeEntry)} 自动写入处置经验。
 */
public interface RAGKnowledgeService {

    /**
     * 检索与查询相关的知识片段。
     *
     * @param query  查询文本
     * @param config 检索配置
     * @return 按 score 降序排列的知识片段列表
     */
    List<KnowledgeFragment> retrieve(String query, RAGConfig config);

    /**
     * 写入知识条目到知识库。
     *
     * @param entry 知识条目
     */
    void ingest(KnowledgeEntry entry);
}
