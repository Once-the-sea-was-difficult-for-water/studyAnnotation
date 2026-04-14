package com.multiagent.service.hallucination;

import com.multiagent.service.rag.KnowledgeFragment;
import com.multiagent.service.rag.RAGConfig;
import com.multiagent.service.rag.RAGKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HallucinationGuardTest {

    private RAGKnowledgeService ragKnowledgeService;
    private HallucinationGuard guard;

    @BeforeEach
    void setUp() {
        ragKnowledgeService = mock(RAGKnowledgeService.class);
        guard = new HallucinationGuard(ragKnowledgeService);
    }

    @Test
    void check_nullOutput_returnsClean() {
        HallucinationCheckResult result = guard.check(null, "some query");
        assertEquals(HallucinationCheckResult.Status.CLEAN, result.getStatus());
    }

    @Test
    void check_blankOutput_returnsClean() {
        HallucinationCheckResult result = guard.check("  ", "some query");
        assertEquals(HallucinationCheckResult.Status.CLEAN, result.getStatus());
    }

    @Test
    void check_nullQuery_returnsClean() {
        HallucinationCheckResult result = guard.check("some output", null);
        assertEquals(HallucinationCheckResult.Status.CLEAN, result.getStatus());
    }

    @Test
    void check_noKnowledgeFragments_returnsClean() {
        when(ragKnowledgeService.retrieve(anyString(), any(RAGConfig.class)))
                .thenReturn(Collections.emptyList());

        HallucinationCheckResult result = guard.check("Agent output", "query");
        assertEquals(HallucinationCheckResult.Status.CLEAN, result.getStatus());
        assertEquals("Agent output", result.getOriginalOutput());
    }

    @Test
    void check_ragRetrievalFails_returnsClean() {
        when(ragKnowledgeService.retrieve(anyString(), any(RAGConfig.class)))
                .thenThrow(new RuntimeException("vector store down"));

        HallucinationCheckResult result = guard.check("Agent output", "query");
        assertEquals(HallucinationCheckResult.Status.CLEAN, result.getStatus());
    }

    @Test
    void check_noContradiction_returnsClean() {
        List<KnowledgeFragment> fragments = List.of(
                KnowledgeFragment.builder()
                        .id("1")
                        .content("Redis 支持持久化存储")
                        .score(0.9)
                        .build()
        );
        when(ragKnowledgeService.retrieve(anyString(), any(RAGConfig.class)))
                .thenReturn(fragments);

        HallucinationCheckResult result = guard.check("Redis 支持持久化存储，性能很好", "Redis 特性");
        assertEquals(HallucinationCheckResult.Status.CLEAN, result.getStatus());
        assertTrue(result.getEvidence().isEmpty());
    }

    @Test
    void check_contradictionDetected_returnsFlagged() {
        List<KnowledgeFragment> fragments = List.of(
                KnowledgeFragment.builder()
                        .id("1")
                        .content("Redis 支持持久化存储")
                        .score(0.9)
                        .build()
        );
        when(ragKnowledgeService.retrieve(anyString(), any(RAGConfig.class)))
                .thenReturn(fragments);

        // Output negates a known fact
        HallucinationCheckResult result = guard.check("Redis 不支持持久化存储", "Redis 特性");
        assertEquals(HallucinationCheckResult.Status.FLAGGED, result.getStatus());
        assertFalse(result.getEvidence().isEmpty());
        assertEquals("Redis 不支持持久化存储", result.getOriginalOutput());
    }

    @Test
    void check_englishNegation_returnsFlagged() {
        List<KnowledgeFragment> fragments = List.of(
                KnowledgeFragment.builder()
                        .id("1")
                        .content("The system supports clustering")
                        .score(0.85)
                        .build()
        );
        when(ragKnowledgeService.retrieve(anyString(), any(RAGConfig.class)))
                .thenReturn(fragments);

        HallucinationCheckResult result = guard.check(
                "The system does not support clustering", "system features");
        assertEquals(HallucinationCheckResult.Status.FLAGGED, result.getStatus());
        assertFalse(result.getEvidence().isEmpty());
    }

    @Test
    void check_preservesOriginalOutput() {
        when(ragKnowledgeService.retrieve(anyString(), any(RAGConfig.class)))
                .thenReturn(Collections.emptyList());

        String output = "This is the original output";
        HallucinationCheckResult result = guard.check(output, "query");
        assertEquals(output, result.getOriginalOutput());
    }

    @Test
    void checkResult_staticFactories() {
        HallucinationCheckResult clean = HallucinationCheckResult.clean("output");
        assertEquals(HallucinationCheckResult.Status.CLEAN, clean.getStatus());
        assertTrue(clean.getEvidence().isEmpty());
        assertEquals("output", clean.getOriginalOutput());

        List<String> evidence = List.of("contradiction 1");
        HallucinationCheckResult flagged = HallucinationCheckResult.flagged("bad output", evidence);
        assertEquals(HallucinationCheckResult.Status.FLAGGED, flagged.getStatus());
        assertEquals(1, flagged.getEvidence().size());
        assertEquals("bad output", flagged.getOriginalOutput());
    }
}
