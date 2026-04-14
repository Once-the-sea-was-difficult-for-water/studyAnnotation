package com.multiagent.service.auth;

import com.multiagent.infrastructure.model.SharedContext;

/**
 * 分级授权网关接口 — 在 Agent 执行操作前根据风险等级进行授权检查。
 */
public interface AuthorizationGate {

    /**
     * 对工具调用请求进行授权检查。
     *
     * @param request 工具调用请求
     * @param context 共享上下文（包含用户身份等信息）
     * @return 授权结果
     */
    AuthResult authorize(ToolCallRequest request, SharedContext context);
}
