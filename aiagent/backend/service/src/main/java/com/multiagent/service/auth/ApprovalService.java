package com.multiagent.service.auth;

import com.multiagent.infrastructure.model.SharedContext;

/**
 * 审批服务接口 — 创建审批工单并等待审批结果。
 */
public interface ApprovalService {

    /**
     * 创建审批工单并等待审批完成。
     *
     * @param request           工具调用请求
     * @param context           共享上下文
     * @param requiredApprovers 需要的审批人数
     * @return 审批工单结果
     */
    ApprovalTicket createAndWait(ToolCallRequest request, SharedContext context, int requiredApprovers);

    /**
     * 审批工单结果。
     */
    record ApprovalTicket(boolean approved, String approver, String reason) {

        public boolean isApproved() {
            return approved;
        }
    }
}
