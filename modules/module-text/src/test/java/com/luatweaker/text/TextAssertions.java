package com.luatweaker.text;

import com.luatweaker.api.text.TextSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared assertions for the text builder tests (kept outside the test classes). */
final class TextAssertions {

    private TextAssertions() {
    }

    static void assertLiteral(TextSpec spec) {
        assertEquals(TextSpec.KIND_LITERAL, spec.kind(), "a literal node keeps its kind");
        assertEquals("Hello", spec.plainText(), "plainText must return the literal text");
    }

    static void assertStyle(TextSpec spec) {
        assertEquals("#FF0000", spec.style().color(), "the color must be normalized to uppercase hex");
        assertEquals(Boolean.TRUE, spec.style().bold(), "Bold must be stored");
        assertEquals(Boolean.FALSE, spec.style().italic(), "an explicit false must be stored");
    }

    static void assertClickHover(TextSpec spec) {
        assertEquals("RUN_COMMAND", spec.style().click().action(), "the click action must be kept");
        assertEquals("/say hi", spec.style().click().value(), "the click payload must be kept");
        assertNotNull(spec.style().hover(), "the hover tooltip must be kept");
        assertEquals("Click me", spec.style().hover().plainText(), "the tooltip text must be kept");
    }

    static void assertTranslation(TextSpec spec) {
        assertEquals(TextSpec.KIND_TRANSLATABLE, spec.kind(), "kind must be translatable");
        assertEquals("item.example.name", spec.text(), "the translation key must be kept");
        assertEquals(2, spec.children().size(), "both arguments must be kept");
        assertEquals("5", spec.children().get(1).plainText(),
                "a number argument must be wrapped as literal text");
    }

    static void assertLegacy(String legacy) {
        assertTrue(legacy.startsWith("\u00a7x\u00a70\u00a70\u00a7F\u00a7F\u00a70\u00a70"),
                "hex colors must use the section-sign form");
        assertTrue(legacy.contains("\u00a7l"), "bold must be emitted");
        assertTrue(legacy.endsWith("\u00a7r"), "legacy output must reset at the end");
    }

    static void assertJson(String json) {
        assertTrue(json.contains("\"text\": \"Hello\""), "literal text must be in the JSON: " + json);
        assertTrue(json.contains("\"color\": \"#FF0000\""), "color must be in the JSON: " + json);
        assertTrue(json.contains("\"bold\": true"), "bold must be in the JSON: " + json);
    }
}
