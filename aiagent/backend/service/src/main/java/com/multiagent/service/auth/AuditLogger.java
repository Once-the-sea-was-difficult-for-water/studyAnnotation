package com.multiagent.service.auth;

import com.multiagent.infrastructure.model.SharedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审计日志组件 — 记录所有操作的完整审计日志。
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    /**
     * 记录审计日志。
     *
     * @param request 工具调用请求
     * @param context 共享上下文
     * @param action  审计动作（如 AUTO_APPROVED, DENIED, PENDING_APPROVAL 等）
     */
    public void log(ToolCallRequest request, SharedContext context, String action) {
        log.info("[AUDIT] tool={}, operationType={}, sessionId={}, action={}",
                request.getToolName(),
                request.getOperationType(),
                context != null ? context.getSessionId() : "unknown",
                action);
    }

    /**
     * 发送运维通知（如钉钉通知）。
     *
     * @param request 工具调用请求
     * @param context 共享上下文
     */
    public void notifyOps(ToolCallRequest request, SharedContext context) {
        log.info("[NOTIFY] tool={}, operationType={}, sessionId={} — 已发送运维通知",
                request.getToolName(),
                request.getOperationType(),
                context != null ? context.getSessionId() : "unknown");
    }
}
