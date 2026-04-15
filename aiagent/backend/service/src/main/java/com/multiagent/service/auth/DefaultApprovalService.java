package com.multiagent.service.auth;

import com.multiagent.infrastructure.model.SharedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认审批服务 — 自动通过，后续接入实际审批流程。
 */
@Component
public class DefaultApprovalService implements ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DefaultApprovalService.class);

    @Override
    public ApprovalTicket createAndWait(ToolCallRequest request, SharedContext context, int requiredApprovers) {
        log.warn("DefaultApprovalService: auto-approving request for tool '{}'", request.getToolName());
        return new ApprovalTicket(true, "system-auto", "默认自动通过");
    }
}
