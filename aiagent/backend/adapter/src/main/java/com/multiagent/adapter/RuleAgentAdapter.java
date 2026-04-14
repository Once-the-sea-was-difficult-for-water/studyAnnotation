package com.multiagent.adapter;

import com.multiagent.infrastructure.model.AgentType;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.UnifiedChunk;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Rule Agent 适配器 — 基于规则引擎执行，不调用 LLM。
 * <p>
 * 维护一个 {@code Map<String, Function>} 规则表。invoke() 按规则名称匹配并执行，
 * stream() 将同步结果包装为单元素 Flux。
 */
public class RuleAgentAdapter extends AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(RuleAgentAdapter.class);

    private final String agentId;
    private final Map<String, Function<UnifiedRequest, String>> rules = new ConcurrentHashMap<>();

    public RuleAgentAdapter(String agentId) {
        this.agentId = agentId;
    }

    /**
     * 注册一条规则。
     *
     * @param ruleName 规则名称
     * @param rule     规则执行函数，接收 UnifiedRequest 返回结果字符串
     */
    public void registerRule(String ruleName, Function<UnifiedRequest, String> rule) {
        rules.put(ruleName, rule);
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.RULE;
    }

    @Override
    public UnifiedResponse invoke(UnifiedRequest request) {
        String input = request.getInput();
        // Try to match a rule by input content
        for (Map.Entry<String, Function<UnifiedRequest, String>> entry : rules.entrySet()) {
            if (input != null && input.contains(entry.getKey())) {
                String result = entry.getValue().apply(request);
                return fromNativeFormat(result);
            }
        }
        // Default: apply the first rule if available, otherwise return empty
        if (!rules.isEmpty()) {
            Function<UnifiedRequest, String> firstRule = rules.values().iterator().next();
            String result = firstRule.apply(request);
            return fromNativeFormat(result);
        }
        return UnifiedResponse.builder().content("No matching rule found").build();
    }

    @Override
    public Flux<UnifiedChunk> stream(UnifiedRequest request) {
        UnifiedResponse response = invoke(request);
        return Flux.just(
                UnifiedChunk.builder().type("content").content(response.getContent()).build(),
                UnifiedChunk.builder().type("done").build()
        );
    }

    @Override
    public void cancel(String taskId) {
        log.info("RuleAgentAdapter [{}]: cancel is a no-op for rule-based execution (taskId={})", agentId, taskId);
    }

    @Override
    protected Object toNativeFormat(SharedContext context) {
        // Rule agent doesn't need native format conversion
        return context;
    }

    @Override
    protected UnifiedResponse fromNativeFormat(Object nativeResponse) {
        if (nativeResponse instanceof String content) {
            return UnifiedResponse.builder().content(content).build();
        }
        return UnifiedResponse.builder().content("").build();
    }
}
