package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应 — Agent 适配层的标准化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedResponse {

    /** 响应文本内容 */
    private String content;

    /** 结构化数据（图表、表格等） */
    private Object data;

    /** Agent 发起的工具调用列表 */
    private List<ToolCall> toolCalls;

    /** Token 用量信息 */
    private UsageInfo usage;
}
