package com.luatweaker.core.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JSON parsing half of {@link ModSync} (client hello). */
final class ModSyncParsing {

    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_VERSION_LENGTH = 32;

    private ModSyncParsing() {
    }

    static @NotNull Map<String, String> parseClientHello(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonObject mods = obj.has("mods") && obj.get("mods").isJsonObject()
                    ? obj.getAsJsonObject("mods")
                    : obj;
            return readMods(mods);
        } catch (JsonParseException | IllegalStateException e) {
            return Map.of();
        }
    }

    private static Map<String, String> readMods(JsonObject mods) {
        Map<String, String> out = new ConcurrentHashMap<>();
        for (Map.Entry<String, JsonElement> entry : mods.entrySet()) {
            putIfValid(out, entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void putIfValid(Map<String, String> out, @Nullable String id,
                                   @NotNull JsonElement value) {
        if (id == null || id.isBlank()) {
            return;
        }
        String version = value.isJsonPrimitive() ? value.getAsString() : "0";
        if (id.length() > MAX_ID_LENGTH || version.length() > MAX_VERSION_LENGTH) {
            return;
        }
        out.put(id.trim(), version.trim());
    }
}
