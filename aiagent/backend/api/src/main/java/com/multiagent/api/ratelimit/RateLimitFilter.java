package com.multiagent.api.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 限流过滤器 — 基于滑动窗口的每用户请求限流。
 * <p>
 * 使用 ConcurrentHashMap + ConcurrentLinkedDeque 实现滑动窗口计数，
 * 防止单用户过度消耗 LLM 资源。超限时返回 HTTP 429。
 * <p>
 * 同时记录审计日志（所有请求）并对 L2+ 操作发送钉钉通知（通过日志标记）。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    /** 每用户的请求时间戳队列（滑动窗口） */
    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    /** 滑动窗口大小：1 分钟 */
    private static final long WINDOW_MS = 60_000L;

    @Value("${rate-limit.default-rpm:60}")
    private int defaultRpm;

    @Value("${rate-limit.llm-rpm:20}")
    private int llmRpm;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = resolveUserId(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 审计日志：记录所有请求
        auditLog.info("[AUDIT] userId={}, method={}, path={}, remoteAddr={}",
                userId, method, path, request.getRemoteAddr());

        // 对 LLM 相关路径使用更严格的限流
        int maxRpm = isLlmPath(path) ? llmRpm : defaultRpm;

        if (!tryAcquire(userId, maxRpm)) {
            log.warn("[RATE_LIMIT] 用户 {} 请求超限 (max={}/min), path={}", userId, maxRpm, path);
            auditLog.warn("[AUDIT][RATE_LIMITED] userId={}, path={}", userId, path);
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"请求过于频繁，请稍后重试\",\"retryAfterSeconds\":60}");
            return;
        }

        // L2+ 操作钉钉通知标记（通过审计日志触发）
        if (isL2PlusOperation(path, method)) {
            auditLog.info("[AUDIT][L2+_NOTIFY] userId={}, method={}, path={} — 触发钉钉通知",
                    userId, method, path);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 滑动窗口限流：尝试获取一个请求配额。
     *
     * @param userId 用户标识
     * @param maxRpm 每分钟最大请求数
     * @return true 如果允许请求
     */
    boolean tryAcquire(String userId, int maxRpm) {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        Deque<Long> timestamps = requestWindows.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

        // 清理过期时间戳
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRpm) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    /**
     * 从请求中解析用户 ID。
     */
    private String resolveUserId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return principal.getName();
        }
        // fallback: 使用 JWT header 中的用户信息或 IP
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return "token:" + authHeader.substring(7, Math.min(15, authHeader.length()));
        }
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * 判断是否为 LLM 相关路径（需要更严格限流）。
     */
    private boolean isLlmPath(String path) {
        return path != null && (path.startsWith("/ws") || path.contains("/chat") || path.contains("/agent"));
    }

    /**
     * 判断是否为 L2+ 操作（需要钉钉通知）。
     */
    private boolean isL2PlusOperation(String path, String method) {
        if (path == null) return false;
        // POST/PUT/DELETE 到敏感路径视为 L2+ 操作
        boolean isMutating = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
        boolean isSensitivePath = path.contains("/skill") || path.contains("/agent")
                || path.contains("/config") || path.contains("/admin");
        return isMutating && isSensitivePath;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 健康检查和静态资源不限流
        return path != null && (path.startsWith("/actuator") || path.startsWith("/api/auth"));
    }
}
