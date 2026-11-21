package com.luatweaker.core.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One dependency entry from a manifest. Accepts both the legacy plain-id form
 * ({@code "other_mod"}) and the rich object form:
 * <pre>{@code
 * {"id": "lib_mod", "min_version": "1.0.0", "max_version": "1.9.9"}
 * {"id": "soft_lib", "min_version": "0.5.0", "optional": true}
 * }</pre>
 */
public record ModDependency(@NotNull String id,
                            @Nullable String minVersion,
                            @Nullable String maxVersion,
                            boolean optional) {

    /**
     * Parses one entry of a {@code dependencies} / {@code mods} array.
     *
     * @param element        the JSON entry (string or object)
     * @param defaultOptional value used when the entry carries no {@code optional} flag
     *                       (true for {@code optional_dependencies}, false otherwise)
     * @return the dependency, or {@code null} when the entry has no usable id
     */
    public static @Nullable ModDependency parse(@Nullable JsonElement element, boolean defaultOptional) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseStringEntry(element.getAsString(), defaultOptional);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        return parseObjectEntry(element.getAsJsonObject(), defaultOptional);
    }

    private static @Nullable ModDependency parseStringEntry(@NotNull String rawId, boolean defaultOptional) {
        String id = rawId.trim();
        return id.isEmpty() ? null : new ModDependency(id, null, null, defaultOptional);
    }

    private static @Nullable ModDependency parseObjectEntry(@NotNull JsonObject obj, boolean defaultOptional) {
        if (!obj.has("id") || !obj.get("id").isJsonPrimitive()) {
            return null;
        }
        String id = obj.get("id").getAsString().trim();
        if (id.isEmpty()) {
            return null;
        }
        return new ModDependency(id, optString(obj, "min_version"), optString(obj, "max_version"),
                optBoolean(obj, "optional", defaultOptional));
    }

    private static boolean optBoolean(@NotNull JsonObject obj, @NotNull String key, boolean fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        // getAsBoolean() only reads the value or parses it; neither throws on a primitive.
        return obj.get(key).getAsBoolean();
    }

    private static @Nullable String optString(@NotNull JsonObject obj, @NotNull String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) { return null; }
        String value = obj.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    /** True when {@code installedVersion} exists and falls inside the version window. */
    public boolean satisfiedBy(@Nullable String installedVersion) {
        return ModVersion.satisfies(installedVersion, minVersion, maxVersion);
    }

    /** Human-readable fragment for skip messages, e.g. {@code "lib_mod (>= 1.0.0)"}. */
    public @NotNull String describeRequirement() {
        VersionConstraint window = new VersionConstraint(minVersion, maxVersion);
        String bounds = window.describe();
        return "any version".equals(bounds) ? id : id + " (" + bounds + ")";
    }
}
