package com.multiagent.service.intent;

import com.multiagent.infrastructure.model.ConversationContext;
import com.multiagent.infrastructure.model.RoutingDecision;

/**
 * 意图识别器接口 — 解析用户输入，识别交互模式并返回路由决策。
 *
 * <p>支持三种输入模式：
 * <ul>
 *   <li>{@code @agent-name query} — 直接路由到指定 Agent (DIRECT_AGENT)</li>
 *   <li>{@code /skill-name param1=value1 param2=value2} — 触发指定 Skill (SKILL)</li>
 *   <li>自由文本 — LLM 分类 + 关键词匹配智能路由 (SKILL 或 AGENT)</li>
 * </ul>
 *
 * <p>实现类必须保证 {@link #recognize} 方法不修改传入的 {@link ConversationContext} 状态。
 */
public interface IntentRecognizer {

    /**
     * 识别用户输入意图并返回路由决策。
     *
     * @param input   用户输入文本，非空且非空白
     * @param context 会话上下文，非空，包含有效的会话 ID
     * @return 非空的 {@link RoutingDecision}，mode 字段为 DIRECT_AGENT、SKILL 或 AGENT 之一
     * @throws IllegalArgumentException 如果 input 为空/空白或 context 为空
     */
    RoutingDecision recognize(String input, ConversationContext context);
}
