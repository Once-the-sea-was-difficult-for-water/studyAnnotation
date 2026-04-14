package com.multiagent.service.skill;

import java.util.Map;

/**
 * 工具执行器接口 — 执行 TOOL_CALL 步骤中指定的工具。
 * <p>
 * 具体实现将集成各类运维工具（DB 查询、指标采集、日志分析等）。
 */
public interface ToolRunner {

    /**
     * 执行指定工具。
     *
     * @param toolName 工具名称
     * @param input    工具输入参数
     * @return 工具执行结果
     */
    Object run(String toolName, Map<String, Object> input);
}
