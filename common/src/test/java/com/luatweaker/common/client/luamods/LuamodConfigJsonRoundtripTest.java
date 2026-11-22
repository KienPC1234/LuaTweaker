package com.luatweaker.common.client.luamods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuamodConfigJsonRoundtripTest {

    private static JsonObject sample() {
        return LuamodConfigJsonFixture.sample();
    }

    private static JsonObject reparse(JsonObject source) {
        String pretty = LuamodConfigJsonFixture.PRETTY_GSON.toJson(source);
        return JsonParser.parseString(pretty).getAsJsonObject();
    }

    @Test
    @DisplayName("Pretty printed config keeps unix line endings")
    void prettyPrintContainsNoCarriageReturns() {
        String pretty = LuamodConfigJsonFixture.PRETTY_GSON.toJson(sample());
        assertFalse(pretty.contains("\r"), "Pretty JSON must use unix line endings");
    }

    @Test
    @DisplayName("Mutated boolean survives a pretty-print roundtrip")
    void mutatedBooleanSurvivesRoundtrip() {
        JsonObject obj = sample();
        obj.addProperty("enabled", false);
        assertFalse(reparse(obj).get("enabled").getAsBoolean(), "Mutated enabled flag should be false");
    }

    @Test
    @DisplayName("Mutated decimal survives a pretty-print roundtrip")
    void mutatedDecimalSurvivesRoundtrip() {
        JsonObject obj = sample();
        obj.addProperty("detail_scale", 0.05);
        assertEquals(0.05, reparse(obj).get("detail_scale").getAsDouble(), 0.0001,
                "Mutated detail_scale should be 0.05");
    }

    @Test
    @DisplayName("Mutated nested boolean survives a pretty-print roundtrip")
    void mutatedNestedBooleanSurvivesRoundtrip() {
        JsonObject obj = sample();
        obj.getAsJsonObject("nested").addProperty("active", true);
        JsonObject nested = reparse(obj).getAsJsonObject("nested");
        assertTrue(nested.get("active").getAsBoolean(), "Mutated nested.active should be true");
    }
}
