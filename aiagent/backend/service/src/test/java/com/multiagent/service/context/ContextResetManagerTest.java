package com.multiagent.service.context;

import com.multiagent.infrastructure.entity.Message;
import com.multiagent.infrastructure.model.MessageRole;
import com.multiagent.infrastructure.model.SharedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ContextResetManagerTest {

    private ContextResetManager resetManager;

    @BeforeEach
    void setUp() {
        resetManager = new ContextResetManager();
    }

    // --- estimateTokenCount tests ---

    @Test
    void estimateTokenCount_nullContext_returnsZero() {
        assertEquals(0, resetManager.estimateTokenCount(null));
    }

    @Test
    void estimateTokenCount_emptyMessages_returnsZero() {
        SharedContext ctx = SharedContext.builder()
                .messages(Collections.emptyList())
                .build();
        assertEquals(0, resetManager.estimateTokenCount(ctx));
    }

    @Test
    void estimateTokenCount_nullMessages_returnsZero() {
        SharedContext ctx = SharedContext.builder()
                .messages(null)
                .build();
        assertEquals(0, resetManager.estimateTokenCount(ctx));
    }

    @Test
    void estimateTokenCount_singleMessage_dividesByFour() {
        // 20 chars -> 5 tokens
        Message msg = Message.builder()
                .content("12345678901234567890")
                .build();
        SharedContext ctx = SharedContext.builder()
                .messages(List.of(msg))
                .build();
        assertEquals(5, resetManager.estimateTokenCount(ctx));
    }

    @Test
    void estimateTokenCount_multipleMessages_sumsAll() {
        // 8 chars + 12 chars = 20 chars -> 5 tokens
        Message m1 = Message.builder().content("12345678").build();
        Message m2 = Message.builder().content("123456789012").build();
        SharedContext ctx = SharedContext.builder()
                .messages(List.of(m1, m2))
                .build();
        assertEquals(5, resetManager.estimateTokenCount(ctx));
    }

    @Test
    void estimateTokenCount_nullContent_skipped() {
        Message m1 = Message.builder().content(null).build();
        Message m2 = Message.builder().content("1234").build();
        SharedContext ctx = SharedContext.builder()
                .messages(List.of(m1, m2))
                .build();
        assertEquals(1, resetManager.estimateTokenCount(ctx));
    }

    // --- checkAndResetIfNeeded tests ---

    @Test
    void checkAndResetIfNeeded_nullContext_returnsNull() {
        assertNull(resetManager.checkAndResetIfNeeded(null));
    }

    @Test
    void checkAndResetIfNeeded_belowThreshold_returnsSameContext() {
        SharedContext ctx = SharedContext.builder()
                .sessionId("session-1")
                .systemPrompt("You are helpful.")
                .messages(List.of(Message.builder().content("hello").build()))
                .build();

        SharedContext result = resetManager.checkAndResetIfNeeded(ctx);
        assertSame(ctx, result);
    }

    @Test
    void checkAndResetIfNeeded_aboveThreshold_resetsContext() {
        // Create content that exceeds 100K tokens -> need 400K+ chars
        String bigContent = "x".repeat(200_000); // 200K chars = 50K tokens each
        Message m1 = Message.builder().content(bigContent).build();
        Message m2 = Message.builder().content(bigContent).build();
        Message m3 = Message.builder().content(bigContent).build(); // total 600K chars = 150K tokens

        SharedContext ctx = SharedContext.builder()
                .sessionId("session-abc")
                .systemPrompt("System prompt here")
                .messages(new ArrayList<>(List.of(m1, m2, m3)))
                .currentParams(Map.of("key", "value"))
                .feedbacks(List.of("old feedback"))
                .intermediateResults(List.of("old result"))
                .build();

        SharedContext result = resetManager.checkAndResetIfNeeded(ctx);

        // Should be a different object
        assertNotSame(ctx, result);
        // sessionId and systemPrompt preserved
        assertEquals("session-abc", result.getSessionId());
        assertEquals("System prompt here", result.getSystemPrompt());
        // currentParams preserved
        assertEquals(Map.of("key", "value"), result.getCurrentParams());
        // Messages should contain exactly 1 system summary message
        assertEquals(1, result.getMessages().size());
        Message summaryMsg = result.getMessages().get(0);
        assertEquals(MessageRole.SYSTEM, summaryMsg.getRole());
        assertTrue(summaryMsg.getContent().contains("你正在继续一个进行中的任务"));
        assertTrue(summaryMsg.getContent().contains("请基于此继续工作"));
        // Feedbacks and intermediateResults should be fresh/empty
        assertTrue(result.getFeedbacks().isEmpty());
        assertTrue(result.getIntermediateResults().isEmpty());
        // Long term memory should be empty
        assertTrue(result.getLongTermMemory().isEmpty());
    }

    @Test
    void checkAndResetIfNeeded_exactlyAtThreshold_returnsSameContext() {
        // 100K tokens = 400K chars exactly
        String content = "a".repeat(400_000);
        Message msg = Message.builder().content(content).build();

        SharedContext ctx = SharedContext.builder()
                .sessionId("s1")
                .systemPrompt("sp")
                .messages(List.of(msg))
                .build();

        // 400000 / 4 = 100000, which is NOT < 100000, so it should reset
        SharedContext result = resetManager.checkAndResetIfNeeded(ctx);
        assertNotSame(ctx, result);
    }

    @Test
    void checkAndResetIfNeeded_justBelowThreshold_returnsSameContext() {
        // 99999 tokens = 399996 chars
        String content = "a".repeat(399_996);
        Message msg = Message.builder().content(content).build();

        SharedContext ctx = SharedContext.builder()
                .sessionId("s1")
                .systemPrompt("sp")
                .messages(List.of(msg))
                .build();

        // 399996 / 4 = 99999, which IS < 100000
        SharedContext result = resetManager.checkAndResetIfNeeded(ctx);
        assertSame(ctx, result);
    }

    // --- generateArchiveSummary tests ---

    @Test
    void generateArchiveSummary_emptyMessages_returnsDefault() {
        SharedContext ctx = SharedContext.builder()
                .messages(Collections.emptyList())
                .build();
        assertEquals("无历史消息。", resetManager.generateArchiveSummary(ctx));
    }

    @Test
    void generateArchiveSummary_fewMessages_includesAll() {
        Message m1 = Message.builder().role(MessageRole.USER).content("Hello").build();
        Message m2 = Message.builder().role(MessageRole.ASSISTANT).content("Hi there").build();
        SharedContext ctx = SharedContext.builder()
                .messages(List.of(m1, m2))
                .build();

        String summary = resetManager.generateArchiveSummary(ctx);
        assertTrue(summary.contains("USER: Hello"));
        assertTrue(summary.contains("ASSISTANT: Hi there"));
    }

    @Test
    void generateArchiveSummary_longContent_truncatedTo300() {
        String longContent = "a".repeat(500);
        Message msg = Message.builder().role(MessageRole.USER).content(longContent).build();
        SharedContext ctx = SharedContext.builder()
                .messages(List.of(msg))
                .build();

        String summary = resetManager.generateArchiveSummary(ctx);
        assertTrue(summary.contains("..."));
        // Should contain at most 300 chars of content + role prefix + "..."
        assertTrue(summary.length() < 320);
    }

    @Test
    void generateArchiveSummary_moreThanFiveMessages_takesLastFive() {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(Message.builder()
                    .role(MessageRole.USER)
                    .content("msg-" + i)
                    .build());
        }
        SharedContext ctx = SharedContext.builder()
                .messages(messages)
                .build();

        String summary = resetManager.generateArchiveSummary(ctx);
        // Should NOT contain msg-0 through msg-4
        assertFalse(summary.contains("msg-0"));
        assertFalse(summary.contains("msg-4"));
        // Should contain msg-5 through msg-9
        assertTrue(summary.contains("msg-5"));
        assertTrue(summary.contains("msg-9"));
    }
}
