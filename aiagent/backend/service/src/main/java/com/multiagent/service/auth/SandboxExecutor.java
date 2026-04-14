package com.multiagent.service.auth;

/**
 * 沙箱执行器接口 — 在沙箱环境中预演高危操作。
 */
public interface SandboxExecutor {

    /**
     * 在沙箱环境中执行干跑（dry-run）。
     *
     * @param request 工具调用请求
     * @return 沙箱执行结果
     */
    SandboxResult dryRun(ToolCallRequest request);

    /**
     * 沙箱执行结果。
     */
    record SandboxResult(boolean safe, String reason) {

        public boolean isSafe() {
            return safe;
        }
    }
}
