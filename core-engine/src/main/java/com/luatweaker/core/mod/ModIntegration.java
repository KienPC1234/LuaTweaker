package com.luatweaker.core.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One declared cross-mod integration: an entry script that the engine executes
 * automatically when the target platform mod is installed with a compatible
 * version. Integrations are always soft: an unmet condition skips just the
 * entry (with a recorded reason) and never fails the owning mod.
 *
 * <p>Manifest shapes (value of each key under {@code "integrations"}):
 * <pre>{@code
 * "integrations": {
 *   "jei": "integrations/jei.lua",
 *   "mekanism": {"entry": "integrations/mekanism.lua", "min_version": "10.7.0"},
 *   "curios": {"min_version": "7.0.0"},
 *   "jade": true
 * }
 * }</pre>
 * A bare string is the entry path, {@code true} (or an object without
 * {@code entry}) defaults to {@code integrations/<id>.lua}, and an object may
 * additionally bound the accepted platform-mod version inclusively.
 */
public record ModIntegration(@Nullable String entry,
                             @Nullable String minVersion,
                             @Nullable String maxVersion) {

    /** Default entry script for a target mod id. */
    public static @NotNull String defaultEntry(@NotNull String targetModId) {
        return "integrations/" + targetModId + ".lua";
    }

    /**
     * Parses one value of the {@code integrations} manifest block.
     *
     * @return the definition, or {@code null} when the value carries nothing usable
     */
    public static @Nullable ModIntegration parse(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) { return null; }
        if (element.isJsonPrimitive()) {
            return parsePrimitive(element.getAsJsonPrimitive());
        }
        if (!element.isJsonObject()) { return null; }
        return parseObject(element.getAsJsonObject());
    }

    /** A bare string is the entry path; boolean {@code true} takes the default entry. */
    private static @Nullable ModIntegration parsePrimitive(JsonElement primitive) {
        if (primitive.getAsJsonPrimitive().isString()) {
            String entry = primitive.getAsString().trim();
            if (entry.isEmpty()) { return null; }
            return new ModIntegration(entry, null, null);
        }
        if (primitive.getAsJsonPrimitive().isBoolean() && primitive.getAsBoolean()) {
            return new ModIntegration(null, null, null);
        }
        return null;
    }

    private static @Nullable ModIntegration parseObject(JsonObject obj) {
        String entry = optString(obj, "entry");
        String min = optString(obj, "min_version");
        String max = optString(obj, "max_version");
        if (entry == null && min == null && max == null) { return null; }
        return new ModIntegration(entry, min, max);
    }

    private static @Nullable String optString(@NotNull JsonObject obj, @NotNull String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) { return null; }
        String value = obj.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    /** Entry script to load, defaulting to {@code integrations/<id>.lua}. */
    public @NotNull String entryOrDefault(@NotNull String targetModId) {
        return entry != null ? entry : defaultEntry(targetModId);
    }

    /** True when {@code installedVersion} exists and falls inside the version window. */
    public boolean satisfiedBy(@Nullable String installedVersion) {
        return ModVersion.satisfies(installedVersion, minVersion, maxVersion);
    }

    /** Human-readable fragment for skip messages, e.g. {@code "mekanism (>= 10.7.0)"}. */
    public @NotNull String describeRequirement(@NotNull String targetModId) {
        VersionConstraint window = new VersionConstraint(minVersion, maxVersion);
        String bounds = window.describe();
        return "any version".equals(bounds) ? targetModId : targetModId + " (" + bounds + ")";
    }
}
