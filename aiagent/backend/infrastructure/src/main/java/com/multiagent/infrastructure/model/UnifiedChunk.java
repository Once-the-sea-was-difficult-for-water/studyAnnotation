package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一流式块 — 流式响应中的单个数据块。
 * type 取值：content（内容）、progress（进度）、done（完成）、error（错误）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedChunk {

    /** 块类型：content, progress, done, error */
    private String type;

    /** 文本内容 */
    private String content;

    /** 结构化数据 */
    private Object data;
}
