package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆片段 — 从向量库检索的长期记忆条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryFragment {

    /** 片段内容 */
    private String content;

    /** 相关度分数 */
    private double score;

    /** 来源会话 ID */
    private String sourceSessionId;
}
