package com.multiagent.infrastructure.model;

/**
 * 操作风险等级枚举 — 分级授权网关使用。
 */
public enum RiskLevel {
    /** 只读操作，自动放行 */
    L1_READ_ONLY,
    /** 低危操作，自动执行 + 事后通知 */
    L2_LOW_RISK,
    /** 中危操作，需人工审批 */
    L3_MEDIUM_RISK,
    /** 高危操作，沙箱预演 + 多人审批 */
    L4_HIGH_RISK
}
