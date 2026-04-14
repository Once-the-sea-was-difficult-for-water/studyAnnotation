package com.multiagent.infrastructure.model;

import com.multiagent.infrastructure.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 共享上下文 — 发送给 Agent 的上下文信息集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedContext {

    /** 会话 ID */
    private String sessionId;

    /** 系统提示词 */
    private String systemPrompt;

    /** 最近 N 轮对话消息 */
    private List<Message> messages;

    /** 从向量库检索的长期记忆 */
    private List<MemoryFragment> longTermMemory;

    /** 当前参数（Skill 执行参数等） */
    private Map<String, Object> currentParams;

    /** 评估反馈列表（PAAL / Harness 迭代反馈） */
    private List<String> feedbacks;

    /** 中间结果列表 */
    private List<Object> intermediateResults;
}
