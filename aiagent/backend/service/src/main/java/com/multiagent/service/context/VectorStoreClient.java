package com.multiagent.service.context;

import com.multiagent.infrastructure.model.MemoryFragment;

import java.util.List;

/**
 * 向量库客户端接口 — 抽象向量存储的检索和写入操作。
 * 实际实现（如 Milvus/Qdrant）由基础设施层提供。
 */
public interface VectorStoreClient {

    /**
     * 根据查询文本检索相关记忆片段。
     *
     * @param query 查询文本
     * @param topK  返回数量上限
     * @return 按相关度降序排列的记忆片段列表
     */
    List<MemoryFragment> search(String query, int topK);

    /**
     * 将摘要内容存入向量库。
     *
     * @param sessionId 会话 ID
     * @param summary   摘要内容
     */
    void store(String sessionId, String summary);
}
