package com.multiagent.service.context;

import com.multiagent.infrastructure.model.MemoryFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的 VectorStoreClient 实现。
 * 作为 Milvus 不可用时的降级方案，数据不持久化。
 */
@Component
public class InMemoryVectorStoreClient implements VectorStoreClient {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStoreClient.class);

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public List<MemoryFragment> search(String query, int topK) {
        log.debug("InMemory vector search for: {}", query);
        return Collections.emptyList();
    }

    @Override
    public void store(String sessionId, String summary) {
        log.debug("InMemory vector store: sessionId={}", sessionId);
        store.put(sessionId, summary);
    }
}
