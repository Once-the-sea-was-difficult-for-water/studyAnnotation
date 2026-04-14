package com.multiagent.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识片段 — RAG 检索返回的单条知识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFragment {

    /** 片段 ID */
    private String id;

    /** 片段内容 */
    private String content;

    /** 相关度得分 */
    private double score;
}
