package com.multiagent.service.auth;

import com.multiagent.infrastructure.model.RiskLevel;
import com.multiagent.infrastructure.model.SharedContext;
import org.springframework.stereotype.Service;

/**
 * 默认分级授权网关实现 — 按照 L1-L4 风险等级执行分级授权。
 *
 * <ul>
 *   <li>L1 只读操作：自动放行</li>
 *   <li>L2 低危操作：自动执行 + 审计日志 + 事后通知</li>
 *   <li>L3 中危操作：创建审批工单，等待人工审批</li>
 *   <li>L4 高危操作：沙箱预演 + 多人审批</li>
 * </ul>
 */
@Service
public class DefaultAuthorizationGate implements AuthorizationGate {

    private final OperationClassifier operationClassifier;
    private final AuditLogger auditLogger;
    private final ApprovalService approvalService;
    private final SandboxExecutor sandboxExecutor;

    public DefaultAuthorizationGate(OperationClassifier operationClassifier,
                                    AuditLogger auditLogger,
                                    ApprovalService approvalService,
                                    SandboxExecutor sandboxExecutor) {
        this.operationClassifier = operationClassifier;
        this.auditLogger = auditLogger;
        this.approvalService = approvalService;
        this.sandboxExecutor = sandboxExecutor;
    }

    @Override
    public AuthResult authorize(ToolCallRequest request, SharedContext context) {
        RiskLevel level = operationClassifier.classify(request);

        // 所有级别都记录审计日志
        auditLogger.log(request, context, "AUTHORIZE_START:" + level.name());

        AuthResult result = switch (level) {
            case L1_READ_ONLY -> handleL1(request, context);
            case L2_LOW_RISK -> handleL2(request, context);
            case L3_MEDIUM_RISK -> handleL3(request, context);
            case L4_HIGH_RISK -> handleL4(request, context);
        };

        // 记录最终授权结果
        String action = result.isApproved() ? "APPROVED" : "DENIED";
        auditLogger.log(request, context, action + ":" + level.name());

        return result;
    }

    private AuthResult handleL1(ToolCallRequest request, SharedContext context) {
        return AuthResult.approved("只读操作，自动放行");
    }

    private AuthResult handleL2(ToolCallRequest request, SharedContext context) {
        auditLogger.log(request, context, "AUTO_APPROVED");
        auditLogger.notifyOps(request, context);
        return AuthResult.approved("低危操作，自动执行");
    }

    private AuthResult handleL3(ToolCallRequest request, SharedContext context) {
        auditLogger.log(request, context, "PENDING_APPROVAL");
        ApprovalService.ApprovalTicket ticket =
                approvalService.createAndWait(request, context, 1);

        if (ticket.isApproved()) {
            return AuthResult.approved("人工审批通过: " + ticket.approver());
        } else {
            return AuthResult.denied("人工审批拒绝: " + ticket.reason());
        }
    }

    private AuthResult handleL4(ToolCallRequest request, SharedContext context) {
        // 先沙箱预演
        auditLogger.log(request, context, "SANDBOX_DRY_RUN");
        SandboxExecutor.SandboxResult sandboxResult = sandboxExecutor.dryRun(request);

        if (!sandboxResult.isSafe()) {
            return AuthResult.denied("沙箱预演失败: " + sandboxResult.reason());
        }

        // 沙箱通过后提交多人审批（需要 2 人）
        auditLogger.log(request, context, "PENDING_MULTI_APPROVAL");
        ApprovalService.ApprovalTicket ticket =
                approvalService.createAndWait(request, context, 2);

        if (ticket.isApproved()) {
            return AuthResult.approved("沙箱通过 + 多人审批通过");
        } else {
            return AuthResult.denied("审批拒绝: " + ticket.reason());
        }
    }
}
