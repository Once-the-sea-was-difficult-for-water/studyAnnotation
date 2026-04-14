package com.multiagent.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识条目 — 写入知识库的处置经验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeEntry {

    /** 任务描述 */
    private String description;

    /** 执行计划 */
    private Object plan;

    /** 执行结果 */
    private Object result;

    /** 是否成功 */
    private boolean success;
}
