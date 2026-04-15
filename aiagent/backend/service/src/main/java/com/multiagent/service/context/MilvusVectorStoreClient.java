package com.multiagent.service.context;

import com.multiagent.infrastructure.model.MemoryFragment;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Milvus 向量库客户端 — 使用 Spring AI EmbeddingClient 生成向量，
 * 通过 Milvus SDK 进行存储和相似度检索。
 */
@Component
@Primary
public class MilvusVectorStoreClient implements VectorStoreClient {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStoreClient.class);

    private final EmbeddingClient embeddingClient;
    private final MilvusServiceClient milvusClient;

    @Value("${vector.milvus.collection-name:knowledge_fragments}")
    private String collectionName;

    @Value("${vector.milvus.dimension:1536}")
    private int dimension;

    private static final String FIELD_ID = "id";
    private static final String FIELD_SESSION_ID = "session_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_EMBEDDING = "embedding";

    private boolean available = false;

    public MilvusVectorStoreClient(EmbeddingClient embeddingClient,
                                   MilvusServiceClient milvusClient) {
        this.embeddingClient = embeddingClient;
        this.milvusClient = milvusClient;
    }

    @PostConstruct
    void initCollection() {
        try {
            R<Boolean> has = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName).build());

            if (has.getData() != null && !has.getData()) {
                createCollection();
                createIndex();
                log.info("Milvus collection '{}' created (dim={})", collectionName, dimension);
            } else {
                log.info("Milvus collection '{}' exists", collectionName);
            }

            milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(collectionName).build());
            available = true;
        } catch (Exception e) {
            log.warn("Milvus init failed, falling back to no-op: {}", e.getMessage());
        }
    }

    private void createCollection() {
        milvusClient.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withShardsNum(1)
                .addFieldType(FieldType.newBuilder().withName(FIELD_ID)
                        .withDataType(DataType.VarChar).withMaxLength(64)
                        .withPrimaryKey(true).withAutoID(false).build())
                .addFieldType(FieldType.newBuilder().withName(FIELD_SESSION_ID)
                        .withDataType(DataType.VarChar).withMaxLength(128).build())
                .addFieldType(FieldType.newBuilder().withName(FIELD_CONTENT)
                        .withDataType(DataType.VarChar).withMaxLength(8192).build())
                .addFieldType(FieldType.newBuilder().withName(FIELD_EMBEDDING)
                        .withDataType(DataType.FloatVector).withDimension(dimension).build())
                .build());
    }

    private void createIndex() {
        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_EMBEDDING)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build());
    }

    @Override
    public List<MemoryFragment> search(String query, int topK) {
        if (!available) return Collections.emptyList();
        try {
            List<Float> vec = embed(query);
            R<SearchResults> resp = milvusClient.search(SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.COSINE)
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(vec))
                    .withVectorFieldName(FIELD_EMBEDDING)
                    .withOutFields(Arrays.asList(FIELD_CONTENT, FIELD_SESSION_ID))
                    .withParams("{\"nprobe\":16}")
                    .build());

            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus search failed: {}", resp.getMessage());
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
            List<MemoryFragment> results = new ArrayList<>();
            if (wrapper.getRowRecords(0) != null) {
                for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                    SearchResultsWrapper.IDScore idScore = wrapper.getIDScore(0).get(i);
                    String content = (String) wrapper.getRowRecords(0).get(i).get(FIELD_CONTENT);
                    String sid = (String) wrapper.getRowRecords(0).get(i).get(FIELD_SESSION_ID);
                    results.add(MemoryFragment.builder()
                            .content(content).score(idScore.getScore())
                            .sourceSessionId(sid).build());
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Milvus search error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void store(String sessionId, String summary) {
        if (!available) return;
        try {
            List<Float> vec = embed(summary);
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            String content = summary.length() > 8000 ? summary.substring(0, 8000) : summary;

            R<MutationResult> resp = milvusClient.insert(InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(Arrays.asList(
                            new InsertParam.Field(FIELD_ID, Collections.singletonList(id)),
                            new InsertParam.Field(FIELD_SESSION_ID, Collections.singletonList(sessionId)),
                            new InsertParam.Field(FIELD_CONTENT, Collections.singletonList(content)),
                            new InsertParam.Field(FIELD_EMBEDDING, Collections.singletonList(vec))))
                    .build());

            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus insert failed: {}", resp.getMessage());
            } else {
                log.debug("Stored memory for session {}", sessionId);
            }
        } catch (Exception e) {
            log.warn("Milvus store error: {}", e.getMessage());
        }
    }

    private List<Float> embed(String text) {
        List<Double> doubles = embeddingClient.embed(text);
        List<Float> floats = new ArrayList<>(doubles.size());
        for (Double d : doubles) floats.add(d.floatValue());
        return floats;
    }
}
