package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一请求 — Agent 适配层的标准化输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedRequest {

    /** 用户输入内容 */
    private String input;

    /** 共享上下文 */
    private SharedContext context;

    /** 可用工具列表 */
    private List<ToolDefinition> tools;
}
