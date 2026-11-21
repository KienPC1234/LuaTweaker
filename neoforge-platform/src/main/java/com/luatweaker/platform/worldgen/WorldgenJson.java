package com.luatweaker.platform.worldgen;

import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Datapack JSON builders for worldgen entries (structures verified against the
 * vanilla 1.21.1 datapack).
 */
final class WorldgenJson {
    static final String JSON_EXTENSION = ".json";
    private static final String STATE_NAME_JSON_FRAGMENT = "\"state\":{\"Name\":\"";
    private static final String DIMENSION_OVERWORLD = "minecraft:overworld";
    private static final String DIMENSION_NETHER = "minecraft:the_nether";
    private static final String DIMENSION_END = "minecraft:the_end";

    private WorldgenJson() {
    }

    @NotNull
    static String configuredOre(WorldgenServiceImpl.OreEntry ore) {
        return "{"
                + "\"type\":\"minecraft:ore\","
                + "\"config\":{"
                + "\"discard_chance_on_air_exposure\":0.0,"
                + "\"size\":" + ore.clusterSize() + ","
                + "\"targets\":[" + oreTargets(ore) + "]"
                + "}"
                + "}";
    }

    /** Replaceable targets per dimension, mirroring vanilla ore features. */
    @NotNull
    private static String oreTargets(WorldgenServiceImpl.OreEntry ore) {
        return switch (ore.dimension()) {
            case DIMENSION_OVERWORLD ->
                    "{"
                    + "\"target\":{\"predicate_type\":\"minecraft:tag_match\","
                    + "\"tag\":\"minecraft:stone_ore_replaceables\"},"
                    + STATE_NAME_JSON_FRAGMENT + escapeJson(ore.blockId()) + "\"}},"
                    + "{\"target\":{\"predicate_type\":\"minecraft:tag_match\","
                    + "\"tag\":\"minecraft:deepslate_ore_replaceables\"},"
                    + STATE_NAME_JSON_FRAGMENT + escapeJson(ore.blockId()) + "\"}}";
            case DIMENSION_NETHER ->
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\","
                    + "\"block\":\"minecraft:netherrack\"},"
                    + STATE_NAME_JSON_FRAGMENT + escapeJson(ore.blockId()) + "\"}}";
            default ->
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\","
                    + "\"block\":\"minecraft:end_stone\"},"
                    + STATE_NAME_JSON_FRAGMENT + escapeJson(ore.blockId()) + "\"}}";
        };
    }

    @NotNull
    static String placedOre(String featureId, WorldgenServiceImpl.OreEntry ore) {
        return "{"
                + "\"feature\":\"luatweaker:" + featureId + "\","
                + "\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":" + ore.frequency() + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\","
                + "\"height\":{\"type\":\"minecraft:uniform\","
                + "\"min_inclusive\":{\"absolute\":" + ore.minHeight() + "},"
                + "\"max_inclusive\":{\"absolute\":" + ore.maxHeight() + "}"
                + "}"
                + "},"
                + "{\"type\":\"minecraft:biome\"}"
                + "]"
                + "}";
    }

    /**
     * NeoForge biome modifier. {@code biomes} is either a biome-id list (filtered
     * ore) or the dimension's biome tag (unfiltered ore).
     */
    @NotNull
    static String addFeatureModifier(String featureId, String dimension, List<String> biomeIds, String step) {
        return "{"
                + "\"type\":\"neoforge:add_features\","
                + "\"biomes\":" + biomes(biomeIds, dimension) + ","
                + "\"features\":[\"luatweaker:" + featureId + "\"],"
                + "\"step\":\"" + step + "\""
                + "}";
    }

    /**
     * Add-features modifier for an explicit biome list (vegetation: one modifier
     * per placed feature, no dimension-tag fallback involved).
     */
    @NotNull
    static String addFeatureModifier(String featureId, List<String> biomeIds, String step) {
        return addFeatureModifier(featureId, null, biomeIds, step);
    }

    @NotNull
    static String removeFeaturesModifier(List<String> features, String dimension) {
        return "{"
                + "\"type\":\"neoforge:remove_features\","
                + "\"biomes\":" + biomes(List.of(), dimension) + ","
                + "\"features\":" + jsonList(features) + ","
                + "\"steps\":[\"underground_ores\"]"
                + "}";
    }

    /** Biome list, or the dimension's standard biome tag when no explicit list was given. */
    @NotNull
    private static String biomes(List<String> biomeIds, @Nullable String dimension) {
        if (biomeIds != null && !biomeIds.isEmpty()) {
            return jsonList(biomeIds);
        }
        String tag = switch (dimension) {
            case DIMENSION_NETHER -> "#minecraft:is_nether";
            case DIMENSION_END -> "#minecraft:is_end";
            default -> "#minecraft:is_overworld";
        };
        return "\"" + tag + "\"";
    }

    @NotNull
    private static String jsonList(@NotNull List<String> values) {
        StringBuilder list = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                list.append(',');
            }
            list.append('"').append(escapeJson(values.get(i))).append('"');
        }
        return list.append(']').toString();
    }

    @NotNull
    static String placedFlower(String configuredFeatureId, WorldgenServiceImpl.VegetationEntry veg) {
        // chance -> rarity_filter: honor the Lua value exactly. chance <= 0 means
        // "never spawn" (a JSON-safe maximum rarity instead of dividing by zero).
        // NOTE: minecraft:biome carries no parameters in vanilla (verified: 191
        // placed features use the bare form) - per-biome scoping is done by the
        // add_features biome modifier emitted alongside each placed feature.
        double chance = veg.chance();
        int rarity = chance <= 0 ? Integer.MAX_VALUE : Math.max(1, (int) (1.0 / chance));
        return "{"
                + "\"feature\":\"" + configuredFeatureId + "\","
                + "\"placement\":["
                + "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + rarity + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},"
                + "{\"type\":\"minecraft:biome\"}"
                + "]"
                + "}";
    }

    @NotNull
    static String escapeJson(@NotNull String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}




