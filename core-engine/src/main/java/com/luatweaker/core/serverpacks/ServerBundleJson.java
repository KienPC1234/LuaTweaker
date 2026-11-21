package com.luatweaker.core.serverpacks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wire shape of a bundle: the {@code download} block verdicts embed.
 *
 * <p>Parsing never throws: hostile verdict JSON parses to {@code null} and the
 * handshake moves on. Serialization mirrors the same field names.</p>
 */
public final class ServerBundleJson {

    private static final String KEY_URL = "url";
    private static final String KEY_SHA = "sha256";
    private static final String KEY_VERSION = "version";
    private static final String KEY_NAME = "name";
    private static final String KEY_FILENAME = "filename";
    private static final String KEY_DOWNLOAD = "download";
    private static final String QUOTE_COLON_QUOTE = "\":\"";
    private static final String QUOTE_COMMA_QUOTE = "\",\"";

    private ServerBundleJson() {
    }

    /**
     * Parses a bare bundle object. Returns {@code null} on malformed JSON
     * or failed validation.
     */
    public static @Nullable ServerBundleInfo parseBundle(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return fromObject(JsonParser.parseString(json).getAsJsonObject());
        } catch (IllegalStateException | JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * Extracts the {@code download} block from a ModSync verdict envelope.
     * Returns {@code null} when the envelope is malformed or carries no valid block.
     */
    public static @Nullable ServerBundleInfo parseVerdictOffer(@Nullable String verdictJson) {
        if (verdictJson == null || verdictJson.isBlank()) {
            return null;
        }
        try {
            JsonObject envelope = JsonParser.parseString(verdictJson).getAsJsonObject();
            if (!envelope.has(KEY_DOWNLOAD) || !envelope.get(KEY_DOWNLOAD).isJsonObject()) {
                return null;
            }
            return fromObject(envelope.getAsJsonObject(KEY_DOWNLOAD));
        } catch (IllegalStateException | JsonSyntaxException e) {
            return null;
        }
    }

    /** Serializes to the {@code download} block shape the verdict embeds. */
    public static @NotNull String toJson(@NotNull ServerBundleInfo bundle) {
        return "{\"" + KEY_URL + QUOTE_COLON_QUOTE + escape(bundle.url())
                + QUOTE_COMMA_QUOTE + KEY_SHA + QUOTE_COLON_QUOTE + bundle.sha256()
                + QUOTE_COMMA_QUOTE + KEY_VERSION + QUOTE_COLON_QUOTE + escape(bundle.version())
                + QUOTE_COMMA_QUOTE + KEY_NAME + QUOTE_COLON_QUOTE + escape(bundle.name())
                + QUOTE_COMMA_QUOTE + KEY_FILENAME + QUOTE_COLON_QUOTE
                + escape(bundle.filename()) + "\"}";
    }

    private static @Nullable ServerBundleInfo fromObject(@NotNull JsonObject obj) {
        return ServerBundleValidation.create(new ServerBundleSpec(
                textMember(obj, KEY_URL), textMember(obj, KEY_SHA),
                textMember(obj, KEY_VERSION), textMember(obj, KEY_NAME),
                textMember(obj, KEY_FILENAME)));
    }

    private static @Nullable String textMember(@NotNull JsonObject obj, @NotNull String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()
                || !obj.getAsJsonPrimitive(key).isString()) {
            return null;
        }
        return obj.getAsJsonPrimitive(key).getAsString();
    }

    private static @NotNull String escape(@NotNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
