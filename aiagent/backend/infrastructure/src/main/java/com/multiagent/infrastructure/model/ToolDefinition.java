package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具定义 — 描述 Agent 可调用的工具。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具参数 JSON Schema */
    private String parametersSchema;
}
