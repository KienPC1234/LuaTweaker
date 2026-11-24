package com.luatweaker.common.client.luamods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shared JSON text helpers for the config editor. */
final class LuamodConfigJson {

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    static final String DEFAULT_EMPTY_JSON = "{\n}";

    record LoadedConfig(@NotNull String text, @Nullable JsonObject root, boolean missingOnDisk) {
    }

    private LuamodConfigJson() {
    }

    /** Normalises CRLF/CR so the raw editor and the pretty-printed form share one representation. */
    static String normalizeLineEndings(@NotNull String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    @Nullable
    static JsonObject tryParseObject(@NotNull String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    static String formatObject(@Nullable JsonObject obj) {
        return normalizeLineEndings(obj != null ? PRETTY_GSON.toJson(obj) : DEFAULT_EMPTY_JSON);
    }

    static LoadedConfig parseInitial(@Nullable String rawDiskText) {
        boolean missing = rawDiskText == null;
        String normalized = missing ? DEFAULT_EMPTY_JSON : normalizeLineEndings(rawDiskText);
        JsonObject root = tryParseObject(normalized);
        String text = root != null ? formatObject(root) : normalized;
        return new LoadedConfig(text, root, missing);
    }
}
