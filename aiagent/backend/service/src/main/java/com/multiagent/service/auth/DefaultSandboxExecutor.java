package com.multiagent.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认沙箱执行器 — 始终返回安全，后续接入实际沙箱环境。
 */
@Component
public class DefaultSandboxExecutor implements SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultSandboxExecutor.class);

    @Override
    public SandboxResult dryRun(ToolCallRequest request) {
        log.warn("DefaultSandboxExecutor: dry-run for tool '{}', returning safe", request.getToolName());
        return new SandboxResult(true, "默认沙箱通过");
    }
}
