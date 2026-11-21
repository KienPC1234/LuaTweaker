package com.luatweaker.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

/**
 * Placement/replace/raw-JSON parsing for ore-like registrations. Each helper owns one
 * concern so the entry points share a single interpretation of the spec table.
 */
final class WorldgenPlacementParser {

    private static final String KEY_STEP = "step";
    private static final String KEY_COUNT = "count";
    private static final String KEY_RARITY = "rarity";
    private static final String KEY_SIZE = "size";
    private static final String KEY_MIN_HEIGHT = "minHeight";
    private static final String KEY_MAX_HEIGHT = "maxHeight";
    private static final String KEY_HEIGHT_TYPE = "heightType";
    private static final String KEY_ANCHOR_MIN = "anchorMin";
    private static final String KEY_ANCHOR_MAX = "anchorMax";
    private static final String KEY_DISCARD_CHANCE = "discardChance";
    private static final String KEY_INNER = "inner";
    private static final String KEY_REPLACE = "replace";

    private static final double MIN_DISCARD_CHANCE = 0.0;
    private static final double MAX_DISCARD_CHANCE = 1.0;
    private static final int MIN_COUNT = 0;
    private static final int MIN_SIZE = 0;
    private static final int MIN_INNER = 0;
    private static final int MIN_RARITY = 0;

    private WorldgenPlacementParser() {}

    private record CountSpec(int count, Integer rarity) {}

    private record HeightSpec(String heightType, String anchorMin, String anchorMax, int inner) {}

    static @NotNull WorldgenPlacement parsePlacement(@NotNull ILuaTable spec) {
        String step = LuaFieldReaders.optStep(spec, KEY_STEP, WorldgenDefaults.DEFAULT_STEP);
        CountSpec frequency = parseCount(spec);
        int size = LuaFieldReaders.optInt(spec, KEY_SIZE, WorldgenDefaults.DEFAULT_FEATURE_SIZE);
        if (size <= MIN_SIZE) {
            throw new IllegalArgumentException("spec.size must be > 0, got: " + size);
        }
        int minHeight = LuaFieldReaders.optInt(spec, KEY_MIN_HEIGHT, WorldgenDefaults.DEFAULT_MIN_HEIGHT);
        int maxHeight = LuaFieldReaders.optInt(spec, KEY_MAX_HEIGHT, WorldgenDefaults.DEFAULT_MAX_HEIGHT);
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException(
                    "spec.minHeight (" + minHeight + ") > spec.maxHeight (" + maxHeight + ")");
        }
        HeightSpec height = parseHeight(spec);
        double discardChance = parseDiscardChance(spec);
        return new WorldgenPlacement(step, frequency.count(), frequency.rarity(), size,
                minHeight, maxHeight, height.heightType(), height.anchorMin(),
                height.anchorMax(), discardChance, height.inner());
    }

    private static @NotNull CountSpec parseCount(@NotNull ILuaTable spec) {
        int count = LuaFieldReaders.optInt(spec, KEY_COUNT, WorldgenDefaults.DEFAULT_FEATURE_COUNT);
        if (count <= MIN_COUNT) {
            throw new IllegalArgumentException("spec.count must be > 0, got: " + count);
        }
        boolean hasCount = LuaFieldReaders.hasExplicit(spec, KEY_COUNT);
        boolean hasRarity = LuaFieldReaders.hasExplicit(spec, KEY_RARITY);
        if (hasCount && hasRarity) {
            throw new IllegalArgumentException(
                    "spec.count and spec.rarity are mutually exclusive (count = N veins per chunk, "
                    + "rarity = 1-in-N chunk chance like vanilla diamond_large); set exactly one");
        }
        if (!hasRarity) {
            return new CountSpec(count, null);
        }
        int rawRarity = LuaFieldReaders.optInt(spec, KEY_RARITY, 1);
        if (rawRarity <= MIN_RARITY) {
            throw new IllegalArgumentException("spec.rarity must be > 0, got: " + rawRarity);
        }
        return new CountSpec(count, rawRarity);
    }

    private static @NotNull HeightSpec parseHeight(@NotNull ILuaTable spec) {
        String heightType = LuaFieldReaders.optString(spec, KEY_HEIGHT_TYPE);
        if (heightType == null || heightType.isEmpty()) {
            heightType = WorldgenDefaults.DEFAULT_HEIGHT_TYPE;
        } else if (!WorldgenDefaults.KNOWN_HEIGHT_TYPES.contains(heightType)) {
            throw new IllegalArgumentException("spec.heightType must be one of "
                    + WorldgenDefaults.KNOWN_HEIGHT_TYPES + ", got: " + heightType);
        }
        String anchorMin = LuaFieldReaders.optAnchor(spec, KEY_ANCHOR_MIN);
        String anchorMax = LuaFieldReaders.optAnchor(spec, KEY_ANCHOR_MAX);
        int inner = LuaFieldReaders.optInt(spec, KEY_INNER, WorldgenDefaults.DEFAULT_BIASED_INNER);
        if (inner < MIN_INNER) {
            throw new IllegalArgumentException("spec.inner must be >= 0, got: " + inner);
        }
        return new HeightSpec(heightType, anchorMin, anchorMax, inner);
    }

    private static double parseDiscardChance(@NotNull ILuaTable spec) {
        double discardChance = LuaFieldReaders.optDouble(spec, KEY_DISCARD_CHANCE,
                WorldgenDefaults.DEFAULT_DISCARD_CHANCE);
        if (!Double.isFinite(discardChance)
                || discardChance < MIN_DISCARD_CHANCE || discardChance > MAX_DISCARD_CHANCE) {
            throw new IllegalArgumentException("spec.discardChance must be between "
                    + MIN_DISCARD_CHANCE + " and " + MAX_DISCARD_CHANCE + ", got: " + discardChance);
        }
        return discardChance;
    }

    static @NotNull String parseReplace(@NotNull ILuaTable spec) {
        String replace = LuaFieldReaders.optString(spec, KEY_REPLACE);
        if (replace == null || replace.isEmpty()) {
            return WorldgenDefaults.DEFAULT_REPLACE_KIND;
        }
        if (!WorldgenDefaults.KNOWN_REPLACE_KINDS.contains(replace)) {
            throw new IllegalArgumentException("spec.replace must be one of "
                    + WorldgenDefaults.KNOWN_REPLACE_KINDS + ", got: " + replace);
        }
        return replace;
    }

    /**
     * Parses raw JSON, requiring a string field (the type or feature reference).
     * Returns the field value; a malformed body is reported with the original cause.
     */
    static @NotNull String parseRawJson(@NotNull String json, @NotNull String kind,
                                        @NotNull String field, @NotNull String featureId) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("AddRaw JSON must not be blank for '" + featureId + "'");
        }
        JsonObject parsed = parseJsonObject(json, kind, featureId);
        return stringMember(parsed, field, kind, featureId);
    }

    private static @NotNull JsonObject parseJsonObject(@NotNull String json, @NotNull String kind,
                                                       @NotNull String featureId) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException(
                    "AddRaw " + kind + " JSON for '" + featureId + "' does not parse: " + e.getMessage(), e);
        }
    }

    private static @NotNull String stringMember(@NotNull JsonObject parsed, @NotNull String field,
                                                @NotNull String kind, @NotNull String featureId) {
        JsonElement member = parsed.get(field);
        if (member == null || !member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("AddRaw " + kind + " JSON for '" + featureId
                    + "' requires a string '" + field + "' field");
        }
        return member.getAsString();
    }
}
