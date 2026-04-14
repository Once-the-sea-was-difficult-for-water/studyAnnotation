package com.multiagent.service.context;

import com.multiagent.infrastructure.entity.Conversation;
import com.multiagent.infrastructure.model.AgentManifest;
import com.multiagent.infrastructure.model.RoutingDecision;
import com.multiagent.infrastructure.model.SharedContext;

/**
 * 上下文管理器接口 — 构建发送给 Agent 的上下文，管理会话历史、长期记忆和跨会话检索。
 */
public interface ContextManager {

    /**
     * 构建 Agent 上下文。
     * <p>
     * 截取最近 N 轮对话（不超过 maxContextTokens），注入 system prompt，
     * 并从向量库检索 Top-5 相关历史作为长期记忆。
     *
     * @param conversation 当前对话（含消息列表）
     * @param routing      路由决策
     * @param agent        目标 Agent 信息
     * @return 构建好的共享上下文
     */
    SharedContext buildContext(Conversation conversation, RoutingDecision routing, AgentManifest agent);

    /**
     * 持久化对话结果。
     * <p>
     * 对话结束后生成摘要并存入向量库，供后续跨会话检索。
     *
     * @param conversation 当前对话
     * @param result       执行结果
     */
    void persist(Conversation conversation, Object result);
}
