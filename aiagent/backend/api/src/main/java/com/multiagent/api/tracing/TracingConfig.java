package com.multiagent.api.tracing;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 追踪配置。
 * <p>
 * 利用 Spring Boot Actuator + micrometer-tracing-bridge-otel 自动配置，
 * 此类补充 @Observed 注解支持，使 WebSocket 消息处理和编排服务方法
 * 自动生成 span。
 * <p>
 * application.yml 中已配置:
 * - management.tracing.sampling.probability=1.0
 * - OTLP exporter 通过 opentelemetry-exporter-otlp 依赖自动注册
 */
@Configuration
@ConditionalOnClass(ObservationRegistry.class)
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    /**
     * 启用 @Observed 注解支持，允许在方法级别自动创建 observation/span。
     * 用于 ChatMessageHandler、OrchestrationService 等关键路径。
     */
    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    ObservedAspect observedAspect(ObservationRegistry registry) {
        log.info("OpenTelemetry tracing enabled with @Observed annotation support");
        return new ObservedAspect(registry);
    }
}
