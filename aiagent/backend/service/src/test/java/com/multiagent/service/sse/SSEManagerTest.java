package com.multiagent.service.sse;

import com.multiagent.infrastructure.model.UnifiedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SSEManagerTest {

    private SSEManager sseManager;

    @BeforeEach
    void setUp() {
        sseManager = new SSEManager();
    }

    @Test
    void register_createsNewEmitter() {
        Flux<UnifiedChunk> flux = sseManager.register("session-1");
        assertNotNull(flux);
        assertTrue(sseManager.hasEmitter("session-1"));
        assertEquals(1, sseManager.activeEmitterCount());
    }

    @Test
    void register_replacesExistingEmitter() {
        Flux<UnifiedChunk> first = sseManager.register("session-1");
        // Subscribe to first to verify it completes on replacement
        StepVerifier.create(first)
                .then(() -> sseManager.register("session-1"))
                .verifyComplete();

        assertTrue(sseManager.hasEmitter("session-1"));
        assertEquals(1, sseManager.activeEmitterCount());
    }

    @Test
    void remove_completesAndCleansUp() {
        Flux<UnifiedChunk> flux = sseManager.register("session-1");

        StepVerifier.create(flux)
                .then(() -> sseManager.remove("session-1"))
                .verifyComplete();

        assertFalse(sseManager.hasEmitter("session-1"));
        assertEquals(0, sseManager.activeEmitterCount());
    }

    @Test
    void remove_nonExistentSession_noError() {
        assertDoesNotThrow(() -> sseManager.remove("non-existent"));
    }

    @Test
    void emitProgress_sendsProgressChunk() {
        Flux<UnifiedChunk> flux = sseManager.register("session-1");

        StepVerifier.create(flux.take(1))
                .then(() -> sseManager.emitProgress("session-1", "step-1", "ok", "completed"))
                .assertNext(chunk -> {
                    assertEquals("progress", chunk.getType());
                    assertTrue(chunk.getContent().contains("step-1"));
                    assertTrue(chunk.getContent().contains("completed"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) chunk.getData();
                    assertEquals("step-1", data.get("stepId"));
                    assertEquals("ok", data.get("result"));
                    assertEquals("completed", data.get("status"));
                })
                .verifyComplete();
    }

    @Test
    void emitProgress_nullResult_usesEmptyString() {
        Flux<UnifiedChunk> flux = sseManager.register("session-1");

        StepVerifier.create(flux.take(1))
                .then(() -> sseManager.emitProgress("session-1", "step-1", null, "running"))
                .assertNext(chunk -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) chunk.getData();
                    assertEquals("", data.get("result"));
                })
                .verifyComplete();
    }

    @Test
    void emitConfirmRequest_sendsConfirmChunk() {
        Flux<UnifiedChunk> flux = sseManager.register("session-1");

        StepVerifier.create(flux.take(1))
                .then(() -> sseManager.emitConfirmRequest("session-1", "confirm-step", "确认执行？"))
                .assertNext(chunk -> {
                    assertEquals("progress", chunk.getType());
                    assertEquals("确认执行？", chunk.getContent());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) chunk.getData();
                    assertEquals("confirm-step", data.get("stepId"));
                    assertEquals("确认执行？", data.get("message"));
                    assertEquals(true, data.get("requiresConfirm"));
                })
                .verifyComplete();
    }

    @Test
    void emitChunk_sendsGenericChunk() {
        Flux<UnifiedChunk> flux = sseManager.register("session-1");
        UnifiedChunk custom = UnifiedChunk.builder()
                .type("content")
                .content("hello")
                .build();

        StepVerifier.create(flux.take(1))
                .then(() -> sseManager.emitChunk("session-1", custom))
                .assertNext(chunk -> {
                    assertEquals("content", chunk.getType());
                    assertEquals("hello", chunk.getContent());
                })
                .verifyComplete();
    }

    @Test
    void emit_toNonExistentSession_dropsEvent() {
        // Should not throw, just log a warning
        assertDoesNotThrow(() ->
                sseManager.emitProgress("non-existent", "step-1", "data", "completed"));
    }

    @Test
    void subscribe_existingSession_returnsSameFlux() {
        sseManager.register("session-1");
        Flux<UnifiedChunk> flux = sseManager.subscribe("session-1");

        StepVerifier.create(flux.take(1))
                .then(() -> sseManager.emitProgress("session-1", "s1", "r", "done"))
                .assertNext(chunk -> assertEquals("progress", chunk.getType()))
                .verifyComplete();
    }

    @Test
    void subscribe_nonExistentSession_autoRegisters() {
        assertFalse(sseManager.hasEmitter("new-session"));
        Flux<UnifiedChunk> flux = sseManager.subscribe("new-session");
        assertNotNull(flux);
        assertTrue(sseManager.hasEmitter("new-session"));
    }

    @Test
    void multipleSessionsIndependent() {
        Flux<UnifiedChunk> flux1 = sseManager.register("s1");
        Flux<UnifiedChunk> flux2 = sseManager.register("s2");

        assertEquals(2, sseManager.activeEmitterCount());

        // Emit to s1 only
        StepVerifier.create(flux1.take(1))
                .then(() -> sseManager.emitProgress("s1", "step", "r", "ok"))
                .assertNext(chunk -> assertNotNull(chunk))
                .verifyComplete();

        // s2 should still be active and empty
        assertTrue(sseManager.hasEmitter("s2"));

        // Clean up
        sseManager.remove("s1");
        sseManager.remove("s2");
        assertEquals(0, sseManager.activeEmitterCount());
    }
}
