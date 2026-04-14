package com.multiagent.service.context;

import com.multiagent.infrastructure.entity.Conversation;
import com.multiagent.infrastructure.entity.Message;
import com.multiagent.infrastructure.model.*;
import com.multiagent.service.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultContextManagerTest {

    private SkillRegistry skillRegistry;
    private VectorStoreClient vectorStoreClient;
    private DefaultContextManager contextManager;

    @BeforeEach
    void setUp() {
        skillRegistry = mock(SkillRegistry.class);
        vectorStoreClient = mock(VectorStoreClient.class);
        contextManager = new DefaultContextManager(skillRegistry, vectorStoreClient);
    }

    // --- buildContext tests ---

    @Test
    void buildContext_withSkillRouting_usesSkillDescriptionAsSystemPrompt() {
        Skill skill = Skill.builder()
                .id("disk-diagnose")
                .description("Diagnose disk space issues")
                .build();
        when(skillRegistry.getById("disk-diagnose")).thenReturn(skill);

        Conversation conversation = buildConversation("s1", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.SKILL)
                .skillId("disk-diagnose")
                .query("check disk")
                .build();
        AgentManifest agent = buildAgent("agent1", "Default prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertEquals("Diagnose disk space issues", ctx.getSystemPrompt());
        assertEquals("s1", ctx.getSessionId());
    }

    @Test
    void buildContext_withoutSkillId_usesAgentDefaultPrompt() {
        Conversation conversation = buildConversation("s2", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query("hello")
                .build();
        AgentManifest agent = buildAgent("agent1", "Agent default prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertEquals("Agent default prompt", ctx.getSystemPrompt());
    }

    @Test
    void buildContext_truncatesMessagesToFitTokenBudget() {
        // Each message ~100 chars => ~25 tokens. With maxContextTokens=60, should fit ~2 messages.
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(Message.builder()
                    .id("m" + i)
                    .content("A".repeat(100)) // ~25 tokens each
                    .role(MessageRole.USER)
                    .timestamp(LocalDateTime.now().plusMinutes(i))
                    .build());
        }

        Conversation conversation = buildConversation("s3", messages);
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query("test")
                .build();
        AgentManifest agent = buildAgent("agent1", "prompt", 60);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        // 60 tokens budget, each message ~25 tokens => 2 messages fit
        assertEquals(2, ctx.getMessages().size());
        // Should be the most recent messages
        assertEquals("m3", ctx.getMessages().get(0).getId());
        assertEquals("m4", ctx.getMessages().get(1).getId());
    }

    @Test
    void buildContext_withEmptyMessages_returnsEmptyList() {
        Conversation conversation = buildConversation("s4", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query("test")
                .build();
        AgentManifest agent = buildAgent("agent1", "prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertTrue(ctx.getMessages().isEmpty());
    }

    @Test
    void buildContext_retrievesLongTermMemory() {
        List<MemoryFragment> memories = List.of(
                MemoryFragment.builder().content("past event").score(0.9).build()
        );
        when(vectorStoreClient.search("my query", 5)).thenReturn(memories);

        Conversation conversation = buildConversation("s5", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query("my query")
                .build();
        AgentManifest agent = buildAgent("agent1", "prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertEquals(1, ctx.getLongTermMemory().size());
        assertEquals("past event", ctx.getLongTermMemory().get(0).getContent());
    }

    @Test
    void buildContext_vectorStoreFailure_returnsEmptyMemory() {
        when(vectorStoreClient.search(anyString(), anyInt())).thenThrow(new RuntimeException("connection refused"));

        Conversation conversation = buildConversation("s6", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query("test")
                .build();
        AgentManifest agent = buildAgent("agent1", "prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertTrue(ctx.getLongTermMemory().isEmpty());
    }

    @Test
    void buildContext_setsCurrentParamsFromRouting() {
        Conversation conversation = buildConversation("s7", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.SKILL)
                .skillId("some-skill")
                .query("test")
                .params(Map.of("region", "cn-hangzhou"))
                .build();
        when(skillRegistry.getById("some-skill")).thenReturn(
                Skill.builder().id("some-skill").description("desc").build());
        AgentManifest agent = buildAgent("agent1", "prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertEquals("cn-hangzhou", ctx.getCurrentParams().get("region"));
    }

    @Test
    void buildContext_nullParams_setsEmptyMap() {
        Conversation conversation = buildConversation("s8", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query("test")
                .build();
        AgentManifest agent = buildAgent("agent1", "prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertNotNull(ctx.getCurrentParams());
        assertTrue(ctx.getCurrentParams().isEmpty());
    }

    @Test
    void buildContext_nullArguments_throwsNPE() {
        AgentManifest agent = buildAgent("a", "p", 100);
        RoutingDecision routing = RoutingDecision.builder().mode(RoutingMode.AGENT).build();
        Conversation conv = buildConversation("s", List.of());

        assertThrows(NullPointerException.class, () -> contextManager.buildContext(null, routing, agent));
        assertThrows(NullPointerException.class, () -> contextManager.buildContext(conv, null, agent));
        assertThrows(NullPointerException.class, () -> contextManager.buildContext(conv, routing, null));
    }

    @Test
    void buildContext_skillNotFound_fallsBackToAgentPrompt() {
        when(skillRegistry.getById("missing-skill")).thenReturn(null);

        Conversation conversation = buildConversation("s9", List.of());
        RoutingDecision routing = RoutingDecision.builder()
                .mode(RoutingMode.SKILL)
                .skillId("missing-skill")
                .query("test")
                .build();
        AgentManifest agent = buildAgent("agent1", "Fallback prompt", 4096);

        SharedContext ctx = contextManager.buildContext(conversation, routing, agent);

        assertEquals("Fallback prompt", ctx.getSystemPrompt());
    }

    // --- persist tests ---

    @Test
    void persist_storesSummaryToVectorStore() {
        List<Message> messages = List.of(
                Message.builder().role(MessageRole.USER).content("What is disk usage?").build()
        );
        Conversation conversation = buildConversation("s10", messages);

        contextManager.persist(conversation, "Disk usage is 80%");

        verify(vectorStoreClient).store(eq("s10"), anyString());
    }

    @Test
    void persist_emptyMessages_doesNotStore() {
        Conversation conversation = buildConversation("s11", List.of());

        contextManager.persist(conversation, "result");

        verify(vectorStoreClient, never()).store(anyString(), anyString());
    }

    @Test
    void persist_vectorStoreFailure_doesNotThrow() {
        List<Message> messages = List.of(
                Message.builder().role(MessageRole.USER).content("test").build()
        );
        Conversation conversation = buildConversation("s12", messages);
        doThrow(new RuntimeException("store failed")).when(vectorStoreClient).store(anyString(), anyString());

        assertDoesNotThrow(() -> contextManager.persist(conversation, "result"));
    }

    // --- estimateTokens tests ---

    @Test
    void estimateTokens_nullOrEmpty_returnsZero() {
        assertEquals(0, contextManager.estimateTokens(null));
        assertEquals(0, contextManager.estimateTokens(""));
    }

    @Test
    void estimateTokens_shortText_returnsAtLeastOne() {
        assertEquals(1, contextManager.estimateTokens("Hi"));
    }

    @Test
    void estimateTokens_approximation() {
        // 100 chars / 4 = 25 tokens
        assertEquals(25, contextManager.estimateTokens("A".repeat(100)));
    }

    // --- resolveMaxContextTokens tests ---

    @Test
    void resolveMaxContextTokens_usesConfigValue() {
        AgentManifest agent = buildAgent("a", "p", 8192);
        assertEquals(8192, contextManager.resolveMaxContextTokens(agent));
    }

    @Test
    void resolveMaxContextTokens_zeroConfig_usesDefault() {
        AgentManifest agent = buildAgent("a", "p", 0);
        assertEquals(4096, contextManager.resolveMaxContextTokens(agent));
    }

    // --- helpers ---

    private Conversation buildConversation(String id, List<Message> messages) {
        return Conversation.builder()
                .id(id)
                .userId("user1")
                .messages(messages)
                .build();
    }

    private AgentManifest buildAgent(String id, String systemPrompt, int maxContextTokens) {
        return AgentManifest.builder()
                .id(id)
                .config(AgentConfig.builder()
                        .systemPrompt(systemPrompt)
                        .maxContextTokens(maxContextTokens)
                        .build())
                .build();
    }
}
