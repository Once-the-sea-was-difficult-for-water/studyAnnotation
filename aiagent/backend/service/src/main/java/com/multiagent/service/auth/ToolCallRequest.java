package com.multiagent.service.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用请求 — 封装 Agent 发起的工具调用信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRequest {

    /** 工具名称 */
    private String toolName;

    /** 调用参数 */
    private Map<String, Object> parameters;

    /** 操作类型（如 query, restart, delete 等） */
    private String operationType;
}
