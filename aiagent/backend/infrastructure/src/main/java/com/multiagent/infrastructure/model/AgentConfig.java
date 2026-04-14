package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 运行配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    /** 模型名称 */
    private String model;

    /** 温度参数 */
    private Double temperature;

    /** 系统提示词 */
    private String systemPrompt;

    /** 可调用的 Tool ID 列表 */
    private List<String> tools;

    /** 最大并发数 */
    private int maxConcurrency;

    /** 超时时间（毫秒） */
    private int timeoutMs;

    /** 上下文最大 token 数 */
    private int maxContextTokens;
}
