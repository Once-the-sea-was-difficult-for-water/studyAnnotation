package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用 — Agent 发起的工具调用请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 调用 ID */
    private String id;

    /** 工具名称 */
    private String toolName;

    /** 调用参数（JSON 字符串） */
    private String arguments;
}
