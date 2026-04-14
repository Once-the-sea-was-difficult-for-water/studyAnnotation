package com.multiagent.service.auth;

import com.multiagent.infrastructure.model.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 操作分类器 — 根据工具调用请求判定操作风险等级。
 */
@Component
public class OperationClassifier {

    private static final Set<String> READ_ONLY_OPS = Set.of(
            "query", "get", "list", "describe", "show", "read", "search", "check"
    );

    private static final Set<String> LOW_RISK_OPS = Set.of(
            "update-config", "scale-up", "modify-param", "enable", "disable"
    );

    private static final Set<String> MEDIUM_RISK_OPS = Set.of(
            "restart", "stop", "start", "failover", "switchover"
    );

    private static final Set<String> HIGH_RISK_OPS = Set.of(
            "delete", "drop", "destroy", "terminate", "format", "truncate"
    );

    /**
     * 根据 ToolCallRequest 分类操作风险等级。
     *
     * @param request 工具调用请求
     * @return 风险等级
     */
    public RiskLevel classify(ToolCallRequest request) {
        String opType = normalizeOperationType(request);

        if (HIGH_RISK_OPS.contains(opType)) {
            return RiskLevel.L4_HIGH_RISK;
        }
        if (MEDIUM_RISK_OPS.contains(opType)) {
            return RiskLevel.L3_MEDIUM_RISK;
        }
        if (LOW_RISK_OPS.contains(opType)) {
            return RiskLevel.L2_LOW_RISK;
        }
        // 默认视为只读
        return RiskLevel.L1_READ_ONLY;
    }

    private String normalizeOperationType(ToolCallRequest request) {
        String opType = request.getOperationType();
        if (opType == null || opType.isBlank()) {
            return "";
        }
        return opType.trim().toLowerCase();
    }
}
