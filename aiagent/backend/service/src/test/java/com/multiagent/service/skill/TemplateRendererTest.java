package com.multiagent.service.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void shouldReplaceSimplePlaceholder() {
        String result = renderer.render("Hello {{name}}", Map.of("name", "World"));
        assertEquals("Hello World", result);
    }

    @Test
    void shouldReplaceMultiplePlaceholders() {
        String result = renderer.render("{{a}} + {{b}} = {{c}}",
                Map.of("a", "1", "b", "2", "c", "3"));
        assertEquals("1 + 2 = 3", result);
    }

    @Test
    void shouldKeepPlaceholderWhenKeyNotFound() {
        String result = renderer.render("Hello {{missing}}", Map.of("name", "World"));
        assertEquals("Hello {{missing}}", result);
    }

    @Test
    void shouldHandleNullTemplate() {
        assertNull(renderer.render(null, Map.of("a", "1")));
    }

    @Test
    void shouldHandleEmptyTemplate() {
        assertEquals("", renderer.render("", Map.of("a", "1")));
    }

    @Test
    void shouldHandleNullContext() {
        assertEquals("Hello {{name}}", renderer.render("Hello {{name}}", null));
    }

    @Test
    void shouldHandleSpacesInPlaceholder() {
        String result = renderer.render("Hello {{ name }}", Map.of("name", "World"));
        assertEquals("Hello World", result);
    }
}
