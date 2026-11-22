package com.luatweaker.common.client.luamods;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuamodConfigJsonParseTest {

    private static final String CRLF_CONFIG = "{\r\n  \"key\": \"value\"\r\n}\r\n";
    private static final String NORMALIZED_CONFIG = "{\n  \"key\": \"value\"\n}\n";

    private static JsonObject sample() {
        return LuamodConfigJsonFixture.sample();
    }

    @Test
    @DisplayName("Line ending normalization strips every carriage return")
    void normalizeLineEndingsStripsCarriageReturns() {
        assertFalse(LuamodConfigJson.normalizeLineEndings(CRLF_CONFIG).contains("\r"),
                "Normalized config must not contain carriage returns");
    }

    @Test
    @DisplayName("Line ending normalization keeps unix newlines")
    void normalizeLineEndingsKeepsUnixNewlines() {
        assertEquals(NORMALIZED_CONFIG, LuamodConfigJson.normalizeLineEndings(CRLF_CONFIG),
                "Normalized config must use unix line endings");
    }

    @Test
    @DisplayName("Boolean config properties parse")
    void parsesBooleanProperty() {
        assertTrue(sample().get("enabled").getAsBoolean(), "enabled should be true");
    }

    @Test
    @DisplayName("Decimal config properties parse")
    void parsesDecimalProperty() {
        JsonObject obj = sample();
        assertEquals(0.045, obj.get("detail_scale").getAsDouble(), 0.0001, "detail_scale should match");
    }

    @Test
    @DisplayName("Integral config properties parse")
    void parsesIntegralProperty() {
        assertEquals(42, sample().get("threshold").getAsLong(), "threshold should match");
    }

    @Test
    @DisplayName("String config properties parse")
    void parsesStringProperty() {
        assertEquals("custom", sample().get("tag").getAsString(), "tag should match");
    }

    @Test
    @DisplayName("Nested boolean config properties parse")
    void parsesNestedBooleanProperty() {
        JsonObject nested = sample().getAsJsonObject("nested");
        assertFalse(nested.get("active").getAsBoolean(), "nested.active should be false");
    }

    @Test
    @DisplayName("Nested decimal config properties parse")
    void parsesNestedDecimalProperty() {
        JsonObject nested = sample().getAsJsonObject("nested");
        assertEquals(1.5, nested.get("factor").getAsDouble(), 0.0001, "nested.factor should match");
    }

    @Test
    @DisplayName("Array config properties parse")
    void parsesArrayProperty() {
        JsonArray items = sample().getAsJsonArray("items");
        assertEquals(2, items.size(), "items should have 2 entries");
    }
}
