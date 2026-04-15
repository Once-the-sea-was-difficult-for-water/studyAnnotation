package com.multiagent.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Agent 自动配置 — 将 Spring AI 的 ChatClient 注册为默认 LLM Agent。
 */
@Configuration
public class AgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentAutoConfiguration.class);

    private final AgentRegistry agentRegistry;
    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public AgentAutoConfiguration(AgentRegistry agentRegistry,
                                  ChatClient chatClient,
                                  StreamingChatClient streamingChatClient) {
        this.agentRegistry = agentRegistry;
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @PostConstruct
    void registerDefaultAgents() {
        // 注册默认 LLM Agent
        LLMAgentAdapter defaultAgent = new LLMAgentAdapter("default-agent", chatClient, streamingChatClient);
        agentRegistry.register(defaultAgent);

        // 注册 generic-agent（降级链兜底）
        LLMAgentAdapter genericAgent = new LLMAgentAdapter("generic-agent", chatClient, streamingChatClient);
        agentRegistry.register(genericAgent);

        log.info("Registered default-agent and generic-agent via Spring AI ChatClient");
    }
}
