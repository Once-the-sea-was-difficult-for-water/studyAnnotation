package com.multiagent.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 检索配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGConfig {

    /** 粗筛 Top-N */
    @Builder.Default
    private int topK = 10;

    /** 最终返回的最大片段数 */
    @Builder.Default
    private int maxFragments = 5;
}
