package com.multiagent.service.tracing;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控组件 — 追踪首字延迟和向量检索延迟。
 * <p>
 * 使用 Micrometer 指标，通过 Actuator /metrics 端点暴露，
 * 可被 Prometheus 抓取并在 Grafana 中展示。
 * <p>
 * 目标 SLA:
 * - 首字延迟 (first byte latency) < 500ms
 * - 向量检索延迟 (vector search latency) < 100ms
 */
@Component
public class PerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);

    private static final long FIRST_BYTE_SLA_MS = 500;
    private static final long VECTOR_SEARCH_SLA_MS = 100;

    private final Timer firstByteTimer;
    private final Timer vectorSearchTimer;
    private final DistributionSummary totalLatencySummary;

    public PerformanceMonitor(MeterRegistry registry) {
        this.firstByteTimer = Timer.builder("agent.first_byte_latency")
                .description("Time to first byte in streaming response")
                .publishPercentiles(0.5, 0.95, 0.99)
                .sla(Duration.ofMillis(FIRST_BYTE_SLA_MS))
                .register(registry);

        this.vectorSearchTimer = Timer.builder("rag.vector_search_latency")
                .description("Vector retrieval latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .sla(Duration.ofMillis(VECTOR_SEARCH_SLA_MS))
                .register(registry);

        this.totalLatencySummary = DistributionSummary.builder("request.total_latency_ms")
                .description("Total request processing latency in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * 记录首字延迟（从请求开始到第一个 content chunk 推送的时间）。
     * 超过 500ms SLA 时记录 warn 日志。
     *
     * @param latencyMs 延迟毫秒数
     */
    public void recordFirstByteLatency(long latencyMs) {
        firstByteTimer.record(latencyMs, TimeUnit.MILLISECONDS);
        if (latencyMs > FIRST_BYTE_SLA_MS) {
            log.warn("首字延迟超过 SLA: {}ms (目标 < {}ms)", latencyMs, FIRST_BYTE_SLA_MS);
        }
    }

    /**
     * 记录向量检索延迟。
     * 超过 100ms SLA 时记录 warn 日志。
     *
     * @param latencyMs 延迟毫秒数
     */
    public void recordVectorSearchLatency(long latencyMs) {
        vectorSearchTimer.record(latencyMs, TimeUnit.MILLISECONDS);
        if (latencyMs > VECTOR_SEARCH_SLA_MS) {
            log.warn("向量检索延迟超过 SLA: {}ms (目标 < {}ms)", latencyMs, VECTOR_SEARCH_SLA_MS);
        }
    }

    /**
     * 记录请求总延迟。
     *
     * @param latencyMs 延迟毫秒数
     */
    public void recordTotalLatency(long latencyMs) {
        totalLatencySummary.record(latencyMs);
    }
}
